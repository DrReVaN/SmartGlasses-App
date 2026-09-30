package com.test;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns pairing, reconnect, time, private notifications and OTA independently of activities. */
public class BluetoothLeService extends Service {
    public static final String ACTION_CONNECT="com.test.CONNECT", ACTION_STOP="com.test.STOP";
    public static final String ADDRESS="address";
    public enum State { DISCONNECTED, CONNECTING, DISCOVERING, PAIRING, READY, BOOTLOADER, UPDATE_WAIT, UPLOADING, VERIFYING, ERROR }
    public interface Listener { void changed(); }
    private static BluetoothLeService active;
    private static int missed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService files=Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners=new HashSet<>();
    private final MessageOutbox outbox=new MessageOutbox();
    private final LocalBinder binder=new LocalBinder();
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private boolean desired, foreground, validServices, messageInFlight, destroyed;
    private String address, detail="", deviceName="Smartglasses", clockSent="";
    private State state=State.DISCONNECTED;
    private int retries, connectionToken, sent, progress, packageGeneration;
    private MessageOutbox.Message sending;
    private byte[] version;
    private OtaImage image;
    private boolean updating, controlSent, committing, awaitingVerification, loading;
    private boolean beginAccepted;
    private String updateFailure="";
    private int otaOffset;
    private long lastPublished;
    private PowerManager.WakeLock updateWake;
    private final GattQueue queue=new GattQueue(new GattQueue.Driver() {
        public long now() { return SystemClock.elapsedRealtime(); }
        public boolean ready() {
            if (!validServices || gatt==null || !BlePermissions.canConnect(BluetoothLeService.this)) return false;
            try { return gatt.getDevice().getBondState()==BluetoothDevice.BOND_BONDED; }
            catch (SecurityException e) { return false; }
        }
        public boolean start(GattQueue.Operation op) {
            if (gatt==null || !BlePermissions.canConnect(BluetoothLeService.this)) return false;
            BluetoothGattCharacteristic c=find(op.uuid);
            if (c==null) return false;
            try {
                if (op.data==null) return (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ)!=0 && gatt.readCharacteristic(c);
                if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE)==0) return false;
                if (Build.VERSION.SDK_INT>=33) return gatt.writeCharacteristic(c,op.data.clone(),BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
                c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); c.setValue(op.data.clone());
                return gatt.writeCharacteristic(c);
            } catch (SecurityException | IllegalArgumentException e) { return false; }
        }
        public void later(Runnable task,long delay) { main.postDelayed(task,delay); }
        public void failed(String reason) { lost(getString(R.string.transfer_failed)); }
    });
    public final class LocalBinder extends Binder { public BluetoothLeService service() { return BluetoothLeService.this; } }
    public State state() { return state; }
    public String detail() { return detail; }
    public String address() { return address; }
    public int pending() { return outbox.size(SystemClock.elapsedRealtime()); }
    public int dropped() { return outbox.dropped()+missed; }
    public int sent() { return sent; }
    public int progress() { return progress; }
    public boolean updating() { return updating || awaitingVerification; }
    public boolean canReadDiagnostics() {
        return (state==State.READY || state==State.BOOTLOADER) && !updating() && queue.idle();
    }
    public boolean canCancelUpdate() { return updating && state!=State.VERIFYING; }
    public boolean loading() { return loading; }
    public OtaImage image() { return image; }
    public void clearImage() { if (!updating() && !loading) { ++packageGeneration; image=null; publish(); } }
    public void discardPending() { outbox.discardWaiting(); publish(); }
    public void addListener(Listener listener) { listeners.add(listener); listener.changed(); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    public static void deliverNotification(String text) {
        if (active!=null && active.desired) active.sendText(text); else missed++;
    }
    public static void listenerStateChanged() { if (active!=null) active.publish(); }
    @Override public void onCreate() {
        super.onCreate(); active=this;
        BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter=manager==null ? null : manager.getAdapter();
        IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        // These are protected system actions; the Bluetooth process can use a different UID.
        if (Build.VERSION.SDK_INT>=33) registerReceiver(systemReceiver,filter,Context.RECEIVER_EXPORTED);
        else registerReceiver(systemReceiver,filter);
        main.post(tick);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (intent!=null && ACTION_STOP.equals(intent.getAction())) { disconnect(); return START_NOT_STICKY; }
        SharedPreferences prefs=getSharedPreferences("glasses",MODE_PRIVATE);
        String next=intent==null ? prefs.getString(ADDRESS,null) : intent.getStringExtra(ADDRESS);
        if (next==null || !BluetoothAdapter.checkBluetoothAddress(next) || adapter==null || !BlePermissions.canConnect(this)) {
            terminal(getString(R.string.bluetooth_permission)); return START_NOT_STICKY;
        }
        if (intent==null && !prefs.getBoolean("connection_enabled",false)) { stopSelf(); return START_NOT_STICKY; }
        boolean same=desired && next.equals(address) && gatt!=null;
        if (!same) { desired=false; closeGatt(); outbox.clear(); messageInFlight=false; sending=null; cancelUpdate(); if (!next.equals(address)) updateFailure=""; address=next; retries=0; }
        desired=true; prefs.edit().putString(ADDRESS,address).putBoolean("connection_enabled",true).apply();
        if (!promote()) return START_NOT_STICKY;
        if (!same) connect();
        return START_STICKY;
    }
    private boolean promote() {
        try {
            if (Build.VERSION.SDK_INT>=26) {
                NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                manager.createNotificationChannel(new NotificationChannel("connection",getString(R.string.connection_channel),NotificationManager.IMPORTANCE_LOW));
            }
            if (Build.VERSION.SDK_INT>=29) startForeground(42,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(42,notification());
            foreground=true; return true;
        } catch (RuntimeException e) { terminal(getString(R.string.foreground_failed)); return false; }
    }
    private Notification notification() {
        Intent open=new Intent(this,DeviceControlActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,BluetoothLeService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26 ? new Notification.Builder(this,"connection") : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_status).setContentTitle(getString(R.string.app_name)).setContentText(statusText())
            .setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,getString(R.string.disconnect),stopIntent).build();
    }
    public String statusText() {
        int id;
        switch (state) {
            case CONNECTING: id=R.string.connecting; break;
            case DISCOVERING: id=R.string.discovering; break;
            case PAIRING: id=R.string.pairing; break;
            case READY: id=R.string.ready; break;
            case BOOTLOADER: id=R.string.bootloader; break;
            case UPDATE_WAIT: id=R.string.update_wait; break;
            case UPLOADING: id=R.string.uploading; break;
            case VERIFYING: id=R.string.verifying; break;
            case ERROR: id=R.string.connection_error; break;
            default: id=R.string.disconnected;
        }
        return getString(id);
    }
    private void publish() {
        for (Listener listener:new ArrayList<>(listeners)) listener.changed();
        long now=SystemClock.elapsedRealtime();
        if (foreground && now-lastPublished>=1000 && (Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)) {
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(42,notification()); lastPublished=now;
        }
    }
    private void setState(State value,String message) { state=value; detail=message; publish(); }
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            int before=outbox.dropped(); outbox.expire(SystemClock.elapsedRealtime());
            if (desired && !BlePermissions.canConnect(BluetoothLeService.this)) terminal(getString(R.string.bluetooth_permission));
            else pump();
            if (before!=outbox.dropped()) publish();
            main.postDelayed(this,1000);
        }
    };
    private void connect() {
        if (!desired || destroyed) return;
        if (!BlePermissions.canConnect(this)) { terminal(getString(R.string.bluetooth_permission)); return; }
        closeGatt();
        try {
            if (!adapter.isEnabled()) { setState(State.ERROR,getString(R.string.bluetooth_off)); return; }
            setState(State.CONNECTING,"");
            BluetoothDevice device=adapter.getRemoteDevice(address);
            gatt=Build.VERSION.SDK_INT>=23 ? device.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE) : device.connectGatt(this,false,callback);
            if (gatt==null) { lost(getString(R.string.connection_error)); return; }
            final int token=connectionToken;
            main.postDelayed(() -> { if (token==connectionToken && (state==State.CONNECTING || state==State.DISCOVERING || state==State.PAIRING)) lost(getString(R.string.connection_timeout)); },60000);
        } catch (SecurityException | IllegalArgumentException e) { terminal(getString(R.string.bluetooth_permission)); }
    }
    private void closeGatt() {
        ++connectionToken; validServices=false; version=null; clockSent=""; queue.clear();
        BluetoothGatt old=gatt; gatt=null;
        if (old!=null) {
            try { if (BlePermissions.canConnect(this)) old.disconnect(); old.close(); } catch (SecurityException ignored) { /* Revoked by the user. */ }
        }
    }
    private void lost(String why) {
        if (messageInFlight) { outbox.failed(sending); messageInFlight=false; sending=null; }
        if (updating && committing) {
            if (!beginAccepted) updateFailure=getString(R.string.update_failed_begin);
            else if (state==State.VERIFYING) updateFailure=getString(R.string.update_failed_commit);
            else updateFailure=getString(R.string.update_failed_transfer,otaOffset,image.size());
            updating=false; committing=false; controlSent=false; releaseWake(); why=updateFailure;
        }
        closeGatt(); setState(State.ERROR,why);
        if (!desired) return;
        if (++retries>8) {
            // Never leave the UI locked if the post-update reconnect cannot complete.
            boolean updatePending=updating() || state==State.VERIFYING;
            updating=false; committing=false; controlSent=false; awaitingVerification=false; releaseWake();
            if (updatePending) updateFailure=getString(R.string.update_reconnect_failed);
            detail=updateFailure.isEmpty() ? getString(R.string.retry_exhausted) : updateFailure; publish(); return;
        }
        long delay=Math.min(30000,1000L << Math.min(retries-1,5));
        int token=connectionToken;
        main.postDelayed(() -> { if (token==connectionToken && desired) connect(); },delay);
    }
    private void terminal(String why) {
        desired=false; messageInFlight=false; sending=null; outbox.clear(); cancelUpdate(); closeGatt();
        getSharedPreferences("glasses",MODE_PRIVATE).edit().putBoolean("connection_enabled",false).apply();
        setState(State.ERROR,why); stopForeground(true); foreground=false; stopSelf();
    }
    public void disconnect() {
        terminal(getString(R.string.disconnected)); setState(State.DISCONNECTED,"");
    }
    private final BroadcastReceiver systemReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int value=intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
                if (value==BluetoothAdapter.STATE_OFF && desired) lost(getString(R.string.bluetooth_off));
                else if (value==BluetoothAdapter.STATE_ON && desired) { retries=0; connect(); }
                return;
            }
            BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (gatt==null || device==null || !device.getAddress().equals(address)) return;
            int bond=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.ERROR);
            if (bond==BluetoothDevice.BOND_BONDED) { queue.wake(); }
            else if (bond==BluetoothDevice.BOND_NONE && intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,0)!=BluetoothDevice.BOND_NONE) terminal(getString(R.string.pairing_failed));
        }
    };
    private BluetoothGattCharacteristic find(UUID uuid) {
        if (gatt==null) return null;
        UUID service=uuid.equals(GlassesProfile.VERSION) || uuid.equals(GlassesProfile.NAME) || uuid.equals(GlassesProfile.DIAGNOSTICS) ? GlassesProfile.INFO
            : uuid.equals(GlassesProfile.BEGIN) || uuid.equals(GlassesProfile.DATA) || uuid.equals(GlassesProfile.END) ? GlassesProfile.OTA : GlassesProfile.RECEIVE;
        BluetoothGattService s=gatt.getService(service); return s==null ? null : s.getCharacteristic(uuid);
    }
    private boolean has(UUID uuid,int property) { BluetoothGattCharacteristic c=find(uuid); return c!=null && (c.getProperties() & property)!=0; }
    private boolean profileValid() {
        if (!has(GlassesProfile.VERSION,BluetoothGattCharacteristic.PROPERTY_READ) || !has(GlassesProfile.NAME,BluetoothGattCharacteristic.PROPERTY_READ) || !has(GlassesProfile.DIAGNOSTICS,BluetoothGattCharacteristic.PROPERTY_READ)) return false;
        for (UUID uuid:Arrays.asList(GlassesProfile.TIME,GlassesProfile.MESSAGE,GlassesProfile.CONTROL,GlassesProfile.BEGIN,GlassesProfile.DATA,GlassesProfile.END))
            if (!has(uuid,BluetoothGattCharacteristic.PROPERTY_WRITE)) return false;
        return true;
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt current,int status,int newState) {
            main.post(() -> {
                if (current!=gatt) return;
                if (status!=BluetoothGatt.GATT_SUCCESS || newState==BluetoothProfile.STATE_DISCONNECTED) { lost(getString(R.string.connection_lost)); return; }
                if (newState==BluetoothProfile.STATE_CONNECTED) {
                    try { setState(State.DISCOVERING,""); if (!current.discoverServices()) lost(getString(R.string.connection_error)); }
                    catch (SecurityException e) { terminal(getString(R.string.bluetooth_permission)); }
                }
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt current,int status) {
            main.post(() -> {
                if (current!=gatt) return;
                if (status!=BluetoothGatt.GATT_SUCCESS) { lost(getString(R.string.connection_error)); return; }
                if (!profileValid()) { terminal(getString(R.string.unsupported_device)); return; }
                validServices=true;
                try {
                    if (!BlePermissions.canConnect(BluetoothLeService.this)) { terminal(getString(R.string.bluetooth_permission)); return; }
                    if (current.getDevice().getBondState()!=BluetoothDevice.BOND_BONDED) {
                        setState(State.PAIRING,getString(R.string.pairing_help));
                        if (current.getDevice().getBondState()==BluetoothDevice.BOND_NONE && !current.getDevice().createBond()) { terminal(getString(R.string.pairing_failed)); return; }
                    }
                    operation(GlassesProfile.VERSION,null,5000,() -> versionRead());
                } catch (SecurityException e) { terminal(getString(R.string.bluetooth_permission)); }
            });
        }
        private void read(BluetoothGatt current,BluetoothGattCharacteristic characteristic,byte[] value,int status) {
            byte[] data=value==null ? null : value.clone(); UUID uuid=characteristic.getUuid();
            main.post(() -> {
                if (current!=gatt) return;
                if (status==BluetoothGatt.GATT_SUCCESS) {
                    if (uuid.equals(GlassesProfile.VERSION)) version=data;
                    else if (uuid.equals(GlassesProfile.NAME) && data!=null) deviceName=new String(data,StandardCharsets.UTF_8);
                    else if (uuid.equals(GlassesProfile.DIAGNOSTICS) && data!=null && data.length==20) {
                        java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        detail=getString(R.string.diagnostics,b.getInt() & 0xffffffffL,b.getInt() & 0xffffffffL,b.getInt() & 0xffffffffL,b.getInt() & 0xffffffffL,b.getInt() & 0xffffffffL);
                        if (!updateFailure.isEmpty()) detail=updateFailure+"\n"+detail;
                    }
                }
                queue.complete(uuid,status==BluetoothGatt.GATT_SUCCESS); publish();
            });
        }
        @Override public void onCharacteristicRead(BluetoothGatt current,BluetoothGattCharacteristic c,int status) { read(current,c,c.getValue(),status); }
        @Override public void onCharacteristicRead(BluetoothGatt current,BluetoothGattCharacteristic c,byte[] value,int status) { read(current,c,value,status); }
        @Override public void onCharacteristicWrite(BluetoothGatt current,BluetoothGattCharacteristic c,int status) {
            main.post(() -> { if (current==gatt) queue.complete(c.getUuid(),status==BluetoothGatt.GATT_SUCCESS); });
        }
    };
    private boolean operation(UUID uuid,byte[] value,long timeout,Runnable done) {
        return queue.enqueue(Collections.singletonList(new GattQueue.Operation(uuid,value,timeout,done)));
    }
    private void versionRead() {
        if (!GlassesProfile.supportedVersion(version)) { terminal(getString(R.string.unsupported_version)); return; }
        retries=0;
        if (version[3]==1) {
            if (awaitingVerification) {
                awaitingVerification=false; updateFailure=getString(R.string.update_verify_failed);
                setState(State.BOOTLOADER,updateFailure); return;
            }
            if (updating) startUpload(); else setState(State.BOOTLOADER,updateFailure.isEmpty() ? getString(R.string.bootloader_help) : updateFailure);
        } else {
            if (awaitingVerification) { awaitingVerification=false; updating=false; committing=false; updateFailure=""; detail=getString(R.string.update_success); }
            else if (updating && controlSent) { terminal(getString(R.string.update_mode_failed)); return; }
            else if (!updateFailure.isEmpty()) detail=updateFailure;
            setState(State.READY,detail);
            if (queue.idle()) operation(GlassesProfile.NAME,null,5000,this::pump);
            pump();
        }
    }
    public boolean sendText(String text) {
        if (!desired || updating() || state==State.BOOTLOADER) { missed++; publish(); return false; }
        boolean accepted=outbox.offer(text,SystemClock.elapsedRealtime()); pump(); publish(); return accepted;
    }
    private void pump() {
        if (!desired || gatt==null || !validServices || version==null || !queue.idle()) return;
        if (updating && !controlSent && !committing) {
            if (version!=null && version[3]==1) startUpload(); else requestBootloader();
            return;
        }
        if (state!=State.READY || updating()) return;
        String now=new SimpleDateFormat("HHmmddMMyyyy",Locale.US).format(new Date());
        if (!now.equals(clockSent)) {
            if (operation(GlassesProfile.TIME,now.getBytes(StandardCharsets.US_ASCII),5000,() -> { clockSent=now; pump(); })) return;
        }
        MessageOutbox.Message message=outbox.peek(SystemClock.elapsedRealtime());
        if (message==null) return;
        List<byte[]> frames=SmartglassesProtocol.frames(message.text);
        List<GattQueue.Operation> batch=new ArrayList<>();
        for (int i=0;i<frames.size();i++) {
            Runnable done=i==frames.size()-1 ? () -> { messageInFlight=false; sending=null; outbox.delivered(message); sent++; publish(); pump(); } : null;
            batch.add(new GattQueue.Operation(GlassesProfile.MESSAGE,frames.get(i),5000,done));
        }
        sending=message; messageInFlight=true; outbox.started(message);
        if (!queue.enqueue(batch)) { messageInFlight=false; sending=null; outbox.failed(message); publish(); }
    }
    public void readDiagnostics() {
        if (canReadDiagnostics()) {
            if (!operation(GlassesProfile.DIAGNOSTICS,null,5000,this::pump)) { detail=getString(R.string.transfer_failed); publish(); }
            else publish();
        }
    }
    public void loadPackage(Uri binary,Uri manifest) {
        if (loading || updating()) return;
        loading=true; image=null; int token=++packageGeneration; publish();
        files.execute(() -> {
            try {
                byte[] data=readLimited(binary,OtaImage.MAX_SIZE);
                JSONObject m=new JSONObject(new String(readLimited(manifest,16384),StandardCharsets.UTF_8));
                OtaImage loaded=new OtaImage(data,m.getInt("format"),m.getString("target"),m.getString("profile"),Long.decode(m.getString("address")),m.getInt("size"),Long.parseLong(m.getString("crc32"),16),m.getString("sha256"),m.getString("version"));
                main.post(() -> { if (!destroyed && token==packageGeneration) { image=loaded; loading=false; detail=getString(R.string.package_valid,loaded.size(),loaded.version); publish(); } });
            } catch (Exception e) {
                main.post(() -> { if (!destroyed && token==packageGeneration) { loading=false; image=null; detail=getString(R.string.package_invalid); publish(); } });
            } finally {
                for (Uri uri:Arrays.asList(binary,manifest)) try { getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) { /* Temporary document grant. */ }
            }
        });
    }
    private byte[] readLimited(Uri uri,int limit) throws java.io.IOException {
        try (InputStream in=getContentResolver().openInputStream(uri); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if (in==null) throw new java.io.IOException("File");
            byte[] block=new byte[4096]; int read;
            while ((read=in.read(block))!=-1) { if (out.size()+read>limit) throw new java.io.IOException("Size"); out.write(block,0,read); }
            return out.toByteArray();
        }
    }
    public boolean beginUpdate() {
        if (image==null || updating() || !(state==State.READY || state==State.BOOTLOADER)) return false;
        updating=true; controlSent=false; committing=false; awaitingVerification=false; beginAccepted=false; updateFailure=""; progress=0;
        // Finish an already accepted ATT batch, but do not start another normal message.
        outbox.discardWaiting(); setState(State.UPDATE_WAIT,getString(R.string.update_wait_help)); pump();
        return true;
    }
    private void requestBootloader() {
        controlSent=true;
        if (!operation(GlassesProfile.CONTROL,"OTA1".getBytes(StandardCharsets.US_ASCII),5000,() -> {
            int token=connectionToken;
            main.postDelayed(() -> { if (token==connectionToken && updating && !committing && state==State.UPDATE_WAIT) { cancelUpdate(); setState(State.READY,getString(R.string.update_mode_failed)); pump(); } },120000);
        })) { cancelUpdate(); detail=getString(R.string.transfer_failed); publish(); }
    }
    private void startUpload() {
        if (image==null || version==null || version[3]!=1) { cancelUpdate(); return; }
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        updateWake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getPackageName()+":ota"); updateWake.acquire(900000);
        committing=true; controlSent=true; otaOffset=0; setState(State.UPLOADING,getString(R.string.keep_power));
        if (!operation(GlassesProfile.BEGIN,image.begin(),15000,() -> { beginAccepted=true; chunk(); })) lost(getString(R.string.update_interrupted));
    }
    private void chunk() {
        if (!updating || image==null) return;
        if (otaOffset<image.size()) {
            int offset=otaOffset;
            if (!operation(GlassesProfile.DATA,image.packet(offset),5000,() -> {
                otaOffset=offset+Math.min(16,image.size()-offset);
                int next=otaOffset*100/image.size(); if (next!=progress) { progress=next; publish(); }
                chunk();
            })) lost(getString(R.string.update_interrupted));
        } else {
            setState(State.VERIFYING,getString(R.string.verifying_help));
            if (!operation(GlassesProfile.END,image.end(),10000,() -> {
                awaitingVerification=true; updating=false; committing=false;
                releaseWake();
                main.postDelayed(() -> { if (awaitingVerification && desired) { closeGatt(); connect(); } },1500);
            })) lost(getString(R.string.update_interrupted));
        }
    }
    public void cancelUpdate() {
        boolean transfer=committing;
        updating=false; controlSent=false; committing=false; awaitingVerification=false;
        releaseWake();
        if (transfer) { closeGatt(); if (desired) connect(); }
        else if (state==State.UPDATE_WAIT) setState(version!=null && version[3]==1 ? State.BOOTLOADER : State.READY,getString(R.string.update_cancelled));
        publish();
    }
    @Override public void onDestroy() {
        destroyed=true; ++packageGeneration; releaseWake(); closeGatt(); outbox.clear(); sending=null;
        main.removeCallbacksAndMessages(null); files.shutdownNow(); listeners.clear(); unregisterReceiver(systemReceiver);
        if (active==this) active=null; super.onDestroy();
    }
    private void releaseWake() { if (updateWake!=null && updateWake.isHeld()) updateWake.release(); updateWake=null; }
}
