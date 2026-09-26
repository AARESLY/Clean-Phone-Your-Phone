package com.organizer.downloads;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.FileObserver;
import android.os.IBinder;
import java.io.File;

public class DownloadObserverService extends Service {

    public static final String ACTION_STOP_SERVICE = "com.organizer.downloads.ACTION_STOP";
    public static final String ACTION_START_SERVICE = "com.organizer.downloads.ACTION_START";
    public static final String ACTION_UPDATE_WATCH_DIR = "com.organizer.downloads.ACTION_UPDATE_WATCH_DIR";

    private static final String CHANNEL_ID = "organizer_service_channel";
    private static final int NOTIF_ID = 1001;
    private static volatile boolean sIsRunning = false;
    private FileObserver mObserver;
    private File mCurrentWatchedDir;

    public static boolean isRunning() {
        return sIsRunning;
    }

    public static void setRunning(boolean running) {
        sIsRunning = running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sIsRunning = true;
        createNotificationChannel();
        startForeground(NOTIF_ID, buildForegroundNotification());
        startWatching();
    }

    private synchronized void startWatching() {
        if (mObserver != null) {
            mObserver.stopWatching();
            mObserver = null;
        }

        final File watchDir = DownloadClassifier.getWatchedDir(getApplicationContext());
        mCurrentWatchedDir = watchDir;
        if (watchDir == null || !watchDir.exists()) {
            return;
        }

        // Mask: FileObserver.CLOSE_WRITE (8) | FileObserver.MOVED_TO (128)
        int mask = FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO;
        mObserver = new FileObserver(watchDir.getAbsolutePath(), mask) {
            @Override
            public void onEvent(int event, String path) {
                if (path == null) return;
                if (DownloadClassifier.isDisorganizing()) return;
                // Only watch direct children of watchDir! Ignore events in subdirectories
                if (path.contains(File.separator) || path.contains("/")) return;

                File target = new File(watchDir, path);
                new Thread(() -> {
                    DownloadClassifier.organizeFile(getApplicationContext(), target);
                }).start();
            }
        };
        mObserver.startWatching();
        DownloadClassifier.logEvent("Watching: " + watchDir.getName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
            sIsRunning = false;
            if (mObserver != null) {
                mObserver.stopWatching();
                mObserver = null;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_UPDATE_WATCH_DIR.equals(intent.getAction())) {
            startWatching();
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.notify(NOTIF_ID, buildForegroundNotification());
            }
        } else if (mObserver == null) {
            startWatching();
        }
        sIsRunning = true;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        sIsRunning = false;
        if (mObserver != null) {
            mObserver.stopWatching();
            mObserver = null;
        }
        DownloadClassifier.logEvent("Inotify service paused");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Auto Organizer Service",
                NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Monitors folder for automatic file categorization");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        File dir = (mCurrentWatchedDir != null) ? mCurrentWatchedDir : DownloadClassifier.getWatchedDir(this);
        String dirName = (dir != null) ? dir.getName() : "Downloads";

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("Auto-Organizer: " + dirName)
               .setContentText("Active • 0% Battery (Kernel inotify)")
               .setSmallIcon(android.R.drawable.stat_notify_sync)
               .setContentIntent(pi)
               .setOngoing(true);

        return builder.build();
    }
}