package com.test;
import android.content.*;
import android.os.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Process-wide checks shared by both screens, independent of glasses connectivity. */
final class AppUpdates {
    interface Listener { void changed(); }
    static boolean installationPending;
    static AppUpdates get(Context context) {
        return ((GlassesApplication)context.getApplicationContext()).updates();
    }
    private static final long INTERVAL=6*60*60*1000L;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners=new HashSet<>();
    private final Context context;
    private final SharedPreferences prefs;
    private final AppUpdateRepository repository;
    private AppIdentity installed;
    AppRelease offer;
    File verified;
    boolean busy;
    long prompted;
    String status="";
    AppUpdates(Context context) {
        this.context=context;prefs=context.getSharedPreferences("app_updates",Context.MODE_PRIVATE);
        repository=new AppUpdateRepository(new File(context.getCacheDir(),"app-updates"),
            new FirmwareRepository.HttpsTransport(AppRelease.REPOSITORY),(file,release)->AppIdentity.verify(context,file,release));
        try { installed=AppIdentity.installed(context);offer=repository.cached(installed,Build.VERSION.SDK_INT); }
        catch(IllegalArgumentException e) { status=context.getString(R.string.app_update_identity); }
    }
    void add(Listener listener) { listeners.add(listener);listener.changed(); }
    void remove(Listener listener) { listeners.remove(listener); }
    private void publish() { for(Listener listener:new ArrayList<>(listeners)) listener.changed(); }
    String installedLabel() { return installed==null ? "?" : installed.version.toString(); }
    boolean shouldPrompt() { return offer!=null && !busy && offer.code!=prompted && prefs.getLong("deferred",0)!=offer.code; }
    void defer(AppRelease release) { if(release!=null) prefs.edit().putLong("deferred",release.code).apply(); }
    void check(boolean manual) {
        if(busy || installed==null) return;
        if(manual) { prefs.edit().remove("deferred").apply();prompted=0; }
        long now=System.currentTimeMillis(),last=prefs.getLong("checked",0);
        if(!manual && now>=last && now-last<INTERVAL) { publish();return; }
        prefs.edit().putLong("checked",now).apply();busy=true;status=context.getString(R.string.app_update_checking);publish();
        worker.execute(()-> {
            try {
                AppRelease next=repository.refresh(installed,Build.VERSION.SDK_INT);
                main.post(()-> { offer=next;verified=null;busy=false;status=context.getString(next==null ? R.string.app_update_current : R.string.app_update_available,
                    next==null ? installedLabel() : next.version.toString());publish(); });
            } catch(Exception e) { main.post(()-> { busy=false;status=context.getString(R.string.app_update_check_failed);publish(); }); }
        });
    }
    void download() {
        if(busy || offer==null) return;
        AppRelease chosen=offer;busy=true;verified=null;status=context.getString(R.string.app_update_downloading);publish();
        worker.execute(()-> {
            try {
                File file=repository.download(chosen);
                main.post(()-> { verified=file;busy=false;status=context.getString(R.string.app_update_verified);publish(); });
            } catch(Exception e) { main.post(()-> { busy=false;status=context.getString(R.string.app_update_download_failed);publish(); }); }
        });
    }
}
