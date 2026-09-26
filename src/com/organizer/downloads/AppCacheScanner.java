package com.organizer.downloads;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.storage.StorageManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AppCacheScanner {

    public interface ScanCallback {
        void onProgress(int current, int total, String currentAppName);
        void onComplete(List<AppCacheItem> items, long totalCacheBytes);
    }

    public static void scanApps(Context context, int mode, ScanCallback callback) {
        // mode: 0 = User only, 1 = System only, 2 = All apps
        new Thread(() -> {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<ApplicationInfo> filtered = new ArrayList<>();
            String selfPkg = context.getPackageName();

            for (ApplicationInfo ai : installed) {
                if (selfPkg.equals(ai.packageName)) continue;
                boolean isSys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (mode == 0 && !isSys) {
                    filtered.add(ai);
                } else if (mode == 1 && isSys) {
                    filtered.add(ai);
                } else if (mode == 2) {
                    filtered.add(ai);
                }
            }

            int total = filtered.size();
            List<AppCacheItem> resultList = new ArrayList<>();
            long totalBytes = 0;

            StorageStatsManager ssm = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    ssm = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
                } catch (Exception ignored) {}
            }

            UUID storageUuid = StorageManager.UUID_DEFAULT;
            android.os.UserHandle userHandle = Process.myUserHandle();

            for (int i = 0; i < total; i++) {
                ApplicationInfo ai = filtered.get(i);
                String appName = ai.packageName;
                Drawable icon = null;
                try {
                    appName = pm.getApplicationLabel(ai).toString();
                    icon = pm.getApplicationIcon(ai);
                } catch (Exception ignored) {}

                if (callback != null) {
                    callback.onProgress(i + 1, total, appName);
                }

                long cacheBytes = 0;
                if (ssm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        StorageStats stats = ssm.queryStatsForPackage(storageUuid, ai.packageName, userHandle);
                        cacheBytes = stats.getCacheBytes();
                    } catch (Exception ignored) {}
                }

                // If cacheBytes is 0 or permission denied, inspect external storage package cache
                if (cacheBytes <= 0) {
                    cacheBytes = getExternalPackageCacheSize(ai.packageName);
                }

                boolean isSys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

                AppCacheItem item = new AppCacheItem(ai.packageName, appName, icon, cacheBytes, isSys);
                resultList.add(item);
                totalBytes += cacheBytes;
            }

            // Sort by cache size descending (largest cache at top)
            Collections.sort(resultList, (a, b) -> Long.compare(b.cacheBytes, a.cacheBytes));

            if (callback != null) {
                callback.onComplete(resultList, totalBytes);
            }
        }).start();
    }

    private static long getExternalPackageCacheSize(String pkg) {
        long total = 0;
        String[] paths = new String[]{
            "/sdcard/Android/data/" + pkg + "/cache",
            "/storage/emulated/0/Android/data/" + pkg + "/cache"
        };
        for (String p : paths) {
            File f = new File(p);
            if (f.exists() && f.isDirectory()) {
                total += calculateDirSize(f);
                break;
            }
        }
        return total;
    }

    private static long calculateDirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                size += calculateDirSize(f);
            } else {
                size += f.length();
            }
        }
        return size;
    }
}
