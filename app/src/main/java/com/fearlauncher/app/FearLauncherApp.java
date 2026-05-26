package com.fearlauncher.app;

import android.app.Application;
import android.util.Log;

public class FearLauncherApp extends Application {
    private static final String TAG = "FearLauncherApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "FearLauncher initialized");
        // Initialize any app-wide components here
    }
}
