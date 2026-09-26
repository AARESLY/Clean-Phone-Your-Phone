package com.organizer.downloads;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_FOLDER = 2001;

    private TextView mTvStatusBadge;
    private TextView mTvServiceSubtitle;
    private Switch mSwitchService;
    private boolean mIsProgrammaticSwitchChange = false;
    private TextView mTvWatchPath;
    private Button mBtnChangeFolder;
    private TextView mTvRamStats;
    private ProgressBar mPbRam;
    private TextView mTvStorageStats;
    private ProgressBar mPbStorage;
    private TextView mTvTotalFiles;
    private TextView mTvCountImages;
    private TextView mTvCountDocs;
    private TextView mTvCountArchives;
    private TextView mTvCountCode;
    private TextView mTvCountAudio;
    private TextView mTvCountVideos;
    private TextView mTvLog;
    private Button mBtnAppCloser;
    private Button mBtnCleanCache;
    private Button mBtnOrganize;
    private Button mBtnDisorganize;
    private Button mBtnResetStats;
    private Button mBtnResetLogs;
    private TextView mBtnHelp;
    private TextView mBtnThemeToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ThemeHelper.applyWindowAppearance(this);

        try {
            bindViews();
            setupListeners();
            checkPermissions();
            ensureServiceStarted();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceUIState();
        updateWatchPathUI();
        refreshCategoryCounts();
        refreshSystemResources();
        mTvLog.setText(DownloadClassifier.getRecentLogsJoined());

        DownloadClassifier.setLogListener(msg -> runOnUiThread(() -> {
            mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
            refreshCategoryCounts();
            refreshSystemResources();
        }));
    }

    @Override
    protected void onPause() {
        super.onPause();
        DownloadClassifier.setLogListener(null);
    }

    private void bindViews() {
        mTvStatusBadge = findViewById(R.id.tv_status_badge);
        mTvServiceSubtitle = findViewById(R.id.tv_service_subtitle);
        mSwitchService = findViewById(R.id.switch_service);
        mTvWatchPath = findViewById(R.id.tv_watch_path);
        mBtnChangeFolder = findViewById(R.id.btn_change_folder);
        mTvRamStats = findViewById(R.id.tv_ram_stats);
        mPbRam = findViewById(R.id.pb_ram);
        mTvStorageStats = findViewById(R.id.tv_storage_stats);
        mPbStorage = findViewById(R.id.pb_storage);
        mTvTotalFiles = findViewById(R.id.tv_total_files);
        mTvCountImages = findViewById(R.id.tv_count_images);
        mTvCountDocs = findViewById(R.id.tv_count_docs);
        mTvCountArchives = findViewById(R.id.tv_count_archives);
        mTvCountCode = findViewById(R.id.tv_count_code);
        mTvCountAudio = findViewById(R.id.tv_count_audio);
        mTvCountVideos = findViewById(R.id.tv_count_videos);
        mTvLog = findViewById(R.id.tv_log);
        mBtnAppCloser = findViewById(R.id.btn_app_closer);
        mBtnCleanCache = findViewById(R.id.btn_clean_cache);
        mBtnOrganize = findViewById(R.id.btn_organize);
        mBtnDisorganize = findViewById(R.id.btn_disorganize);
        mBtnResetStats = findViewById(R.id.btn_reset_stats);
        mBtnResetLogs = findViewById(R.id.btn_reset_logs);
        mBtnHelp = findViewById(R.id.btn_help);
        mBtnThemeToggle = findViewById(R.id.btn_theme_toggle);
    }

    private void refreshSystemResources() {
        new Thread(() -> {
            // RAM calculation
            ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            actManager.getMemoryInfo(memInfo);
            long totalMem = memInfo.totalMem;
            long availMem = memInfo.availMem;
            long usedMem = totalMem - availMem;
            int ramPercent = totalMem > 0 ? (int) ((usedMem * 100) / totalMem) : 0;
            String ramStr = CacheCleaner.formatSize(usedMem) + " / " + CacheCleaner.formatSize(totalMem) + " (" + ramPercent + "%)";

            // Storage calculation
            File dataPath = Environment.getDataDirectory();
            StatFs stat = new StatFs(dataPath.getPath());
            long totalStorage = stat.getTotalBytes();
            long freeStorage = stat.getAvailableBytes();
            long usedStorage = totalStorage - freeStorage;
            int storagePercent = totalStorage > 0 ? (int) ((usedStorage * 100) / totalStorage) : 0;
            String storageStr = CacheCleaner.formatSize(usedStorage) + " / " + CacheCleaner.formatSize(totalStorage) + " (" + storagePercent + "%)";

            runOnUiThread(() -> {
                mTvRamStats.setText(ramStr);
                mPbRam.setProgress(ramPercent);
                mTvStorageStats.setText(storageStr);
                mPbStorage.setProgress(storagePercent);
            });
        }).start();
    }

    private void updateWatchPathUI() {
        File dir = DownloadClassifier.getWatchedDir(this);
        if (dir != null) {
            mTvWatchPath.setText(dir.getAbsolutePath());
        }
    }

    private void setupListeners() {
        if (mBtnThemeToggle != null) {
            boolean isDark = ThemeHelper.isDarkMode(this);
            mBtnThemeToggle.setText(isDark ? "☀️ Light" : "🌙 Dark");
            mBtnThemeToggle.setOnClickListener(v -> {
                ThemeHelper.toggleTheme(this);
                recreate();
            });
        }

        mSwitchService.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mIsProgrammaticSwitchChange) return;

            if (isChecked) {
                ensureServiceStarted();
            } else {
                stopOrganizerService();
            }
            applyServiceVisualState(isChecked);
        });

        View rowServiceToggle = findViewById(R.id.row_service_toggle);
        if (rowServiceToggle != null) {
            rowServiceToggle.setOnClickListener(v -> mSwitchService.toggle());
        }

        mBtnChangeFolder.setOnClickListener(v -> showFolderSelectionDialog());

        mBtnAppCloser.setOnClickListener(v -> {
            mBtnAppCloser.setEnabled(false);
            mBtnAppCloser.setText("Checking Root...");
            RootHelper.checkRootAccess(isRooted -> runOnUiThread(() -> {
                if (isRooted) {
                    mBtnAppCloser.setText("🛑 Closing Apps (Root)...");
                    RootHelper.executeRootKillApps(getApplicationContext(), null, (appsKilled, success) -> runOnUiThread(() -> {
                        mBtnAppCloser.setEnabled(true);
                        mBtnAppCloser.setText("🛑 App Closer (Kill Apps & Free RAM)");
                        refreshSystemResources();
                        DownloadClassifier.logEvent("App Closer (Root): force-stopped " + appsKilled + " background apps in 1 command");
                        mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
                        Toast.makeText(MainActivity.this, "🛑 Root: Force-stopped " + appsKilled + " apps & freed RAM!", Toast.LENGTH_SHORT).show();
                    }));
                } else {
                    mBtnAppCloser.setEnabled(true);
                    mBtnAppCloser.setText("🛑 App Closer (Kill Apps & Free RAM)");
                    showAppCloserDialog();
                }
            }));
        });

        mBtnCleanCache.setOnClickListener(v -> {
            mBtnCleanCache.setEnabled(false);
            mBtnCleanCache.setText("Checking Root...");
            RootHelper.checkRootAccess(isRooted -> runOnUiThread(() -> {
                if (isRooted) {
                    mBtnCleanCache.setText("⚡ Wiping Caches (Root)...");
                    RootHelper.executeRootCacheClean(getApplicationContext(), (bytesCleaned, success) -> runOnUiThread(() -> {
                        mBtnCleanCache.setEnabled(true);
                        mBtnCleanCache.setText("🧹 Clean Phone (Deep Cache Cleaner)");
                        refreshSystemResources();
                        String formatted = CacheCleaner.formatSize(bytesCleaned);
                        DownloadClassifier.logEvent("Cache Cleaner (Root): cleared " + formatted + " in 1 command");
                        mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
                        Toast.makeText(MainActivity.this, "⚡ Root: Cleared " + formatted + " system & app cache!", Toast.LENGTH_SHORT).show();
                    }));
                } else {
                    mBtnCleanCache.setEnabled(true);
                    mBtnCleanCache.setText("🧹 Clean Phone (Deep Cache Cleaner)");
                    startCacheScanFlow(2);
                }
            }));
        });
        mBtnCleanCache.setOnLongClickListener(v -> {
            showCacheCleanerOptionsDialog();
            return true;
        });

        mBtnOrganize.setOnClickListener(v -> {
            mBtnOrganize.setEnabled(false);
            mBtnOrganize.setText("Organizing...");
            new Thread(() -> {
                DownloadClassifier.setStatsResetState(getApplicationContext(), false);
                int organizedCount = DownloadClassifier.scanAndOrganizeAll(getApplicationContext());
                runOnUiThread(() -> {
                    mBtnOrganize.setEnabled(true);
                    mBtnOrganize.setText("🗂️ Scan & Organize Now");
                    refreshCategoryCounts();
                    mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
                    Toast.makeText(MainActivity.this,
                        "Done! Organized " + organizedCount + " new items.",
                        Toast.LENGTH_SHORT).show();
                });
            }).start();
        });

        mBtnDisorganize.setOnClickListener(v -> {
            mBtnDisorganize.setEnabled(false);
            mBtnDisorganize.setText("Disorganizing...");
            if (DownloadObserverService.isRunning()) {
                stopOrganizerService();
                updateServiceUIState();
            }
            new Thread(() -> {
                DownloadClassifier.setStatsResetState(getApplicationContext(), false);
                int disCount = DownloadClassifier.scanAndDisorganizeAll(getApplicationContext());
                runOnUiThread(() -> {
                    mBtnDisorganize.setEnabled(true);
                    mBtnDisorganize.setText("📂 Disorganize Files (Restore to Root)");
                    refreshCategoryCounts();
                    mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
                    Toast.makeText(MainActivity.this,
                        "Restored " + disCount + " file(s) back to folder root!",
                        Toast.LENGTH_SHORT).show();
                });
            }).start();
        });

        mBtnResetStats.setOnClickListener(v -> {
            DownloadClassifier.setStatsResetState(getApplicationContext(), true);
            mTvCountImages.setText("0");
            mTvCountDocs.setText("0");
            mTvCountArchives.setText("0");
            mTvCountCode.setText("0");
            mTvCountAudio.setText("0");
            mTvCountVideos.setText("0");
            mTvTotalFiles.setText("0 files sorted");
            Toast.makeText(this, "File statistics reset to 0!", Toast.LENGTH_SHORT).show();
        });

        mBtnResetLogs.setOnClickListener(v -> {
            DownloadClassifier.clearLogsOnly();
            mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
            Toast.makeText(this, "Activity logs cleared!", Toast.LENGTH_SHORT).show();
        });

        mBtnHelp.setOnClickListener(v -> showHelpDialog());
    }

    private void showCacheCleanerOptionsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_cache_cleaner, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView btnClose = dialogView.findViewById(R.id.btn_cache_dialog_close);
        Button optBatchAll = dialogView.findViewById(R.id.opt_batch_all);
        Button optBatchUser = dialogView.findViewById(R.id.opt_batch_user);
        Button optBatchSystem = dialogView.findViewById(R.id.opt_batch_system);
        Button optFastWipe = dialogView.findViewById(R.id.opt_fast_wipe);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (optBatchAll != null) {
            optBatchAll.setOnClickListener(v -> {
                dialog.dismiss();
                startCacheScanFlow(2);
            });
        }

        if (optBatchUser != null) {
            optBatchUser.setOnClickListener(v -> {
                dialog.dismiss();
                startCacheScanFlow(0);
            });
        }

        if (optBatchSystem != null) {
            optBatchSystem.setOnClickListener(v -> {
                dialog.dismiss();
                startCacheScanFlow(1);
            });
        }

        if (optFastWipe != null) {
            optFastWipe.setOnClickListener(v -> {
                dialog.dismiss();
                executeFastSystemCacheClean();
            });
        }

        dialog.show();
    }

    private void executeFastSystemCacheClean() {
        mBtnCleanCache.setEnabled(false);
        mBtnCleanCache.setText("Cleaning Caches...");
        new Thread(() -> {
            CacheCleaner.CleanResult res = CacheCleaner.cleanAllCaches(getApplicationContext());
            String formatted = CacheCleaner.formatSize(res.bytesCleaned);
            String extraMsg = res.rootUsed ? " (Root Purged)" : "";
            DownloadClassifier.logEvent("Cache Cleaner: cleared " + formatted + " (" + res.filesDeleted + " items)" + extraMsg);
            runOnUiThread(() -> {
                mBtnCleanCache.setEnabled(true);
                mBtnCleanCache.setText("🧹 Clean Phone (Deep Cache Cleaner)");
                refreshSystemResources();
                mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
                Toast.makeText(MainActivity.this,
                    "Cleaned " + formatted + " of system & app caches!" + extraMsg,
                    Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void startCacheScanFlow(int mode) {
        View scanView = LayoutInflater.from(this).inflate(R.layout.dialog_cache_scanner, null);
        AlertDialog scanDialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setView(scanView)
                .setCancelable(false)
                .create();

        if (scanDialog.getWindow() != null) {
            scanDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        scanDialog.show();

        ProgressBar pb = scanView.findViewById(R.id.pb_cache_scan);
        TextView tvPercent = scanView.findViewById(R.id.tv_scan_percent);
        TextView tvCount = scanView.findViewById(R.id.tv_scan_count);
        TextView tvAppName = scanView.findViewById(R.id.tv_scan_app_name);

        AppCacheScanner.scanApps(this, mode, new AppCacheScanner.ScanCallback() {
            @Override
            public void onProgress(int current, int total, String currentAppName) {
                runOnUiThread(() -> {
                    if (pb != null) {
                        pb.setMax(total);
                        pb.setProgress(current);
                    }
                    if (tvPercent != null) {
                        int pct = total > 0 ? (int) ((current * 100f) / total) : 0;
                        tvPercent.setText(pct + "%");
                    }
                    if (tvCount != null) {
                        tvCount.setText(current + " / " + total);
                    }
                    if (tvAppName != null) {
                        tvAppName.setText(currentAppName);
                    }
                });
            }

            @Override
            public void onComplete(List<AppCacheItem> items, long totalCacheBytes) {
                runOnUiThread(() -> {
                    if (scanDialog.isShowing()) {
                        scanDialog.dismiss();
                    }
                    showCacheAppListDialog(items, totalCacheBytes);
                });
            }
        });
    }

    private void showCacheAppListDialog(List<AppCacheItem> items, long initialTotalBytes) {
        if (items.isEmpty()) {
            Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show();
            return;
        }

        View listView = LayoutInflater.from(this).inflate(R.layout.dialog_cache_app_list, null);
        AlertDialog listDialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setView(listView)
                .setCancelable(true)
                .create();

        if (listDialog.getWindow() != null) {
            listDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView btnBack = listView.findViewById(R.id.btn_cache_back);
        TextView tvSubtitle = listView.findViewById(R.id.tv_cache_subtitle);
        TextView btnInstantWipe = listView.findViewById(R.id.btn_instant_wipe);
        TextView tabAll = listView.findViewById(R.id.tab_all_apps);
        TextView tabUser = listView.findViewById(R.id.tab_user_apps);
        TextView tabSystem = listView.findViewById(R.id.tab_system_apps);
        EditText etSearch = listView.findViewById(R.id.et_cache_search);
        ListView lvApps = listView.findViewById(R.id.lv_cache_apps);
        TextView chipToggle = listView.findViewById(R.id.chip_toggle_select);
        Button btnClean = listView.findViewById(R.id.btn_clean_selected);

        final AppCacheListAdapter[] adapterHolder = new AppCacheListAdapter[1];

        Runnable updateSummary = () -> {
            if (adapterHolder[0] == null) return;
            long totalSel = adapterHolder[0].getTotalSelectedBytes();
            int count = adapterHolder[0].getSelectedCount();
            if (tvSubtitle != null) {
                tvSubtitle.setText(CacheCleaner.formatSize(totalSel) + " Selected (" + count + " apps)");
            }
            if (btnClean != null) {
                btnClean.setText("🧹 Clean Selected (" + CacheCleaner.formatSize(totalSel) + ")");
            }
            if (chipToggle != null) {
                chipToggle.setText(adapterHolder[0].areAllSelected() ? "☐ None" : "☑ All");
            }
        };

        adapterHolder[0] = new AppCacheListAdapter(this, items, (totalSelectedBytes, count) -> updateSummary.run());
        lvApps.setAdapter(adapterHolder[0]);
        updateSummary.run();

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> listDialog.dismiss());
        }

        if (btnInstantWipe != null) {
            btnInstantWipe.setOnClickListener(v -> {
                listDialog.dismiss();
                executeFastSystemCacheClean();
            });
        }

        if (tabAll != null && tabUser != null && tabSystem != null) {
            tabAll.setOnClickListener(v -> {
                adapterHolder[0].setTabFilter(0);
                tabAll.setBackgroundResource(R.drawable.tab_segmented_selected);
                tabAll.setTextColor(Color.WHITE);
                tabUser.setBackgroundResource(R.drawable.tab_segmented_unselected);
                tabUser.setTextColor(Color.parseColor("#94A3B8"));
                tabSystem.setBackgroundResource(R.drawable.tab_segmented_unselected);
                tabSystem.setTextColor(Color.parseColor("#94A3B8"));
                updateSummary.run();
            });
            tabUser.setOnClickListener(v -> {
                adapterHolder[0].setTabFilter(1);
                tabUser.setBackgroundResource(R.drawable.tab_segmented_selected);
                tabUser.setTextColor(Color.WHITE);
                tabAll.setBackgroundResource(R.drawable.tab_segmented_unselected);
                tabAll.setTextColor(Color.parseColor("#94A3B8"));
                tabSystem.setBackgroundResource(R.drawable.tab_segmented_unselected);
                tabSystem.setTextColor(Color.parseColor("#94A3B8"));
                updateSummary.run();
            });
            tabSystem.setOnClickListener(v -> {
                adapterHolder[0].setTabFilter(2);
                tabSystem.setBackgroundResource(R.drawable.tab_segmented_selected);
                tabSystem.setTextColor(Color.WHITE);
                tabAll.setBackgroundResource(R.drawable.tab_segmented_unselected);
                tabAll.setTextColor(Color.parseColor("#94A3B8"));
                tabUser.setBackgroundResource(R.drawable.tab_segmented_unselected);
                tabUser.setTextColor(Color.parseColor("#94A3B8"));
                updateSummary.run();
            });
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapterHolder[0].setSearchQuery(s.toString());
                    updateSummary.run();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (chipToggle != null) {
            chipToggle.setOnClickListener(v -> {
                if (adapterHolder[0].areAllSelected()) {
                    adapterHolder[0].deselectAll();
                } else {
                    adapterHolder[0].selectAll();
                }
                updateSummary.run();
            });
        }

        if (btnClean != null) {
            btnClean.setOnClickListener(v -> {
                List<String> selectedPkgs = adapterHolder[0].getSelectedPackages();
                if (selectedPkgs.isEmpty()) {
                    Toast.makeText(this, "Please select at least one app to clean", Toast.LENGTH_SHORT).show();
                    return;
                }
                listDialog.dismiss();
                RootHelper.checkRootAccess(isRooted -> runOnUiThread(() -> {
                    if (isRooted) {
                        Toast.makeText(MainActivity.this, "⚡ Cleaning cache via Root in 1 command...", Toast.LENGTH_SHORT).show();
                        RootHelper.executeRootCacheClean(getApplicationContext(), (bytesCleaned, success) -> runOnUiThread(() -> {
                            refreshSystemResources();
                            String formatted = CacheCleaner.formatSize(bytesCleaned);
                            DownloadClassifier.logEvent("Cache Cleaner (Root): cleared " + formatted + " in 1 command");
                            mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
                            Toast.makeText(MainActivity.this, "⚡ Root: Cleared " + formatted + " cache in 1 command!", Toast.LENGTH_SHORT).show();
                        }));
                    } else {
                        startBatchSettingsCleaningForPackages(selectedPkgs);
                    }
                }));
            });
        }

        listDialog.show();
    }

    private void startBatchSettingsCleaningForPackages(List<String> targetPackages) {
        if (!AppCacheCleanerAccessibilityService.isServiceRunning()) {
            showAccessibilityRequiredDialog();
            return;
        }

        AppCacheCleanerAccessibilityService.setProgressListener(new AppCacheCleanerAccessibilityService.CleanProgressListener() {
            @Override
            public void onProgress(String packageName, int current, int total) {
                // Handled cleanly by floating shortcut pill and fullscreen overlay
            }

            @Override
            public void onComplete(int totalCleaned) {
                runOnUiThread(() -> {
                    executeFastSystemCacheClean();
                    DownloadClassifier.logEvent("Batch Clear Cache: processed " + totalCleaned + " apps in System Settings");
                    mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
                    Toast.makeText(MainActivity.this,
                        "Batch clear completed! Processed " + totalCleaned + " apps.",
                        Toast.LENGTH_LONG).show();
                });
            }
        });

        AppCacheCleanerAccessibilityService svc = AppCacheCleanerAccessibilityService.getInstance();
        if (svc != null) {
            svc.startBatchClean(targetPackages);
        }
    }

    private void showAccessibilityRequiredDialog() {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Accessibility Permission Required")
            .setMessage("To automatically clear cache for all apps in System -> Apps -> Storage & Cache all at once, please enable 'Clean Phone - Your Phone Automation' in Accessibility settings.\n\nTap 'Enable Service' below to open Accessibility Settings.")
            .setPositiveButton("Enable Service", (dialog, which) -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open Accessibility Settings", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showAppCloserDialog() {
        checkOverlayPermissionAndPrompt();
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_app_closer, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView btnClose = dialogView.findViewById(R.id.btn_closer_close);
        Button btnTabUser = dialogView.findViewById(R.id.btn_tab_user);
        Button btnTabSystem = dialogView.findViewById(R.id.btn_tab_system);
        Button btnTabAll = dialogView.findViewById(R.id.btn_tab_all);
        TextView tvSelectedCount = dialogView.findViewById(R.id.tv_selected_count);
        Button btnSelectAll = dialogView.findViewById(R.id.btn_select_all);
        Button btnDeselectAll = dialogView.findViewById(R.id.btn_deselect_all);
        ListView lvApps = dialogView.findViewById(R.id.lv_apps);
        Button btnExecute = dialogView.findViewById(R.id.btn_execute_close_apps);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        final List<AppInfoItem> masterList = new ArrayList<>();
        final List<AppInfoItem> displayList = new ArrayList<>();
        final int[] currentTab = new int[]{0}; // 0 = user, 1 = system, 2 = all

        AppCloserAdapter adapter = new AppCloserAdapter(this, displayList);
        lvApps.setAdapter(adapter);

        Runnable updateDisplay = () -> {
            displayList.clear();
            for (AppInfoItem item : masterList) {
                if (currentTab[0] == 0 && !item.isSystem) {
                    displayList.add(item);
                } else if (currentTab[0] == 1 && item.isSystem) {
                    displayList.add(item);
                } else if (currentTab[0] == 2) {
                    displayList.add(item);
                }
            }
            adapter.notifyDataSetChanged();

            int selected = 0;
            for (AppInfoItem item : displayList) {
                if (item.isSelected) selected++;
            }
            tvSelectedCount.setText(selected + " / " + displayList.size() + " apps selected");
        };

        adapter.setOnSelectionChangedListener(() -> {
            int selected = 0;
            for (AppInfoItem item : displayList) {
                if (item.isSelected) selected++;
            }
            tvSelectedCount.setText(selected + " / " + displayList.size() + " apps selected");
        });

        // Load all active / background apps asynchronously & instantly
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(0);
            List<AppInfoItem> userApps = new ArrayList<>();
            List<AppInfoItem> systemApps = new ArrayList<>();

            for (ApplicationInfo ai : installed) {
                if (getPackageName().equals(ai.packageName)) continue;

                boolean isSys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

                if (isSys) {
                    // Filter out critical core OS packages from kill list
                    if ("android".equals(ai.packageName) ||
                        "com.android.systemui".equals(ai.packageName) ||
                        ai.packageName.startsWith("com.android.keyguard") ||
                        ai.packageName.startsWith("com.android.launcher") ||
                        ai.packageName.startsWith("com.motorola.launcher") ||
                        ai.packageName.startsWith("com.google.android.inputmethod")) {
                        continue;
                    }
                    if (pm.getLaunchIntentForPackage(ai.packageName) == null) {
                        continue;
                    }
                }

                CharSequence labelCs = ai.loadLabel(pm);
                String label = labelCs != null ? labelCs.toString() : ai.packageName;
                AppInfoItem item = new AppInfoItem(label, ai.packageName, null, isSys);
                if (!isSys) {
                    item.isSelected = true; // Pre-select user apps by default
                    userApps.add(item);
                } else {
                    item.isSelected = false;
                    systemApps.add(item);
                }
            }

            Collections.sort(userApps, (a, b) -> a.appName.compareToIgnoreCase(b.appName));
            Collections.sort(systemApps, (a, b) -> a.appName.compareToIgnoreCase(b.appName));

            final List<AppInfoItem> combined = new ArrayList<>();
            combined.addAll(userApps);
            combined.addAll(systemApps);

            runOnUiThread(() -> {
                masterList.clear();
                masterList.addAll(combined);
                updateDisplay.run();
            });
        }).start();

        // Tabs switching
        btnTabUser.setOnClickListener(v -> {
            currentTab[0] = 0;
            btnTabUser.setBackgroundResource(R.drawable.tab_btn_selected);
            btnTabUser.setTextColor(Color.WHITE);
            btnTabSystem.setBackgroundResource(R.drawable.tab_btn_unselected);
            btnTabSystem.setTextColor(Color.parseColor("#94A3B8"));
            btnTabAll.setBackgroundResource(R.drawable.tab_btn_unselected);
            btnTabAll.setTextColor(Color.parseColor("#94A3B8"));
            updateDisplay.run();
        });

        btnTabSystem.setOnClickListener(v -> {
            currentTab[0] = 1;
            btnTabSystem.setBackgroundResource(R.drawable.tab_btn_selected);
            btnTabSystem.setTextColor(Color.WHITE);
            btnTabUser.setBackgroundResource(R.drawable.tab_btn_unselected);
            btnTabUser.setTextColor(Color.parseColor("#94A3B8"));
            btnTabAll.setBackgroundResource(R.drawable.tab_btn_unselected);
            btnTabAll.setTextColor(Color.parseColor("#94A3B8"));
            updateDisplay.run();
        });

        btnTabAll.setOnClickListener(v -> {
            currentTab[0] = 2;
            btnTabAll.setBackgroundResource(R.drawable.tab_btn_selected);
            btnTabAll.setTextColor(Color.WHITE);
            btnTabUser.setBackgroundResource(R.drawable.tab_btn_unselected);
            btnTabUser.setTextColor(Color.parseColor("#94A3B8"));
            btnTabSystem.setBackgroundResource(R.drawable.tab_btn_unselected);
            btnTabSystem.setTextColor(Color.parseColor("#94A3B8"));
            updateDisplay.run();
        });

        btnSelectAll.setOnClickListener(v -> {
            for (AppInfoItem item : displayList) {
                item.isSelected = true;
            }
            adapter.notifyDataSetChanged();
            tvSelectedCount.setText(displayList.size() + " / " + displayList.size() + " apps selected");
        });

        btnDeselectAll.setOnClickListener(v -> {
            for (AppInfoItem item : displayList) {
                item.isSelected = false;
            }
            adapter.notifyDataSetChanged();
            tvSelectedCount.setText("0 / " + displayList.size() + " apps selected");
        });

        btnExecute.setOnClickListener(v -> {
            List<String> toKill = new ArrayList<>();
            for (AppInfoItem item : masterList) {
                if (item.isSelected) {
                    toKill.add(item.packageName);
                }
            }

            if (toKill.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please select at least 1 app to close", Toast.LENGTH_SHORT).show();
                return;
            }

            RootHelper.checkRootAccess(isRooted -> runOnUiThread(() -> {
                if (isRooted) {
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "🛑 Closing " + toKill.size() + " apps via Root...", Toast.LENGTH_SHORT).show();
                    RootHelper.executeRootKillApps(getApplicationContext(), toKill, (appsKilled, success) -> runOnUiThread(() -> {
                        refreshSystemResources();
                        DownloadClassifier.logEvent("App Closer (Root): force-stopped " + appsKilled + " selected apps in 1 command");
                        mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
                        Toast.makeText(MainActivity.this, "🛑 Root: Force-stopped " + appsKilled + " apps in 1 command!", Toast.LENGTH_SHORT).show();
                    }));
                } else {
                    dialog.dismiss();
                    moveTaskToBack(true);

                    // 1. Immediately kill background cached processes via ActivityManager
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    new Thread(() -> {
                        for (String pkg : toKill) {
                            try {
                                am.killBackgroundProcesses(pkg);
                            } catch (Exception ignored) {}
                        }
                    }).start();

                    // 2. Automated deep Force Stop via Accessibility Service (like Baxa / KillApps)
                    if (AppCacheCleanerAccessibilityService.isServiceRunning()) {
                        AppCacheCleanerAccessibilityService.setProgressListener(new AppCacheCleanerAccessibilityService.CleanProgressListener() {
                            @Override
                            public void onProgress(String packageName, int current, int total) {
                                // Handled by the floating shortcut window overlay
                            }

                            @Override
                            public void onComplete(int totalClosed) {
                                DownloadClassifier.logEvent("App Closer: force-stopped " + totalClosed + " apps & reclaimed RAM");
                                runOnUiThread(() -> {
                                    refreshSystemResources();
                                    mTvLog.setText(DownloadClassifier.getRecentLogsJoined());
                                    Toast.makeText(MainActivity.this,
                                        "Successfully closed " + totalClosed + " apps and freed memory!",
                                        Toast.LENGTH_LONG).show();
                                });
                            }
                        });

                        AppCacheCleanerAccessibilityService.getInstance().startBatchCloseApps(toKill);
                    } else {
                        showAccessibilityPromptForAppCloser(toKill);
                    }
                }
            }));
        });

        dialog.show();
    }

    private void showAccessibilityPromptForAppCloser(List<String> packages) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("⚡ Deep App Closer")
            .setMessage("Background cached processes for " + packages.size() + " apps were terminated.\n\nTo automate deep Force Stop for all active apps (like Baxa / KillApps), please enable the Automation Service in Accessibility Settings.")
            .setPositiveButton("⚙️ Enable in Settings", (d, w) -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open Accessibility Settings", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Done", (d, w) -> {
                refreshSystemResources();
                Toast.makeText(this, "Terminated background cached processes for " + packages.size() + " apps", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void checkOverlayPermissionAndPrompt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Floating Shortcut Permission")
                .setMessage("To display the floating shortcut pill and seamless progress overlay over other apps (like Baxa / iOS shortcuts), please allow 'Display over other apps'.")
                .setPositiveButton("⚙️ Grant Permission", (d, w) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Later", null)
                .show();
        }
    }

    private void showHelpDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_help, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView btnClose = dialogView.findViewById(R.id.btn_close_dialog);
        Button btnPrivacy = dialogView.findViewById(R.id.btn_privacy_policy);
        Button btnTerms = dialogView.findViewById(R.id.btn_terms_conditions);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnPrivacy != null) {
            btnPrivacy.setOnClickListener(v -> {
                showPolicyDialog("Privacy Policy",
                    "Privacy Policy for Cache & File Organizer:\n\n" +
                    "1. Local-Only Processing: All cache cleaning, app closing, and download classification operations execute 100% locally on your device.\n\n" +
                    "2. Zero Data Collection: We do not collect, transmit, upload, or sell your files, downloads, running apps, or telemetry.\n\n" +
                    "3. Permissions: Storage and inotify permissions are strictly utilized to discover and clean junk cache folders and organize files as directed by you.");
            });
        }

        if (btnTerms != null) {
            btnTerms.setOnClickListener(v -> {
                showPolicyDialog("Terms and Conditions",
                    "Terms & Conditions for Cache & File Organizer:\n\n" +
                    "1. Usage: This utility is provided to assist in maintaining organized storage directories, closing background tasks, and clearing temporary cache files.\n\n" +
                    "2. User Responsibility: Cache cleaner permanently clears cache directories and temporary trash files to reclaim space.\n\n" +
                    "3. License & Warranty: The software is provided 'as is' without warranties of any kind. You retain full control over your files and watch directories.");
            });
        }

        dialog.show();
    }

    private void showPolicyDialog(String title, String message) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showFolderSelectionDialog() {
        final List<String> labels = new ArrayList<>();
        final List<String> paths = new ArrayList<>();

        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloads != null) {
            labels.add("📥 Downloads (" + downloads.getAbsolutePath() + ")");
            paths.add(downloads.getAbsolutePath());
        }

        File documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (documents != null) {
            labels.add("📄 Documents (" + documents.getAbsolutePath() + ")");
            paths.add(documents.getAbsolutePath());
        }

        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (pictures != null) {
            labels.add("🖼️ Pictures (" + pictures.getAbsolutePath() + ")");
            paths.add(pictures.getAbsolutePath());
        }

        File rootStorage = Environment.getExternalStorageDirectory();
        if (rootStorage != null) {
            labels.add("💾 Root Internal Storage (" + rootStorage.getAbsolutePath() + ")");
            paths.add(rootStorage.getAbsolutePath());
        }

        labels.add("📂 Browse via System File Picker...");
        paths.add("__SYS_PICKER__");

        CharSequence[] items = labels.toArray(new CharSequence[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        builder.setTitle("Select Watch Directory");
        builder.setItems(items, (dialog, which) -> {
            String selected = paths.get(which);
            if ("__SYS_PICKER__".equals(selected)) {
                openSystemFolderPicker();
            } else {
                applyNewWatchedDirectory(selected);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void openSystemFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_FOLDER);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to launch system folder picker", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                } catch (Exception ignored) {}

                String resolvedPath = resolvePathFromUri(treeUri);
                if (resolvedPath != null) {
                    applyNewWatchedDirectory(resolvedPath);
                } else {
                    Toast.makeText(this, "Could not resolve physical folder path", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String resolvePathFromUri(Uri uri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(uri);
            if (docId != null) {
                String[] parts = docId.split(":");
                String type = parts[0];
                String relativePath = parts.length > 1 ? parts[1] : "";
                if ("primary".equalsIgnoreCase(type)) {
                    File ext = Environment.getExternalStorageDirectory();
                    return new File(ext, relativePath).getAbsolutePath();
                } else {
                    return "/storage/" + type + "/" + relativePath;
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return null;
    }

    private void applyNewWatchedDirectory(String newPath) {
        File dir = new File(newPath);
        if (!dir.exists() || !dir.isDirectory()) {
            Toast.makeText(this, "Selected folder does not exist or is not a directory", Toast.LENGTH_LONG).show();
            return;
        }

        DownloadClassifier.setWatchedDir(this, newPath);
        updateWatchPathUI();
        refreshCategoryCounts();

        Intent serviceIntent = new Intent(this, DownloadObserverService.class);
        serviceIntent.setAction(DownloadObserverService.ACTION_UPDATE_WATCH_DIR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "Watching: " + dir.getName(), Toast.LENGTH_SHORT).show();
    }

    private void updateServiceUIState() {
        boolean running = DownloadObserverService.isRunning();
        mIsProgrammaticSwitchChange = true;
        mSwitchService.setChecked(running);
        mIsProgrammaticSwitchChange = false;
        applyServiceVisualState(running);
    }

    private void applyServiceVisualState(boolean running) {
        if (running) {
            mTvStatusBadge.setText("ACTIVE");
            boolean isDark = ThemeHelper.isDarkMode(this);
            mTvStatusBadge.setTextColor(Color.parseColor(isDark ? "#10B981" : "#059669"));
            mTvStatusBadge.setBackgroundResource(R.drawable.pill_status_active);
            mTvServiceSubtitle.setText("Sleeping until download event (0% Battery)");
            mTvServiceSubtitle.setTextColor(Color.parseColor(isDark ? "#10B981" : "#059669"));
        } else {
            mTvStatusBadge.setText("PAUSED");
            mTvStatusBadge.setTextColor(Color.parseColor("#EF4444"));
            mTvStatusBadge.setBackgroundResource(R.drawable.pill_status_stopped);
            mTvServiceSubtitle.setText("Monitoring is turned off");
            mTvServiceSubtitle.setTextColor(Color.parseColor("#EF4444"));
        }
    }

    private void refreshCategoryCounts() {
        new Thread(() -> {
            int images = DownloadClassifier.countFilesInFolder(getApplicationContext(), "Images");
            int docs = DownloadClassifier.countFilesInFolder(getApplicationContext(), "Documents");
            int archives = DownloadClassifier.countFilesInFolder(getApplicationContext(), "Archives");
            int code = DownloadClassifier.countFilesInFolder(getApplicationContext(), "Code_and_Notes");
            int audio = DownloadClassifier.countFilesInFolder(getApplicationContext(), "Audio");
            int videos = DownloadClassifier.countFilesInFolder(getApplicationContext(), "Videos");
            int total = images + docs + archives + code + audio + videos;

            runOnUiThread(() -> {
                mTvCountImages.setText(String.valueOf(images));
                mTvCountDocs.setText(String.valueOf(docs));
                mTvCountArchives.setText(String.valueOf(archives));
                mTvCountCode.setText(String.valueOf(code));
                mTvCountAudio.setText(String.valueOf(audio));
                mTvCountVideos.setText(String.valueOf(videos));
                mTvTotalFiles.setText(total + " files sorted");
            });
        }).start();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        }
    }

    private void ensureServiceStarted() {
        DownloadObserverService.setRunning(true);
        Intent serviceIntent = new Intent(this, DownloadObserverService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopOrganizerService() {
        DownloadObserverService.setRunning(false);
        Intent serviceIntent = new Intent(this, DownloadObserverService.class);
        serviceIntent.setAction(DownloadObserverService.ACTION_STOP_SERVICE);
        startService(serviceIntent);
    }
}
