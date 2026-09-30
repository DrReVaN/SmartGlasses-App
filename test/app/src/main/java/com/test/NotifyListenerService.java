package com.test;
import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.HashSet;
import java.util.Set;
/** System-bound listener; text delivery remains inside this process. */
public class NotifyListenerService extends NotificationListenerService {
    public static volatile boolean connected;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final NotificationDeduplicator dedup=new NotificationDeduplicator();
    @Override public void onListenerConnected() { connected=true; main.post(BluetoothLeService::listenerStateChanged); }
    @Override public void onListenerDisconnected() { connected=false; main.post(BluetoothLeService::listenerStateChanged); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { main.post(() -> forward(sbn)); }
    private void forward(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName().equals(getPackageName())) return;
        SharedPreferences prefs=getSharedPreferences("glasses",MODE_PRIVATE);
        Set<String> known=new HashSet<>(prefs.getStringSet("known_apps",new HashSet<>()));
        if (known.size()<64 && known.add(sbn.getPackageName())) prefs.edit().putStringSet("known_apps",known).apply();
        if (!prefs.getBoolean("forward",true) || prefs.getStringSet("blocked_apps",new HashSet<>()).contains(sbn.getPackageName())) return;
        Notification notification=sbn.getNotification();
        if ((notification.flags & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_ONGOING_EVENT | Notification.FLAG_LOCAL_ONLY)) != 0) return;
        String text=NotificationText.read(notification);
        if (text != null && dedup.accept(sbn.getKey(),text,SystemClock.elapsedRealtime())) BluetoothLeService.deliverNotification(text);
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) main.post(() -> dedup.remove(sbn.getKey()));
    }
    @Override public void onDestroy() {
        connected=false; main.removeCallbacksAndMessages(null); dedup.clear();
        BluetoothLeService.listenerStateChanged(); super.onDestroy();
    }
}
