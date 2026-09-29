import com.test.GattQueue;
import com.test.SmartglassesProtocol;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class GattQueueTest {
    static final UUID UUID1=UUID.fromString("00000022-8e22-4541-9d4c-21edae82ed19");
    static final class Driver implements GattQueue.Driver {
        long time; boolean ready=true, busy=false; int starts, failures;
        final List<Runnable> tasks=new ArrayList<>(); final List<Long> deadlines=new ArrayList<>();
        public long now(){return time;}
        public boolean ready(){return ready;}
        public boolean start(GattQueue.Operation op){starts++;return !busy;}
        public void later(Runnable task,long delay){tasks.add(task);deadlines.add(time+delay);}
        public void failed(String reason){failures++;}
        void advance(long amount){
            long end=time+amount;
            while(true){
                int next=-1; long at=Long.MAX_VALUE;
                for(int i=0;i<tasks.size();i++) if(deadlines.get(i)<at){at=deadlines.get(i);next=i;}
                if(next<0 || at>end)break;
                time=at; Runnable r=tasks.remove(next);deadlines.remove(next);r.run();
            }
            time=end;
        }
    }
    static void check(boolean value){if(!value)throw new AssertionError();}
    static List<GattQueue.Operation> batch(String text){
        List<GattQueue.Operation> r=new ArrayList<>();
        for(byte[] frame:SmartglassesProtocol.frames(text))r.add(new GattQueue.Operation(UUID1,frame));
        return r;
    }
    public static void main(String[] args){
        List<byte[]> f=SmartglassesProtocol.frames("ä".repeat(60));
        check(f.size()==7 && f.get(6).length==14);
        f=SmartglassesProtocol.frames("😀".repeat(100));int bytes=0;
        for(byte[] frame:f){check(frame.length<=20);bytes+=frame.length-2;} check(bytes==252 && f.size()==14);
        byte[] raw=new byte[bytes];int at=0;
        for(byte[] frame:f){System.arraycopy(frame,2,raw,at,frame.length-2);at+=frame.length-2;}
        check(new String(raw,StandardCharsets.UTF_8).equals("😀".repeat(63)));
        check(SmartglassesProtocol.frames("").isEmpty());
        check(SmartglassesProtocol.frames(null).isEmpty());
        Driver d=new Driver();GattQueue q=new GattQueue(d);
        check(q.enqueue(batch("X".repeat(36))));check(d.starts==1);
        d.advance(100);check(d.starts==1);
        q.complete(UUID1,true);check(d.starts==2);
        q.complete(UUID1,true);d.advance(6000);check(d.failures==0);
        check(q.enqueue(batch("abc")));q.complete(UUID1,false);check(d.failures==1);
        d.busy=true;check(q.enqueue(batch("abc")));d.advance(1000);check(d.failures==2);
        d.busy=false;check(q.enqueue(batch("abc")));d.advance(5000);check(d.failures==3);
        d.ready=false;check(q.enqueue(batch("abc")));d.advance(59999);check(d.failures==3);
        d.advance(1);check(d.failures==4);
        check(q.enqueue(batch("abc")));q.clear();d.advance(60001);check(d.failures==4);
        d.ready=true;check(q.enqueue(batch("abc")));q.complete(UUID1,true);check(d.failures==4);
        System.out.println("App tests passed: UTF-8 framing, serialization, retry limits, timeout, disconnect and reconnect.");
    }
}
