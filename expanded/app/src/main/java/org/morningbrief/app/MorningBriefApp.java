package org.morningbrief.app;

import android.app.Application;

public class MorningBriefApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        if (SettingsStore.isSetup(this)) Scheduler.ensureScheduled(this);
    }
}
