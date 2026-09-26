package com.organizer.downloads;

import android.graphics.drawable.Drawable;

public class AppInfoItem {
    public String appName;
    public String packageName;
    public Drawable icon;
    public boolean isSystem;
    public boolean isSelected;

    public AppInfoItem(String appName, String packageName, Drawable icon, boolean isSystem) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSystem = isSystem;
        this.isSelected = false;
    }
}
