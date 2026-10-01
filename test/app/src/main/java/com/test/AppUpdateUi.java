package com.test;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

/** Consent belongs to the visible activity; the system confirms installation. */
final class AppUpdateUi implements AppUpdates.Listener {
    private static final int INSTALL_REQUEST=80;
    private final Activity activity;
    private final AppUpdates updates;
    private final TextView status;
    private final Button check,update;
    private boolean visible,installWhenReady;
    private AlertDialog dialog;
    AppUpdateUi(Activity activity,LinearLayout root) {
        this.activity=activity;updates=AppUpdates.get(activity);
        status=Ui.text(activity,root,R.string.app_update_empty);
        check=Ui.button(activity,root,R.string.app_update_check,v->updates.check(true));
        update=Ui.button(activity,root,R.string.app_update_download,v->{ if(updates.verified!=null) install();else confirm(); });
        changed();
    }
    void start() { visible=true;updates.add(this);updates.check(false); }
    void stop() { visible=false;updates.remove(this);if(dialog!=null) { dialog.dismiss();dialog=null; } }
    boolean result(int request) {
        if(request!=INSTALL_REQUEST) return false;
        AppUpdates.installationPending=false;changed();return true;
    }
    @Override public void changed() {
        status.setText(updates.status.isEmpty() ? activity.getString(R.string.app_update_installed,updates.installedLabel()) : updates.status);check.setEnabled(!updates.busy);
        update.setVisibility(updates.offer==null ? View.GONE : View.VISIBLE);
        update.setEnabled(!updates.busy && !AppUpdates.installationPending && !BluetoothLeService.firmwareUpdateRunning());
        update.setText(updates.verified==null ? R.string.app_update_download : R.string.app_update_install);
        if(visible && installWhenReady && !updates.busy) { installWhenReady=false;if(updates.verified!=null) install(); }
        else if(visible && !AppUpdates.installationPending && updates.shouldPrompt() && !BluetoothLeService.firmwareUpdateRunning()) confirm();
    }
    private void confirm() {
        if(!visible || updates.offer==null || updates.busy || BluetoothLeService.firmwareUpdateRunning()) return;
        AppRelease chosen=updates.offer;updates.prompted=chosen.code;
        dialog=new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.app_update_available,chosen.version.toString()))
            .setMessage(R.string.app_update_confirm).setNegativeButton(R.string.firmware_later,(d,w)->updates.defer(chosen))
            .setPositiveButton(R.string.app_update_download,(d,w)->{ if(updates.offer==chosen) { installWhenReady=true;updates.download(); } }).create();
        dialog.setOnCancelListener(d->updates.defer(chosen));dialog.show();
    }
    private void install() {
        if(updates.verified==null || updates.offer==null) return;
        if(BluetoothLeService.firmwareUpdateRunning()) { Toast.makeText(activity,R.string.app_update_ota_busy,Toast.LENGTH_LONG).show();return; }
        try {
            if(Build.VERSION.SDK_INT>=26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity).setMessage(R.string.app_update_permission)
                    .setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton(R.string.app_update_settings,(d,w)->activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())))).show();return;
            }
            AppUpdateRepository.check(updates.offer,AppUpdateRepository.read(updates.verified,AppRelease.MAX_APK));
            AppIdentity.verify(activity,updates.verified,updates.offer);
            Uri uri=Uri.parse("content://"+activity.getPackageName()+".appupdates/apk/"+updates.verified.getName());
            Intent install=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);install.setClipData(ClipData.newRawUri("APK",uri));
            AppUpdates.installationPending=true;activity.startActivityForResult(install,INSTALL_REQUEST);
        } catch(Exception e) { AppUpdates.installationPending=false;Toast.makeText(activity,R.string.app_update_install_failed,Toast.LENGTH_LONG).show(); }
    }
}
