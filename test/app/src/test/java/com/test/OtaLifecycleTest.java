package com.test;

import android.Manifest;
import android.bluetooth.*;
import android.content.Intent;
import android.os.Looper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBluetoothGatt;
import org.robolectric.util.ReflectionHelpers;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.zip.CRC32;
import static org.junit.Assert.*;

/** Controlled ATT responses exercise the actual service, including failed radio sessions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35},shadows={OtaLifecycleTest.ControlledGatt.class})
public class OtaLifecycleTest {
    @Implements(BluetoothGatt.class)
    public static class ControlledGatt extends ShadowBluetoothGatt {
        @RealObject private BluetoothGatt real;
        final Map<UUID,BluetoothGattService> services=new HashMap<>();
        BluetoothGattCharacteristic pending;
        byte[] bytes;
        boolean reading, closed;
        int reads, writes;
        @Implementation protected BluetoothGattService getService(UUID uuid) { return services.get(uuid); }
        @Implementation protected boolean readCharacteristic(BluetoothGattCharacteristic c) {
            assertNull(pending); pending=c; reading=true; reads++; return true;
        }
        @Implementation protected boolean writeCharacteristic(BluetoothGattCharacteristic c) {
            return write(c,c.getValue());
        }
        @Implementation(minSdk=33) protected int writeCharacteristic(BluetoothGattCharacteristic c,byte[] data,int type) {
            assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,type);
            return write(c,data) ? 0 : 1;
        }
        private boolean write(BluetoothGattCharacteristic c,byte[] data) {
            assertNull(pending); pending=c; reading=false; bytes=data.clone(); writes++; return true;
        }
        @Implementation protected void disconnect() { }
        @Implementation protected void close() { closed=true; }
        void acknowledge() {
            BluetoothGattCharacteristic c=pending; assertNotNull(c); pending=null;
            if (reading && android.os.Build.VERSION.SDK_INT>=33) getGattCallback().onCharacteristicRead(real,c,c.getValue(),BluetoothGatt.GATT_SUCCESS);
            else if (reading) getGattCallback().onCharacteristicRead(real,c,BluetoothGatt.GATT_SUCCESS);
            else getGattCallback().onCharacteristicWrite(real,c,BluetoothGatt.GATT_SUCCESS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }
    private ServiceController<BluetoothLeService> controller;
    private BluetoothLeService service;
    private BluetoothGatt gatt;
    private ControlledGatt radio;
    private BluetoothGattCallback callback;
    private OtaImage image;
    @Before public void create() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN);
        controller=Robolectric.buildService(BluetoothLeService.class).create(); service=controller.get();
        byte[] data=new byte[0x151]; Arrays.fill(data,(byte)0x55);
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(0x20008000).putInt(OtaImage.ADDRESS+0x141);
        CRC32 crc=new CRC32(); crc.update(data);
        image=new OtaImage(data,1,"STM32WB35CE","application",OtaImage.ADDRESS,data.length,crc.getValue(),OtaImage.hash(data),"0.2.0");
        ReflectionHelpers.setField(service,"image",image);
        ReflectionHelpers.setField(service,"address","00:11:22:33:44:55");
        attach(1);
    }
    @After public void destroy() { controller.destroy(); }
    private BluetoothGattCharacteristic channel(UUID serviceId,UUID uuid,int property) {
        BluetoothGattService s=radio.services.get(serviceId);
        if (s==null) { s=new BluetoothGattService(serviceId,BluetoothGattService.SERVICE_TYPE_PRIMARY); radio.services.put(serviceId,s); }
        BluetoothGattCharacteristic c=new BluetoothGattCharacteristic(uuid,property,0); s.addCharacteristic(c); return c;
    }
    private void attach(int mode) {
        BluetoothDevice device=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55");
        Shadows.shadowOf(device).setBondState(BluetoothDevice.BOND_BONDED);
        gatt=ShadowBluetoothGatt.newInstance(device); radio=Shadow.extract(gatt);
        callback=ReflectionHelpers.getField(service,"callback"); radio.setGattCallback(callback);
        channel(GlassesProfile.INFO,GlassesProfile.VERSION,BluetoothGattCharacteristic.PROPERTY_READ).setValue(new byte[]{0,2,0,(byte)mode});
        channel(GlassesProfile.INFO,GlassesProfile.NAME,BluetoothGattCharacteristic.PROPERTY_READ).setValue(new byte[]{'S','G'});
        channel(GlassesProfile.INFO,GlassesProfile.DIAGNOSTICS,BluetoothGattCharacteristic.PROPERTY_READ).setValue(new byte[20]);
        for (UUID id:Arrays.asList(GlassesProfile.TIME,GlassesProfile.MESSAGE,GlassesProfile.CONTROL)) channel(GlassesProfile.RECEIVE,id,BluetoothGattCharacteristic.PROPERTY_WRITE);
        for (UUID id:Arrays.asList(GlassesProfile.BEGIN,GlassesProfile.DATA,GlassesProfile.END)) channel(GlassesProfile.OTA,id,BluetoothGattCharacteristic.PROPERTY_WRITE);
        ReflectionHelpers.setField(service,"gatt",gatt);
        ReflectionHelpers.setField(service,"desired",true);
        ReflectionHelpers.setField(service,"validServices",true);
        ReflectionHelpers.setField(service,"version",new byte[]{0,2,0,(byte)mode});
        ReflectionHelpers.setField(service,"state",mode==1 ? BluetoothLeService.State.BOOTLOADER : BluetoothLeService.State.READY);
    }
    private void lose() {
        callback.onConnectionStateChange(gatt,133,BluetoothProfile.STATE_DISCONNECTED);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void completeUploadNeedsEndAcknowledgementAndApplicationReconnect() {
        assertTrue(service.beginUpdate()); assertEquals(GlassesProfile.BEGIN,radio.pending.getUuid());
        assertArrayEquals(image.begin(),radio.bytes); assertEquals(0,radio.reads);
        assertFalse(service.sendText("Paused")); radio.acknowledge();
        int offset=0;
        while (offset<image.size()) {
            assertEquals(GlassesProfile.DATA,radio.pending.getUuid());
            assertArrayEquals(image.packet(offset),radio.bytes); offset+=Math.min(16,image.size()-offset);
            radio.acknowledge();
        }
        assertEquals(100,service.progress()); assertTrue(service.updating());
        assertEquals(BluetoothLeService.State.VERIFYING,service.state());
        assertEquals(GlassesProfile.END,radio.pending.getUuid()); assertArrayEquals(image.end(),radio.bytes);
        radio.acknowledge(); assertTrue(service.updating()); assertFalse(service.canCancelUpdate());
        lose(); attach(0);
        callback.onServicesDiscovered(gatt,BluetoothGatt.GATT_SUCCESS); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(GlassesProfile.VERSION,radio.pending.getUuid()); radio.acknowledge();
        assertFalse(service.updating()); assertEquals(BluetoothLeService.State.READY,service.state());
        assertEquals(service.getString(R.string.update_success),service.detail());
    }
    @Test public void interruptedTransferClosesGattAndNeedsExplicitRestart() {
        assertTrue(service.beginUpdate()); radio.acknowledge();
        assertEquals(GlassesProfile.DATA,radio.pending.getUuid()); ControlledGatt old=radio; lose();
        assertTrue(old.closed); assertFalse(service.updating()); assertEquals(BluetoothLeService.State.ERROR,service.state());
        // A late ACK from the discarded session cannot move progress or send another packet.
        old.acknowledge(); assertEquals(0,service.progress()); assertEquals(2,old.writes);
        attach(1); callback.onServicesDiscovered(gatt,BluetoothGatt.GATT_SUCCESS); Shadows.shadowOf(Looper.getMainLooper()).idle();
        radio.acknowledge(); assertEquals(BluetoothLeService.State.BOOTLOADER,service.state());
        assertNull(radio.pending); assertEquals(0,radio.writes); assertTrue(service.beginUpdate());
        assertEquals(GlassesProfile.BEGIN,radio.pending.getUuid());
    }
    @Test public void unbindingControlsPreservesAnActiveUpload() {
        assertTrue(service.beginUpdate()); service.onUnbind(new Intent());
        assertFalse(radio.closed); assertTrue(service.updating());
        radio.acknowledge(); assertEquals(GlassesProfile.DATA,radio.pending.getUuid());
    }
    @Test public void cancellationDiscardsOutstandingWrites() {
        assertTrue(service.beginUpdate()); radio.acknowledge(); ControlledGatt old=radio;
        ReflectionHelpers.setField(service,"desired",false); service.cancelUpdate();
        assertTrue(old.closed); assertFalse(service.updating()); old.acknowledge();
        assertEquals(0,service.progress()); assertEquals(2,old.writes);
    }
    @Test public void exhaustedPostUpdateReconnectUnlocksManualRetry() {
        ReflectionHelpers.setField(service,"awaitingVerification",true);
        ReflectionHelpers.setField(service,"retries",8); lose();
        assertFalse(service.updating()); assertEquals(service.getString(R.string.update_reconnect_failed),service.detail());
    }
    @Test public void wrongFullServiceUuidIsRejectedBeforePairingOrWriting() {
        radio.services.remove(GlassesProfile.OTA);
        callback.onServicesDiscovered(gatt,BluetoothGatt.GATT_SUCCESS); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(radio.closed); assertEquals(BluetoothLeService.State.ERROR,service.state());
        assertEquals(service.getString(R.string.unsupported_device),service.detail()); assertEquals(0,radio.reads); assertEquals(0,radio.writes);
    }
}
