package com.organizer.downloads;

import android.graphics.drawable.Drawable;

public class AppCacheItem {
    public final String packageName;
    public final String appName;
    public final Drawable icon;
    public final long cacheBytes;
    public final boolean isSystemApp;
    public boolean isSelected = true;

    public AppCacheItem(String packageName, String appName, Drawable icon, long cacheBytes, boolean isSystemApp) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.cacheBytes = cacheBytes;
        this.isSystemApp = isSystemApp;
    }
}
