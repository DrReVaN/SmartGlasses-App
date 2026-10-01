package com.test;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import java.util.*;

/** User functions are bound to known channels, never arbitrary GATT entries. */
public class DeviceControlActivity extends Activity implements BluetoothLeService.Listener {
    private static final int BINARY=10, MANIFEST=11, PERMISSIONS=12;
    private BluetoothLeService service;
    private AppUpdateUi appUpdates;
    private boolean bound, autoConnect;
    private TextView status, detail, statistics, listener, packageInfo;
    private TextView firmwareVersion, firmwareStatus, firmwareOffer;
    private Button checkFirmware, versions, prepareNew, deferNew;
    private Button send, diagnostics, startUpdate, cancelUpdate, select, connect;
    private EditText text;
    private ProgressBar progress;
    private Uri binary;
    private Uri manifest;
    private boolean packageChanged;
    private String address;
    private final ServiceConnection connection=new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name,IBinder binder) {
            if (!bound) return;
            service=((BluetoothLeService.LocalBinder)binder).service(); service.addListener(DeviceControlActivity.this); loadPending();
        }
        @Override public void onServiceDisconnected(ComponentName name) { service=null; changed(); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); setTitle(R.string.app_name);
        address=getIntent().getStringExtra(BluetoothLeService.ADDRESS);
        autoConnect=saved==null && address!=null;
        if (address==null) address=getSharedPreferences("glasses",MODE_PRIVATE).getString(BluetoothLeService.ADDRESS,null);
        if (saved!=null && saved.getString("binary")!=null) binary=Uri.parse(saved.getString("binary"));
        if (saved!=null && saved.getString("manifest")!=null) manifest=Uri.parse(saved.getString("manifest"));
        packageChanged=saved!=null && saved.getBoolean("packageChanged");
        LinearLayout page=Ui.page(this);
        ScrollView scroll=new ScrollView(this); page.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); scroll.addView(root);
        status=Ui.text(this,root,R.string.disconnected);
        detail=Ui.text(this,root,R.string.controls_help);
        statistics=Ui.text(this,root,R.string.empty_text);
        connect=Ui.button(this,root,R.string.connect,v -> connectDevice());
        Ui.button(this,root,R.string.disconnect,v -> { if (service!=null) service.disconnect(); });
        Ui.button(this,root,R.string.choose_device,v -> startActivity(new Intent(this,DeviceScanActivity.class)));
        Ui.button(this,root,R.string.permissions_settings,v -> Ui.appSettings(this));
        listener=Ui.text(this,root,R.string.listener_missing);
        Ui.button(this,root,R.string.notification_settings,v -> Ui.notificationSettings(this));
        Switch forwarding=new Switch(this); forwarding.setText(R.string.forward_notifications);
        forwarding.setChecked(getSharedPreferences("glasses",MODE_PRIVATE).getBoolean("forward",true)); root.addView(forwarding);
        forwarding.setOnCheckedChangeListener((button,checked) -> {
            getSharedPreferences("glasses",MODE_PRIVATE).edit().putBoolean("forward",checked).apply();
            if (!checked && service!=null) service.discardPending();
        });
        Ui.button(this,root,R.string.app_filter,v -> chooseApps());
        text=new EditText(this); text.setId(android.R.id.edit); text.setHint(R.string.text_hint);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); text.setMaxLines(4); root.addView(text);
        send=Ui.button(this,root,R.string.send_text,v -> {
            boolean accepted=service!=null && service.sendText(text.getText().toString());
            Toast.makeText(this,accepted ? R.string.message_accepted : R.string.message_rejected,Toast.LENGTH_SHORT).show();
        });
        diagnostics=Ui.button(this,root,R.string.read_diagnostics,v -> { if (service!=null) service.readDiagnostics(); });
        Ui.text(this,root,R.string.ota_heading);
        Ui.text(this,root,R.string.ota_help);
        firmwareVersion=Ui.text(this,root,R.string.firmware_unread);
        firmwareStatus=Ui.text(this,root,R.string.empty_text);
        firmwareOffer=Ui.text(this,root,R.string.empty_text);
        prepareNew=Ui.button(this,root,R.string.firmware_prepare,v -> {
            if(service!=null && service.offeredRelease()!=null) prepareRelease(service.offeredRelease());
        });
        deferNew=Ui.button(this,root,R.string.firmware_later,v -> { if(service!=null) service.deferFirmware(); });
        checkFirmware=Ui.button(this,root,R.string.firmware_check,v -> { if(service!=null) service.checkFirmware(true); });
        versions=Ui.button(this,root,R.string.firmware_versions,v -> chooseVersion());
        packageInfo=Ui.text(this,root,R.string.package_missing);
        select=Ui.button(this,root,R.string.select_firmware,v -> chooseFile(BINARY));
        startUpdate=Ui.button(this,root,R.string.start_update,v -> confirmUpdate());
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); root.addView(progress);
        cancelUpdate=Ui.button(this,root,R.string.cancel_update,v -> { if (service!=null) service.cancelUpdate(); });
        appUpdates=new AppUpdateUi(this,root);
        changed();
    }
    @Override protected void onStart() {
        super.onStart(); appUpdates.start(); bound=bindService(new Intent(this,BluetoothLeService.class),connection,BIND_AUTO_CREATE);
    }
    @Override protected void onResume() {
        super.onResume(); changed();
        if (autoConnect) { autoConnect=false; connectDevice(); }
    }
    @Override protected void onStop() {
        appUpdates.stop();
        if (service!=null) service.removeListener(this);
        if (bound) { unbindService(connection); bound=false; }
        service=null; super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (binary!=null) out.putString("binary",binary.toString());
        if (manifest!=null) out.putString("manifest",manifest.toString());
        out.putBoolean("packageChanged",packageChanged); super.onSaveInstanceState(out);
    }
    private void connectDevice() {
        if (address==null) { startActivity(new Intent(this,DeviceScanActivity.class)); return; }
        if (!BlePermissions.canConnect(this)) {
            requestPermissions(BlePermissions.scanPermissions(Build.VERSION.SDK_INT),PERMISSIONS);
            return;
        }
        Intent intent=new Intent(this,BluetoothLeService.class).setAction(BluetoothLeService.ACTION_CONNECT).putExtra(BluetoothLeService.ADDRESS,address);
        try { if (Build.VERSION.SDK_INT>=26) startForegroundService(intent); else startService(intent); }
        catch (RuntimeException e) { Toast.makeText(this,R.string.foreground_failed,Toast.LENGTH_LONG).show(); }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if (request==PERMISSIONS && BlePermissions.canConnect(this)) connectDevice(); else if (request==PERMISSIONS) Toast.makeText(this,R.string.permission_denied,Toast.LENGTH_LONG).show();
    }
    @Override public void changed() {
        if (status==null) return;
        boolean ready=service!=null && service.state()==BluetoothLeService.State.READY;
        boolean boot=service!=null && service.state()==BluetoothLeService.State.BOOTLOADER;
        boolean updating=service!=null && service.updating();
        status.setText(service==null ? getString(R.string.disconnected) : service.statusText());
        detail.setText(service==null ? getString(R.string.controls_help) : service.detail());
        statistics.setText(service==null ? getString(R.string.empty_text) : getString(R.string.message_statistics,service.sent(),service.pending(),service.dropped()));
        listener.setText(NotifyListenerService.connected ? R.string.listener_ready : R.string.listener_missing);
        send.setEnabled(ready && !updating);
        diagnostics.setEnabled(service!=null && service.canReadDiagnostics());
        select.setEnabled(service!=null && !updating && !service.loading());
        startUpdate.setEnabled((ready || boot) && !updating && !AppUpdates.installationPending && service.image()!=null);
        cancelUpdate.setEnabled(service!=null && service.canCancelUpdate());
        firmwareVersion.setText(getString(R.string.firmware_installed,service==null ? getString(R.string.firmware_unread) : service.installedLabel()));
        firmwareStatus.setText(service==null ? "" : service.catalogStatus());
        FirmwareRelease offered=service==null ? null : service.offeredRelease();
        firmwareOffer.setText(offered==null ? "" : getString(R.string.firmware_available,offered.version.toString())
            +(offered.development ? " · "+getString(R.string.firmware_development) : ""));
        prepareNew.setVisibility(offered==null ? android.view.View.GONE : android.view.View.VISIBLE);
        deferNew.setVisibility(offered==null ? android.view.View.GONE : android.view.View.VISIBLE);
        prepareNew.setEnabled(service!=null && !updating && !service.loading());
        deferNew.setEnabled(!updating);
        checkFirmware.setEnabled(service!=null && !updating && !service.loading() && !service.catalogBusy());
        versions.setEnabled(service!=null && !service.catalog().isEmpty() && !updating && !service.loading());
        progress.setProgress(service==null ? 0 : service.progress());
        if (service!=null && service.loading()) packageInfo.setText(R.string.package_loading);
        else if (service!=null && service.image()!=null) packageInfo.setText(getString(R.string.package_valid,service.image().size(),service.image().version));
        else packageInfo.setText(R.string.package_missing);
        connect.setEnabled(!updating);
        if(appUpdates!=null) appUpdates.changed();
    }
    private void chooseVersion() {
        if(service==null) return;
        List<FirmwareRelease> available=new ArrayList<>(service.catalog());
        String[] labels=new String[available.size()]; FirmwareVersion installed=service.installedRelease();
        for(int i=0;i<labels.length;i++) {
            FirmwareRelease release=available.get(i);
            int relation=installed==null ? 1 : release.version.compareTo(installed);
            labels[i]=release.version+" · "+getString(relation<0 ? R.string.firmware_older : relation==0 ? R.string.firmware_current : R.string.firmware_newer)
                +(release.development ? " · "+getString(R.string.firmware_development) : "");
        }
        new AlertDialog.Builder(this).setTitle(R.string.firmware_versions).setItems(labels,(dialog,which) -> prepareRelease(available.get(which)))
            .setNegativeButton(android.R.string.cancel,null).show();
    }
    private void prepareRelease(FirmwareRelease release) {
        new AlertDialog.Builder(this).setTitle(getString(R.string.firmware_target,release.version.toString()))
            .setMessage(R.string.firmware_prepare_help).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.firmware_download,(dialog,which) -> { if(service!=null) service.loadRelease(release); }).show();
    }
    private void confirmUpdate() {
        if(service==null || service.image()==null) return;
        OtaImage selected=service.image(); String targetAddress=service.address();
        FirmwareVersion current=service.installedRelease();
        boolean downgrade=current!=null && FirmwareVersion.parse(selected.version).compareTo(current)<0;
        String action=getString(downgrade ? R.string.firmware_downgrade : R.string.firmware_target,selected.version);
        new AlertDialog.Builder(this).setTitle(action).setMessage(getString(R.string.update_confirmation))
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.start_update,(dialog,which) -> {
                if(service==null || service.image()!=selected || !Objects.equals(service.address(),targetAddress) || !service.beginUpdate())
                    Toast.makeText(this,R.string.message_rejected,Toast.LENGTH_SHORT).show();
            }).show();
    }
    private void chooseFile(int request) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,request);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(appUpdates.result(request)) { changed();return; }
        if (result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) { /* Some document providers grant only temporary access. */ }
        if (request==BINARY) {
            if (binary!=null) release(binary);
            if (manifest!=null) { release(manifest); manifest=null; }
            binary=uri; packageChanged=true; loadPending();
            Toast.makeText(this,R.string.select_manifest,Toast.LENGTH_LONG).show(); chooseFile(MANIFEST);
        } else if (request==MANIFEST && binary!=null) {
            manifest=uri; loadPending();
        }
    }
    private void loadPending() {
        if (service==null) return;
        if (packageChanged) { service.clearImage(); packageChanged=false; }
        if (binary!=null && manifest!=null) { service.loadPackage(binary,manifest); binary=null; manifest=null; }
    }
    private void release(Uri uri) { try { getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) { /* Not persisted. */ } }
    @Override protected void onDestroy() {
        if (isFinishing()) {
            if (binary!=null) release(binary);
            if (manifest!=null) release(manifest);
        }
        super.onDestroy();
    }
    private void chooseApps() {
        SharedPreferences prefs=getSharedPreferences("glasses",MODE_PRIVATE);
        List<String> apps=new ArrayList<>(prefs.getStringSet("known_apps",new HashSet<>())); Collections.sort(apps);
        if (apps.isEmpty()) { new AlertDialog.Builder(this).setMessage(R.string.filter_help).setPositiveButton(android.R.string.ok,null).show(); return; }
        Set<String> blocked=new HashSet<>(prefs.getStringSet("blocked_apps",new HashSet<>()));
        String[] labels=new String[apps.size()]; boolean[] enabled=new boolean[apps.size()];
        for (int i=0;i<apps.size();i++) {
            String app=apps.get(i); enabled[i]=!blocked.contains(app);
            try { labels[i]=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(app,0)).toString(); }
            catch (PackageManager.NameNotFoundException e) { labels[i]=app; }
        }
        new AlertDialog.Builder(this).setTitle(R.string.app_filter).setMultiChoiceItems(labels,enabled,(dialog,which,checked) -> {
            if (checked) blocked.remove(apps.get(which)); else blocked.add(apps.get(which));
        }).setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(dialog,which) -> prefs.edit().putStringSet("blocked_apps",blocked).apply()).show();
    }
}
