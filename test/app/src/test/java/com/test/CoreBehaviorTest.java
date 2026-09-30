package com.test;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.zip.CRC32;
import static org.junit.Assert.*;

public class CoreBehaviorTest {
    private byte[] binary() {
        byte[] image=new byte[0x151]; Arrays.fill(image,(byte)0xaa);
        ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN).putInt(0x20008000).putInt(OtaImage.ADDRESS+0x141);
        return image;
    }
    private OtaImage image(byte[] bytes) {
        CRC32 crc=new CRC32(); crc.update(bytes);
        return new OtaImage(bytes,1,"STM32WB35CE","application",OtaImage.ADDRESS,bytes.length,crc.getValue(),OtaImage.hash(bytes),"0.2.0");
    }
    @Test public void fiveLongNotificationsAreRetainedAsMessages() {
        MessageOutbox box=new MessageOutbox();
        for (int i=0;i<5;i++) assertTrue(box.offer(new String(new char[252]).replace('\0','x'),0));
        assertEquals(5,box.size(0)); assertEquals(0,box.dropped());
    }
    @Test public void overloadIsBoundedAndCounted() {
        MessageOutbox box=new MessageOutbox();
        for (int i=0;i<MessageOutbox.CAPACITY;i++) assertTrue(box.offer("message",0));
        assertFalse(box.offer("overflow",0)); assertEquals(1,box.dropped());
    }
    @Test public void oldPrivateMessagesExpire() {
        MessageOutbox box=new MessageOutbox(); box.offer("old",0);
        assertNotNull(box.peek(59999)); assertNull(box.peek(60000)); assertEquals(1,box.dropped());
    }
    @Test public void lateCompletionCannotRemoveAnotherMessage() {
        MessageOutbox box=new MessageOutbox(); box.offer("old",0);
        MessageOutbox.Message old=box.peek(0);
        box.offer("new",59999); box.expire(60000); box.delivered(old);
        assertEquals("new",box.peek(60000).text);
        box.failed(old); assertEquals(1,box.size(60000));
    }
    @Test public void utf8BoundsPreserveCodepoints() {
        MessageOutbox box=new MessageOutbox();
        StringBuilder text=new StringBuilder(); for (int i=0;i<100;i++) text.append("😀");
        assertTrue(box.offer(text.toString(),0)); assertEquals(126,box.peek(0).text.length());
        assertEquals(14,SmartglassesProtocol.frames(box.peek(0).text).size());
    }
    @Test public void acceptedBatchSurvivesExpiryWhileWaitingMessagesExpire() {
        MessageOutbox box=new MessageOutbox(); box.offer("sending",0); box.offer("waiting",0);
        MessageOutbox.Message sending=box.peek(0); box.started(sending); box.expire(60000);
        assertSame(sending,box.peek(60000)); assertEquals(1,box.dropped());
        box.delivered(sending); assertEquals(0,box.size(60000)); assertEquals(1,box.dropped());
    }
    @Test public void pausingNotificationsFinishesOnlyTheAcceptedBatch() {
        MessageOutbox box=new MessageOutbox(); box.offer("sending",0); box.offer("waiting",0);
        MessageOutbox.Message sending=box.peek(0); box.started(sending); box.discardWaiting();
        assertEquals(1,box.size(0)); assertEquals(1,box.dropped());
        box.delivered(sending); assertEquals(0,box.size(0)); assertEquals(1,box.dropped());
    }
    @Test public void duplicateUpdatesDoNotForwardTwice() {
        NotificationDeduplicator gate=new NotificationDeduplicator();
        assertTrue(gate.accept("key","hello",0)); assertFalse(gate.accept("key","hello",100));
        assertTrue(gate.accept("key","changed",200)); gate.remove("key"); assertTrue(gate.accept("key","changed",201));
    }
    @Test public void expiredDedupCanReceiveNewNotification() {
        NotificationDeduplicator gate=new NotificationDeduplicator(); gate.accept("key","hello",0);
        assertTrue(gate.accept("key","hello",300000));
    }
    @Test public void otaPacketsMatchFirmwareLittleEndianWireFormat() {
        OtaImage image=image(binary());
        ByteBuffer begin=ByteBuffer.wrap(image.begin()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x31554753,begin.getInt()); assertEquals(image.size(),begin.getInt());
        byte[] last=image.packet(0x150); assertEquals(5,last.length);
        assertEquals(0x150,ByteBuffer.wrap(last).order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertArrayEquals(new byte[]{'E','N','D','1'},image.end());
    }
    @Test(expected=IllegalArgumentException.class) public void wrongDigestNeverStartsAnUpdate() {
        byte[] b=binary(); CRC32 crc=new CRC32(); crc.update(b);
        new OtaImage(b,1,"STM32WB35CE","application",OtaImage.ADDRESS,b.length,crc.getValue(),"invalid","0.2.0");
    }
    @Test(expected=IllegalArgumentException.class) public void bootloaderPackageCannotOverwriteApplication() {
        byte[] b=binary(); CRC32 crc=new CRC32(); crc.update(b);
        new OtaImage(b,1,"STM32WB35CE","bootloader",0x08000000,b.length,crc.getValue(),OtaImage.hash(b),"0.2.0");
    }
    @Test(expected=IllegalArgumentException.class) public void invalidStackRejectedEvenWithValidDigests() {
        byte[] b=binary(); ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(0x20010000); image(b);
    }
    @Test(expected=IllegalArgumentException.class) public void resetOutsideImageRejected() {
        byte[] b=binary(); ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(4,0x08040001); image(b);
    }
    @Test(expected=IllegalArgumentException.class) public void unalignedOffsetRejected() { image(binary()).packet(1); }
    @Test public void fullUuidAndFirmwareModeAreChecked() {
        assertFalse(GlassesProfile.MESSAGE.equals(UUID.fromString("00000022-0000-1000-8000-00805f9b34fb")));
        assertTrue(GlassesProfile.supportedVersion(new byte[]{0,2,0,0}));
        assertTrue(GlassesProfile.supportedVersion(new byte[]{0,2,0,1}));
        assertFalse(GlassesProfile.supportedVersion(new byte[]{0,2,0,2}));
        assertFalse(GlassesProfile.supportedVersion(new byte[]{0,2,0}));
    }
    private static final class Driver implements GattQueue.Driver {
        long now; int starts,failures; boolean ready=true;
        final List<Runnable> timers=new ArrayList<>(); final List<Long> deadlines=new ArrayList<>();
        public long now() { return now; }
        public boolean ready() { return ready; }
        public boolean start(GattQueue.Operation op) { starts++; return true; }
        public void later(Runnable task,long delay) { timers.add(task); deadlines.add(now+delay); }
        public void failed(String reason) { failures++; }
        void advance(long target) {
            while (!deadlines.isEmpty() && Collections.min(deadlines)<=target) {
                int index=deadlines.indexOf(Collections.min(deadlines)); now=deadlines.remove(index); timers.remove(index).run();
            }
            now=target;
        }
    }
    @Test public void nextOtaPacketWaitsForAcknowledgement() {
        Driver d=new Driver(); GattQueue queue=new GattQueue(d); int[] done={0};
        queue.enqueue(Collections.singletonList(new GattQueue.Operation(GlassesProfile.DATA,new byte[]{1},5000,() -> done[0]++)));
        queue.enqueue(Collections.singletonList(new GattQueue.Operation(GlassesProfile.DATA,new byte[]{2})));
        assertEquals(1,d.starts); assertEquals(0,done[0]); queue.complete(GlassesProfile.DATA,true);
        assertEquals(2,d.starts); assertEquals(1,done[0]); queue.complete(GlassesProfile.DATA,true); assertTrue(queue.idle());
    }
    @Test public void flashEraseGetsLongerBoundedTimeout() {
        Driver d=new Driver(); GattQueue queue=new GattQueue(d);
        queue.enqueue(Collections.singletonList(new GattQueue.Operation(GlassesProfile.BEGIN,new byte[]{1},15000,null)));
        d.advance(14999); assertEquals(0,d.failures); d.advance(15000); assertEquals(1,d.failures);
    }
    @Test public void cancelledOperationDoesNotInvokeSuccessOrOldTimeout() {
        Driver d=new Driver(); GattQueue queue=new GattQueue(d); int[] done={0};
        queue.enqueue(Collections.singletonList(new GattQueue.Operation(GlassesProfile.END,new byte[]{1},5000,() -> done[0]++)));
        queue.clear(); queue.complete(GlassesProfile.END,true); d.advance(6000);
        assertEquals(0,done[0]); assertEquals(0,d.failures);
    }
}
