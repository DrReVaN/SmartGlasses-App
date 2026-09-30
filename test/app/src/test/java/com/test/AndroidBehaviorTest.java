package com.test;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35})
public class AndroidBehaviorTest {
    @Test public void formattedNotificationTextIsForwardable() {
        Notification n=new Notification(); n.extras=new Bundle();
        n.extras.putCharSequence(Notification.EXTRA_TITLE,new SpannableString("Alice"));
        n.extras.putCharSequence(Notification.EXTRA_TEXT,new SpannableString("Hallo"));
        assertEquals("Alice: Hallo",NotificationText.read(n));
    }
    @Test public void expandedTextAndInboxFallbackAreRead() {
        Notification n=new Notification(); n.extras=new Bundle(); n.extras.putCharSequence(Notification.EXTRA_TEXT,"Kurz");
        n.extras.putCharSequence(Notification.EXTRA_BIG_TEXT,"Lang"); assertEquals("Lang",NotificationText.read(n));
        n.extras.clear(); n.extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES,new CharSequence[]{"Alt","Neu"});
        assertEquals("Neu",NotificationText.read(n));
    }
    @Test public void messagingStyleUsesLatestSenderAndText() {
        Context context=RuntimeEnvironment.getApplication();
        Notification.MessagingStyle style=new Notification.MessagingStyle("Ich").addMessage("Alt",1,"Bob").addMessage("Neu",2,"Alice");
        Notification n=new Notification.Builder(context,"test").setSmallIcon(android.R.drawable.ic_dialog_info).setStyle(style).build();
        assertEquals("Alice: Neu",NotificationText.read(n));
    }
    @Test public void permissionsAreVersionSpecific() {
        assertEquals(0,BlePermissions.scanPermissions(21).length);
        assertArrayEquals(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},BlePermissions.scanPermissions(30));
        assertArrayEquals(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},BlePermissions.scanPermissions(31));
    }
    @Test public void missingPermissionDoesNotCrashLauncher() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.ACCESS_FINE_LOCATION);
        try (org.robolectric.android.controller.ActivityController<DeviceScanActivity> controller=Robolectric.buildActivity(DeviceScanActivity.class).setup()) {
            assertFalse(controller.get().isFinishing());
            controller.pause().stop().destroy();
        }
    }
    @Test public void serviceUnbindDoesNotCloseOrDestroyIt() {
        org.robolectric.android.controller.ServiceController<BluetoothLeService> controller=Robolectric.buildService(BluetoothLeService.class).create();
        try {
            BluetoothLeService service=controller.get();
            assertNotNull(service.onBind(new Intent())); service.onUnbind(new Intent());
            assertEquals(BluetoothLeService.State.DISCONNECTED,service.state());
        } finally { controller.destroy(); }
    }
}
