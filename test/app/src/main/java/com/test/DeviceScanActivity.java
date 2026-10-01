package com.test;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.*;

/** Short foreground scans; first result is immediately selectable, with no RSSI gate. */
public class DeviceScanActivity extends Activity {
    private static final int PERMISSIONS=1, ENABLE=2, NOTIFICATIONS=3;
    private static final long SCAN_MS=10000;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,BluetoothDevice> devices=new LinkedHashMap<>();
    private final List<String> addresses=new ArrayList<>();
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private ArrayAdapter<String> rows;
    private TextView status;
    private AppUpdateUi appUpdates;
    private Button search;
    private boolean scanning, visible;
    private int generation;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); setTitle(R.string.app_name);
        LinearLayout root=Ui.page(this);
        Ui.text(this,root,R.string.scan_help);
        status=Ui.text(this,root,R.string.scan_idle);
        search=Ui.button(this,root,R.string.scan,v -> requestScan());
        Ui.button(this,root,R.string.permissions_settings,v -> Ui.appSettings(this));
        if (Build.VERSION.SDK_INT>=23 && Build.VERSION.SDK_INT<=30) Ui.button(this,root,R.string.location_settings,v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        Ui.button(this,root,R.string.notification_settings,v -> Ui.notificationSettings(this));
        Ui.button(this,root,R.string.open_controls,v -> startActivity(new Intent(this,DeviceControlActivity.class)));
        appUpdates=new AppUpdateUi(this,root);
        ListView list=new ListView(this);
        rows=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());
        list.setAdapter(rows); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((parent,view,position,id) -> {
            if (!BlePermissions.canConnect(this) || position>=addresses.size()) return;
            stopScan();
            String address=addresses.get(position);
            getSharedPreferences("glasses",MODE_PRIVATE).edit().putString(BluetoothLeService.ADDRESS,address).apply();
            startActivity(new Intent(this,DeviceControlActivity.class).putExtra(BluetoothLeService.ADDRESS,address));
        });
        BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter=manager==null ? null : manager.getAdapter();
        if (adapter==null) { search.setEnabled(false); status.setText(R.string.ble_unavailable); }
    }
    @Override protected void onStart() { super.onStart(); appUpdates.start(); }
    @Override protected void onStop() { appUpdates.stop(); super.onStop(); }
    @Override protected void onResume() { super.onResume(); visible=true; if (!BlePermissions.canScan(this)) status.setText(R.string.bluetooth_permission); }
    @Override protected void onPause() { visible=false; stopScan(); super.onPause(); }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void requestScan() {
        if (scanning) { stopScan(); return; }
        if (adapter==null) return;
        if (!BlePermissions.canScan(this)) {
            if (Build.VERSION.SDK_INT>=23) requestPermissions(BlePermissions.scanPermissions(Build.VERSION.SDK_INT),PERMISSIONS);
            return;
        }
        try {
            if (!adapter.isEnabled()) { startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE); return; }
            if (Build.VERSION.SDK_INT>=23 && Build.VERSION.SDK_INT<=30 && Settings.Secure.getInt(getContentResolver(),Settings.Secure.LOCATION_MODE,0)==Settings.Secure.LOCATION_MODE_OFF) {
                status.setText(R.string.location_disabled); return;
            }
            if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
                    && !getSharedPreferences("glasses",MODE_PRIVATE).getBoolean("asked_notification",false)) {
                getSharedPreferences("glasses",MODE_PRIVATE).edit().putBoolean("asked_notification",true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS); return;
            }
            startScan();
        } catch (SecurityException e) { status.setText(R.string.bluetooth_permission); }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if (request==NOTIFICATIONS) { requestScan(); return; }
        if (request==PERMISSIONS) {
            if (BlePermissions.canScan(this)) requestScan(); else status.setText(R.string.permission_denied);
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(appUpdates.result(request)) return;
        if (request==ENABLE) { if (result==RESULT_OK) requestScan(); else status.setText(R.string.bluetooth_off); }
    }
    private final Runnable timeout=() -> { stopScan(); status.setText(devices.isEmpty() ? R.string.scan_empty : R.string.scan_finished); };
    private void startScan() {
        stopScan(); devices.clear(); addresses.clear(); rows.clear();
        scanner=adapter.getBluetoothLeScanner();
        if (scanner==null) { status.setText(R.string.bluetooth_off); return; }
        ++generation; scanning=true; search.setText(R.string.scan_stop); status.setText(R.string.scanning);
        try {
            // Legacy firmware advertises its name without a service UUID. Validate the full
            // service after selection, before pairing; do not filter it out by UUID here.
            scanner.startScan(Collections.emptyList(),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);
            main.postDelayed(timeout,SCAN_MS);
        } catch (SecurityException | IllegalStateException e) { stopScan(); status.setText(R.string.scan_failed); }
    }
    private void stopScan() {
        ++generation; main.removeCallbacks(timeout);
        boolean wasScanning=scanning; scanning=false;
        if (wasScanning && scanner!=null) try { scanner.stopScan(callback); } catch (SecurityException | IllegalStateException ignored) { /* Bluetooth/permission changed. */ }
        if (search!=null) search.setText(R.string.scan);
    }
    private final ScanCallback callback=new ScanCallback() {
        @Override public void onScanResult(int type,ScanResult result) {
            int token=generation;
            main.post(() -> {
                if (!visible || !scanning || token!=generation || !BlePermissions.canScan(DeviceScanActivity.this)) return;
                BluetoothDevice device=result.getDevice();
                String address=device.getAddress();
                if (devices.size()>=64 && !devices.containsKey(address)) return;
                String name=result.getScanRecord()==null ? null : result.getScanRecord().getDeviceName();
                try { if (name==null && BlePermissions.canConnect(DeviceScanActivity.this)) name=device.getName(); }
                catch (SecurityException e) { stopScan(); status.setText(R.string.bluetooth_permission); return; }
                if (name==null) name=getString(R.string.unknown_device);
                String row=getString(R.string.device_row,name,address,result.getRssi());
                int index=addresses.indexOf(address);
                if (index<0) { devices.put(address,device); addresses.add(address); rows.add(row); }
                else if (!row.equals(rows.getItem(index))) { rows.remove(rows.getItem(index)); rows.insert(row,index); }
            });
        }
        @Override public void onScanFailed(int code) {
            main.post(() -> { if (scanning) { stopScan(); status.setText(R.string.scan_failed); } });
        }
    };
}
