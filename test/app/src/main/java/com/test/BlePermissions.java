package com.test;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
public final class BlePermissions {
    private BlePermissions() {}
    // These permission names are inlined strings, selected for the supplied OS version.
    @SuppressLint("InlinedApi")
    public static String[] scanPermissions(int sdk) {
        if (sdk >= 31) return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        if (sdk >= 23) return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        return new String[0];
    }
    public static boolean canConnect(Context context) {
        return Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean canScan(Context context) {
        if (Build.VERSION.SDK_INT < 23) return true;
        for (String permission : scanPermissions(Build.VERSION.SDK_INT)) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }
}
