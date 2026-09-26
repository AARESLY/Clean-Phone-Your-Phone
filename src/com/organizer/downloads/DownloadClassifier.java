package com.organizer.downloads;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.Environment;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

public class DownloadClassifier {

    private static final String PREFS_NAME = "organizer_prefs";
    private static final String KEY_WATCH_DIR = "watch_directory";
    private static final String KEY_IS_STATS_RESET = "stats_is_reset";
    private static final String KEY_TRACKED_ORGANIZED = "tracked_organized_files";
    private static volatile boolean sIsDisorganizing = false;

    public static boolean isDisorganizing() {
        return sIsDisorganizing;
    }

    public static void setDisorganizing(boolean disorganizing) {
        sIsDisorganizing = disorganizing;
    }

    public static synchronized void trackOrganizedFile(Context context, String relPath) {
        if (context == null || relPath == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Set<String> set = new java.util.HashSet<>(prefs.getStringSet(KEY_TRACKED_ORGANIZED, new java.util.HashSet<>()));
        set.add(relPath);
        prefs.edit().putStringSet(KEY_TRACKED_ORGANIZED, set).apply();
    }

    public static synchronized void untrackOrganizedFile(Context context, String relPath) {
        if (context == null || relPath == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Set<String> set = new java.util.HashSet<>(prefs.getStringSet(KEY_TRACKED_ORGANIZED, new java.util.HashSet<>()));
        set.remove(relPath);
        prefs.edit().putStringSet(KEY_TRACKED_ORGANIZED, set).apply();
    }

    public static synchronized java.util.Set<String> getTrackedOrganizedFiles(Context context) {
        if (context == null) return new java.util.HashSet<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new java.util.HashSet<>(prefs.getStringSet(KEY_TRACKED_ORGANIZED, new java.util.HashSet<>()));
    }

    public static synchronized void clearTrackedOrganizedFiles(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_TRACKED_ORGANIZED).apply();
    }

    public interface OnLogListener {
        void onLog(String message);
    }

    private static final LinkedList<String> sRecentLogs = new LinkedList<>();
    private static OnLogListener sLogListener;

    public static synchronized void setLogListener(OnLogListener listener) {
        sLogListener = listener;
    }

    public static synchronized void logEvent(String msg) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String entry = "[" + timestamp + "] " + msg;
        sRecentLogs.addFirst(entry);
        while (sRecentLogs.size() > 8) {
            sRecentLogs.removeLast();
        }
        if (sLogListener != null) {
            sLogListener.onLog(entry);
        }
    }

    public static synchronized void clearLogsOnly() {
        sRecentLogs.clear();
        if (sLogListener != null) {
            sLogListener.onLog("");
        }
    }

    public static synchronized String getRecentLogsJoined() {
        if (sRecentLogs.isEmpty()) {
            return "• Log cleared.\n• Waiting for file events...";
        }
        StringBuilder sb = new StringBuilder();
        for (String l : sRecentLogs) {
            sb.append("• ").append(l).append("\n");
        }
        return sb.toString().trim();
    }

