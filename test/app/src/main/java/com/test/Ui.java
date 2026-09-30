package com.test;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.view.WindowInsets;
import android.graphics.Insets;
final class Ui {
    private Ui() {}
    static LinearLayout page(Activity activity) {
        LinearLayout root=new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL);
        int p=(int)(16*activity.getResources().getDisplayMetrics().density); root.setPadding(p,p,p,p);
        if (Build.VERSION.SDK_INT>=30) root.setOnApplyWindowInsetsListener((view,insets) -> {
            Insets system=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            view.setPadding(p+system.left,p+system.top,p+system.right,p+system.bottom); return insets;
        });
        activity.setContentView(root); return root;
    }
    static TextView text(Activity activity,LinearLayout root,int text) {
        TextView view=new TextView(activity); view.setText(text); view.setPadding(0,8,0,8); root.addView(view); return view;
    }
    static Button button(Activity activity,LinearLayout root,int text,View.OnClickListener click) {
        Button button=new Button(activity); button.setText(text); button.setOnClickListener(click); root.addView(button); return button;
    }
    static void appSettings(Activity activity) {
        activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName())));
    }
    static void notificationSettings(Activity activity) {
        Intent intent=new Intent(Build.VERSION.SDK_INT>=22 ? Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS : Settings.ACTION_SECURITY_SETTINGS);
        try { activity.startActivity(intent); }
        catch (android.content.ActivityNotFoundException e) { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }
}
