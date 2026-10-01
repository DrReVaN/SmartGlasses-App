package com.test;
import android.app.Application;

/** Owns the update coordinator for this process without retaining an activity. */
public final class GlassesApplication extends Application {
    private AppUpdates updates;
    AppUpdates updates() { if(updates==null) updates=new AppUpdates(this);return updates; }
}