    public static File getWatchedDir(Context context) {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String customPath = prefs.getString(KEY_WATCH_DIR, null);
            if (customPath != null) {
                File customFile = new File(customPath);
                if (customFile.exists() && customFile.isDirectory()) {
                    return customFile;
                }
            }
        }
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public static void setWatchedDir(Context context, String path) {
        if (context != null && path != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_WATCH_DIR, path).putBoolean(KEY_IS_STATS_RESET, false).apply();
            logEvent("Target folder set to: " + path);
        }
    }

    public static void setStatsResetState(Context context, boolean reset) {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_IS_STATS_RESET, reset).apply();
        }
    }

    public static boolean isStatsReset(Context context) {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getBoolean(KEY_IS_STATS_RESET, false);
        }
        return false;
    }

    public static boolean organizeFile(Context context, File file) {
        return organizeFile(context, file, true);
    }

    public static boolean organizeFile(Context context, File file, boolean checkStabilization) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return false;
        }

        if (sIsDisorganizing) {
            return false;
        }

        File watchDir = getWatchedDir(context);
        if (watchDir == null || !watchDir.exists()) return false;

        // STRICT REQUIREMENT: Only organize files that are DIRECT children of watchDir!
        // Never touch subdirectories, files inside subdirectories, or files in other folders.
        File parentDir = file.getParentFile();
        if (parentDir == null || !parentDir.getAbsolutePath().equals(watchDir.getAbsolutePath())) {
            return false;
        }

        String name = file.getName();
        if (name.startsWith(".")) {
            return false;
        }

        String lower = name.toLowerCase(Locale.ROOT);
        // Ignore incomplete downloads
        if (lower.endsWith(".crdownload") || lower.endsWith(".download") ||
            lower.endsWith(".part") || lower.endsWith(".tmp")) {
            return false;
        }

        // Wait up to 2 seconds for write completion / size stabilization only if requested
        if (checkStabilization) {
            long prevSize = -1;
            for (int i = 0; i < 4; i++) {
                long curSize = file.length();
                if (curSize == prevSize && curSize > 0) {
                    break;
                }
                prevSize = curSize;
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {}
            }
        }

        if (!file.exists()) return false;

        String category = getCategoryForFile(lower);
        if (category == null) {
            return false;
        }

        File targetDir = new File(watchDir, category);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File destination = new File(targetDir, name);
        if (destination.exists()) {
            String base = name;
            String ext = "";
            int dotIdx = name.lastIndexOf('.');
            if (dotIdx > 0) {
                base = name.substring(0, dotIdx);
                ext = name.substring(dotIdx);
            }
            int counter = 1;
            while (destination.exists()) {
                destination = new File(targetDir, base + " (" + counter + ")" + ext);
                counter++;
            }
        }

        boolean success = file.renameTo(destination);
        if (success) {
            trackOrganizedFile(context, category + "/" + destination.getName());
            setStatsResetState(context, false);
            logEvent("Sorted " + name + " -> " + category + "/");
            if (context != null) {
                MediaScannerConnection.scanFile(context,
                    new String[]{ destination.getAbsolutePath() }, null, null);
            }
            return true;
        }
        return false;
    }

    public static String getCategoryForFile(String lower) {
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".heic") ||
            lower.endsWith(".bmp") || lower.endsWith(".svg") || lower.endsWith(".raw") ||
            lower.endsWith(".tiff")) {
            return "Images";
        } else if (lower.endsWith(".pdf") || lower.endsWith(".epub") || lower.endsWith(".mobi") ||
                   lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".xls") ||
                   lower.endsWith(".xlsx") || lower.endsWith(".ppt") || lower.endsWith(".pptx") ||
                   lower.endsWith(".csv") || lower.endsWith(".odt") || lower.endsWith(".rtf")) {
            return "Documents";
        } else if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") ||
                   lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".xz") ||
                   lower.endsWith(".bz2") || lower.endsWith(".iso")) {
            return "Archives";
        } else if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") ||
                   lower.endsWith(".flac") || lower.endsWith(".ogg") || lower.endsWith(".aac") ||
                   lower.endsWith(".opus") || lower.endsWith(".wma")) {
            return "Audio";
        } else if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") ||
                   lower.endsWith(".webm") || lower.endsWith(".avi") || lower.endsWith(".3gp") ||
                   lower.endsWith(".flv") || lower.endsWith(".wmv")) {
            return "Videos";
        } else if (lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".xml") ||
                   lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".js") ||
                   lower.endsWith(".py") || lower.endsWith(".java") || lower.endsWith(".kt") ||
                   lower.endsWith(".sh") || lower.endsWith(".plist") || lower.endsWith(".vtt") ||
                   lower.endsWith(".log") || lower.endsWith(".md") || lower.endsWith(".data")) {
            return "Code_and_Notes";
        }
        return null;
    }

    public static int scanAndOrganizeAll(Context context) {
        File watchDir = getWatchedDir(context);
        int count = 0;
        if (watchDir != null && watchDir.exists() && watchDir.isDirectory()) {
            File[] files = watchDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && !f.isDirectory()) {
                        if (organizeFile(context, f, false)) {
                            count++;
                        }
                    }
                }
            }
        }
        if (count > 0) {
            setStatsResetState(context, false);
            logEvent("Batch scan: organized " + count + " file(s)");
        } else {
            logEvent("Batch scan: all files up-to-date");
        }
        return count;
    }

    public static int countFilesInFolder(Context context, String subfolder) {
        if (isStatsReset(context)) {
            return 0;
        }
        File watchDir = getWatchedDir(context);
        if (watchDir == null || !watchDir.exists()) return 0;
        File dir = new File(watchDir, subfolder);
        if (!dir.exists() || !dir.isDirectory()) return 0;
        File[] list = dir.listFiles();
        if (list == null) return 0;
        java.util.Set<String> tracked = getTrackedOrganizedFiles(context);
        int count = 0;
        for (File f : list) {
            if (f.isFile() && !f.getName().startsWith(".")) {
                String relPath = subfolder + "/" + f.getName();
                if (tracked.isEmpty() || tracked.contains(relPath)) {
                    count++;
                }
            }
        }
        return count;
    }

    public static final String[] CATEGORY_FOLDERS = new String[]{
        "Images", "Documents", "Archives", "Audio", "Videos", "Code_and_Notes"
    };

    public static int scanAndDisorganizeAll(Context context) {
        setDisorganizing(true);
        File watchDir = getWatchedDir(context);
        int count = 0;
        try {
            if (watchDir != null && watchDir.exists() && watchDir.isDirectory()) {
                java.util.List<String> scannedPaths = new java.util.ArrayList<>();
                java.util.Set<String> tracked = getTrackedOrganizedFiles(context);

                for (String category : CATEGORY_FOLDERS) {
                    File catDir = new File(watchDir, category);
                    // Strict: catDir must be a direct child of watchDir and exist
                    if (catDir.exists() && catDir.isDirectory() && catDir.getParentFile().equals(watchDir)) {
                        File[] files = catDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.isFile() && !f.getName().startsWith(".")) {
                                    String relPath = category + "/" + f.getName();
                                    // Disorganize ONLY files that were organized by the organizer
                                    // (or all files in the 6 exact organizer category folders if tracked set is uninitialized)
                                    if (tracked.isEmpty() || tracked.contains(relPath)) {
                                        String name = f.getName();
                                        File dest = new File(watchDir, name);
                                        if (dest.exists()) {
                                            String base = name;
                                            String ext = "";
                                            int dotIdx = name.lastIndexOf('.');
                                            if (dotIdx > 0) {
                                                base = name.substring(0, dotIdx);
                                                ext = name.substring(dotIdx);
                                            }
                                            int counter = 1;
                                            while (dest.exists()) {
                                                dest = new File(watchDir, base + " (" + counter + ")" + ext);
                                                counter++;
                                            }
                                        }
                                        if (f.renameTo(dest)) {
                                            count++;
                                            scannedPaths.add(dest.getAbsolutePath());
                                            untrackOrganizedFile(context, relPath);
                                        }
                                    }
                                }
                            }
                        }
                        // Clean up empty category directory after moving files out
                        try {
                            String[] remaining = catDir.list();
                            if (remaining == null || remaining.length == 0) {
                                catDir.delete();
                            }
                        } catch (Exception ignored) {}
                    }
                }

                if (context != null && !scannedPaths.isEmpty()) {
                    MediaScannerConnection.scanFile(context,
                        scannedPaths.toArray(new String[0]), null, null);
                }
            }
        } finally {
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {}
                setDisorganizing(false);
            }).start();
        }

        if (count > 0) {
            setStatsResetState(context, false);
            logEvent("Disorganizer: restored " + count + " file(s) back to " + (watchDir != null ? watchDir.getName() : "root"));
        } else {
            logEvent("Disorganizer: no categorized files to disorganize");
        }
        return count;
    }

}
