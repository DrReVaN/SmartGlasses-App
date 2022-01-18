package com.test;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;

/**
 * Service for managing notifications and sending them via a intent Broadcast
 * to the DeviceControlActivity.
 */

public class NotifyListenerService extends NotificationListenerService {

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){

        Notification notification = sbn.getNotification();
        Bundle bundle = notification.extras;

        if (bundle.getString(NotificationCompat.EXTRA_TITLE) != null && bundle.getString(NotificationCompat.EXTRA_TEXT) != null) {
            String from = bundle.getString(NotificationCompat.EXTRA_TITLE);
            String message = bundle.getString(NotificationCompat.EXTRA_TEXT);

            String notifyInfoText;

            notifyInfoText = from + ": " + message;

            Intent notifyIntent = new Intent("com.test.notifyListener");
            notifyIntent.putExtra("Notification Info", notifyInfoText);
            sendBroadcast(notifyIntent);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){

    }

}