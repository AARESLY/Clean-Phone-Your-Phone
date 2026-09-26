package com.organizer.downloads;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Intent serviceIntent = new Intent(context, DownloadObserverService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } else if ("com.organizer.downloads.ACTION_DISORGANIZE".equals(action)) {
            new Thread(() -> {
                DownloadClassifier.scanAndDisorganizeAll(context.getApplicationContext());
            }).start();
        } else if ("com.organizer.downloads.ACTION_ORGANIZE".equals(action)) {
            new Thread(() -> {
                DownloadClassifier.scanAndOrganizeAll(context.getApplicationContext());
            }).start();
        } else if ("com.organizer.downloads.ACTION_CLOSE_APPS".equals(action)) {
            String[] pkgs = intent.getStringArrayExtra("packages");
            if (pkgs == null) {
                String single = intent.getStringExtra("package");
                if (single != null) {
                    pkgs = new String[]{single};
                }
            }
            if (pkgs != null && pkgs.length > 0 && AppCacheCleanerAccessibilityService.isServiceRunning()) {
                java.util.List<String> list = java.util.Arrays.asList(pkgs);
                AppCacheCleanerAccessibilityService.getInstance().startBatchCloseApps(list);
            }
        }
    }
}
