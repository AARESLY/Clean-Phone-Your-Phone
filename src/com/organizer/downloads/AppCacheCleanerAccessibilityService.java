package com.organizer.downloads;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class AppCacheCleanerAccessibilityService extends AccessibilityService {

    private static final String TAG = "CleanerAutomation";

    public interface CleanProgressListener {
        void onProgress(String packageName, int current, int total);
        void onComplete(int totalCleaned);
    }

    private static AppCacheCleanerAccessibilityService sInstance = null;
    private static CleanProgressListener sListener = null;

    public static final int MODE_CLEAR_CACHE = 1;
    public static final int MODE_CLOSE_APPS = 2;
    private int mCurrentMode = MODE_CLEAR_CACHE;

    private final List<String> mPackageQueue = new ArrayList<>();
    private int mTotalToClean = 0;
    private int mCurrentIndex = 0;
    private boolean mIsCleaning = false;
    private boolean mIsPaused = false;
    private String mCurrentPackage = null;
    private long mPackageStartTime = 0;
    private boolean mClickedStorage = false;
    private boolean mClickedClearCache = false;
    private boolean mClickedForceStop = false;
    private boolean mConfirmedForceStop = false;
    private boolean mSkippedAlreadyStopped = false;
    private boolean mAdvancingToNext = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Runnable mSafetyTimeoutRunnable = () -> {
        if (mIsCleaning && !mIsPaused) {
            advanceToNext(100);
        }
    };

    private WindowManager mWindowManager;

    // Baxa-style Fullscreen Closing UI Elements
    private View mFullscreenView;
    private WindowManager.LayoutParams mFullscreenParams;
    private ProgressBar mPbClosingCircle;
    private TextView mTvClosingPercent;
    private TextView mTvClosingAppName;
    private TextView mTvClosingCount;
    private ProgressBar mPbClosingHorizontal;
    private Button mBtnClosingStop;
    private Button mBtnClosingMinimize;

    // Floating Shortcut Pill Overlay Elements
    private View mFloatingView;
    private WindowManager.LayoutParams mFloatingParams;
    private TextView mTvFloatIcon;
    private TextView mTvFloatCount;
    private TextView mTvFloatAppName;
    private Button mBtnFloatPauseResume;
    private Button mBtnFloatStop;

    private boolean mIsMinimized = false;

    public static boolean isServiceRunning() {
        return sInstance != null;
    }

    public static AppCacheCleanerAccessibilityService getInstance() {
        return sInstance;
    }

    public static void setProgressListener(CleanProgressListener listener) {
        sListener = listener;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.i(TAG, "Accessibility service connected");

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                      AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                      AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.packageNames = null;
        info.notificationTimeout = 20;
        setServiceInfo(info);

        try {
            mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        } catch (Exception e) {
            Log.e(TAG, "Error getting WindowManager", e);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sInstance = null;
        mIsCleaning = false;
        hideAllOverlays();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        mIsCleaning = false;
        hideAllOverlays();
    }

    public void startBatchClean(List<String> packages) {
        if (packages == null || packages.isEmpty()) {
            if (sListener != null) sListener.onComplete(0);
            return;
        }
        mCurrentMode = MODE_CLEAR_CACHE;
        mPackageQueue.clear();
        mPackageQueue.addAll(packages);
        mTotalToClean = mPackageQueue.size();
        mCurrentIndex = 0;
        mIsCleaning = true;
        mIsPaused = false;
        mIsMinimized = true; // Floating shortcut pill for cache cleaner

        showFloatingWindow();
        processNextPackage();
    }

    public void startBatchCloseApps(List<String> packages) {
        if (packages == null || packages.isEmpty()) {
            if (sListener != null) sListener.onComplete(0);
            return;
        }
        mCurrentMode = MODE_CLOSE_APPS;
        mPackageQueue.clear();
        mPackageQueue.addAll(packages);
        mTotalToClean = mPackageQueue.size();
        mCurrentIndex = 0;
        mIsCleaning = true;
        mIsPaused = false;
        mIsMinimized = false; // Start in Baxa-style fullscreen AMOLED closing mode

        showFullscreenClosingWindow();
        processNextPackage();
    }

    public void pauseCleaning() {
        mIsPaused = true;
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(() -> {
            if (mBtnFloatPauseResume != null) {
                mBtnFloatPauseResume.setText("▶");
            }
            if (mTvFloatAppName != null) {
                mTvFloatAppName.setText("⏸ Paused");
            }
        });
    }

    public void resumeCleaning() {
        if (!mIsCleaning) return;
        mIsPaused = false;
        mHandler.post(() -> {
            if (mBtnFloatPauseResume != null) {
                mBtnFloatPauseResume.setText("⏸");
            }
        });
        processNextPackage();
    }

    public void stopCleaning() {
        mIsCleaning = false;
        mIsPaused = false;
        mPackageQueue.clear();
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(() -> {
            hideAllOverlays();
            bringMainActivityToFront();
        });
    }

    private void processNextPackage() {
        if (!mIsCleaning || mIsPaused) return;

        if (mCurrentIndex >= mTotalToClean) {
            // All apps processed!
            mIsCleaning = false;
            final int completed = mTotalToClean;
            mHandler.postDelayed(() -> {
                hideAllOverlays();
                if (sListener != null) {
                    sListener.onComplete(completed);
                }
                bringMainActivityToFront();
            }, 300);
            return;
        }

        mCurrentPackage = mPackageQueue.get(mCurrentIndex);
        mPackageStartTime = System.currentTimeMillis();
        mClickedStorage = false;
        mClickedClearCache = false;
        mClickedForceStop = false;
        mConfirmedForceStop = false;
        mSkippedAlreadyStopped = false;
        mAdvancingToNext = false;

        Log.i(TAG, "processNextPackage: [" + (mCurrentIndex + 1) + "/" + mTotalToClean + "] " + mCurrentPackage);
        updateProgressUI(mCurrentPackage, mCurrentIndex, mTotalToClean);

        if (sListener != null) {
            sListener.onProgress(mCurrentPackage, mCurrentIndex, mTotalToClean);
        }

        // Launch Settings App details for current package
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + mCurrentPackage));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to open details for: " + mCurrentPackage, e);
            advanceToNext(100);
            return;
        }

        // Safety timeout per app (4500ms max to allow Compose Settings to initialize)
        mHandler.removeCallbacks(mSafetyTimeoutRunnable);
        mHandler.postDelayed(mSafetyTimeoutRunnable, 4500);

        // Proactive polling checks in case OS events are dropped or coalesced
        scheduleProactiveWindowChecks(1);
    }

    private void scheduleProactiveWindowChecks(final int attempt) {
        if (!mIsCleaning || mIsPaused || mAdvancingToNext) return;
        if (mCurrentMode == MODE_CLOSE_APPS && (mConfirmedForceStop || mSkippedAlreadyStopped)) return;
        if (mCurrentMode == MODE_CLEAR_CACHE && mClickedClearCache) return;

        mHandler.postDelayed(() -> {
            if (!mIsCleaning || mIsPaused || mAdvancingToNext) return;
            if (mCurrentMode == MODE_CLOSE_APPS && (mConfirmedForceStop || mSkippedAlreadyStopped)) return;
            if (mCurrentMode == MODE_CLEAR_CACHE && mClickedClearCache) return;

            AccessibilityNodeInfo root = findSettingsRoot();
            if (root != null) {
                if (mCurrentMode == MODE_CLOSE_APPS) {
                    handleForceStopEvent(root);
                } else {
                    handleClearCacheEvent(root);
                }
            }

            boolean shouldContinue = false;
            if (mCurrentMode == MODE_CLOSE_APPS) {
                shouldContinue = !mConfirmedForceStop && !mSkippedAlreadyStopped;
            } else {
                shouldContinue = !mClickedClearCache;
            }

            if (shouldContinue && !mAdvancingToNext && attempt < 12) {
                scheduleProactiveWindowChecks(attempt + 1);
            }
        }, 250);
    }

    private void advanceToNext(long delayMs) {
        if (mAdvancingToNext) return;
        mAdvancingToNext = true;
        mHandler.removeCallbacks(mSafetyTimeoutRunnable);
        Log.i(TAG, "advanceToNext: completed " + mCurrentPackage + ", moving to index " + (mCurrentIndex + 1));
        mCurrentIndex++;
        updateProgressUI(mCurrentPackage, mCurrentIndex, mTotalToClean);
        if (sListener != null) {
            sListener.onProgress(mCurrentPackage, mCurrentIndex, mTotalToClean);
        }
        mHandler.postDelayed(this::processNextPackage, delayMs);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!mIsCleaning || mIsPaused || mCurrentPackage == null) return;

        CharSequence eventPkg = event.getPackageName();
        if (eventPkg == null) return;
        String pkg = eventPkg.toString();

        // STRICT PACKAGE FILTER:
        // NEVER handle events from our own app (com.organizer.downloads)!
        if (pkg.equals(getPackageName())) return;
        // Only react to Settings and Android OS framework dialogs
        if (!"com.android.settings".equals(pkg) && !"android".equals(pkg)) {
            return;
        }

        Log.i(TAG, "onAccessibilityEvent: from " + pkg + ", type=" + AccessibilityEvent.eventTypeToString(event.getEventType()));

        // Get the actual Settings root (not our own overlay, and not a child leaf node)
        AccessibilityNodeInfo root = findSettingsRoot();
        if (root == null) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                AccessibilityNodeInfo curr = source;
                while (curr.getParent() != null) {
                    curr = curr.getParent();
                }
                root = curr;
            }
        }
        Log.i(TAG, "Root resolved: " + (root != null ? (root.getPackageName() + " / " + root.getClassName()) : "null"));
        if (root == null) return;

        if (mCurrentMode == MODE_CLOSE_APPS) {
            handleForceStopEvent(root);
        } else {
            handleClearCacheEvent(root);
        }
    }

    private AccessibilityNodeInfo findSettingsRoot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo win : windows) {
                        if (win == null) continue;
                        AccessibilityNodeInfo wRoot = win.getRoot();
                        if (wRoot != null) {
                            CharSequence wPkg = wRoot.getPackageName();
                            if (wPkg != null && ("com.android.settings".equals(wPkg.toString()) || "android".equals(wPkg.toString()))) {
                                return wRoot;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && active.getPackageName() != null) {
            String p = active.getPackageName().toString();
            if ("com.android.settings".equals(p) || "android".equals(p)) {
                return active;
            }
        }
        return null;
    }

    private void handleForceStopEvent(AccessibilityNodeInfo root) {
        if (!mIsCleaning || mIsPaused || mConfirmedForceStop || mSkippedAlreadyStopped || mAdvancingToNext) return;

        // Step 1: Check if system confirmation dialog ("OK" / Confirm) is visible
        AccessibilityNodeInfo confirmNode = findAnyDialogConfirmNode(root);
        if (confirmNode != null) {
            Log.i(TAG, "Confirmation dialog detected for " + mCurrentPackage + "! Clicking OK...");
            mConfirmedForceStop = true;
            mHandler.removeCallbacks(mSafetyTimeoutRunnable);
            performClick(confirmNode);

            // Delay 350ms so system commits force-stop and dismisses dialog
            mHandler.postDelayed(() -> {
                if (mIsCleaning && !mIsPaused) {
                    advanceToNext(100);
                }
            }, 350);
            return;
        }

        // Step 2: Search for "Force stop" button on App Info page
        if (!mClickedForceStop && !mSkippedAlreadyStopped) {
            AccessibilityNodeInfo forceStopNode = findAnyForceStopNode(root);
            if (forceStopNode != null) {
                // Determine if button is enabled
                boolean isEnabled = forceStopNode.isEnabled();
                AccessibilityNodeInfo parent = forceStopNode.getParent();
                if (parent != null && parent.isClickable()) {
                    isEnabled = isEnabled && parent.isEnabled();
                }

                long elapsed = System.currentTimeMillis() - mPackageStartTime;
                Log.i(TAG, "Force Stop node found for " + mCurrentPackage + ": isEnabled=" + isEnabled + ", elapsed=" + elapsed);

                if (isEnabled) {
                    // App is running! Click Force stop!
                    Log.i(TAG, "Force Stop button found & enabled for " + mCurrentPackage + ". Clicking...");
                    mClickedForceStop = true;
                    performClick(forceStopNode);

                    // Polling checks for confirmation dialog or disabled button
                    scheduleConfirmationChecks(1);
                } else {
                    // Button is disabled.
                    // Compose Settings in Android 14/15 needs ~350ms to evaluate process state!
                    if (elapsed >= 350) {
                        Log.i(TAG, "Force Stop button already disabled for " + mCurrentPackage + " (already stopped). Skipping.");
                        mSkippedAlreadyStopped = true;
                        mHandler.removeCallbacks(mSafetyTimeoutRunnable);
                        advanceToNext(100);
                    }
                }
            }
        }
    }

    private AccessibilityNodeInfo findAnyForceStopNode(AccessibilityNodeInfo root) {
        if (root != null) {
            AccessibilityNodeInfo n = findForceStopNode(root);
            if (n != null) return n;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo win : windows) {
                        if (win == null) continue;
                        AccessibilityNodeInfo wRoot = win.getRoot();
                        if (wRoot != null) {
                            AccessibilityNodeInfo n = findForceStopNode(wRoot);
                            if (n != null) return n;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (root != null) {
            Log.d(TAG, "findAnyForceStopNode failed on root " + root.getPackageName() + ", dumping tree:");
            logNodeTree(root, 0);
        }
        return null;
    }

    private void scheduleConfirmationChecks(final int attempt) {
        if (!mIsCleaning || mIsPaused || mConfirmedForceStop) return;

        // Schedule check in 200ms intervals up to 12 attempts (2400ms total)
        mHandler.postDelayed(() -> {
            if (!mIsCleaning || mIsPaused || mConfirmedForceStop) return;

            AccessibilityNodeInfo activeRoot = findSettingsRoot();
            AccessibilityNodeInfo confirm = findAnyDialogConfirmNode(activeRoot);
            if (confirm != null) {
                Log.i(TAG, "Confirmation dialog found on check attempt " + attempt + " for " + mCurrentPackage + "! Clicking OK...");
                mConfirmedForceStop = true;
                mHandler.removeCallbacks(mSafetyTimeoutRunnable);
                performClick(confirm);
                mHandler.postDelayed(() -> {
                    if (mIsCleaning && !mIsPaused) {
                        advanceToNext(100);
                    }
                }, 350);
                return;
            }

            // If not found yet, check if Force Stop button became disabled (app stopped without dialog)
            if (activeRoot != null) {
                AccessibilityNodeInfo fs = findForceStopNode(activeRoot);
                if (fs != null && !fs.isEnabled()) {
                    Log.i(TAG, "Force stop disabled on attempt " + attempt + "! App successfully stopped.");
                    mConfirmedForceStop = true;
                    mHandler.removeCallbacks(mSafetyTimeoutRunnable);
                    advanceToNext(100);
                    return;
                }
            }

            if (attempt < 12) {
                scheduleConfirmationChecks(attempt + 1);
            }
        }, 200);
    }

    private void handleClearCacheEvent(AccessibilityNodeInfo root) {
        if (!mIsCleaning || mIsPaused || mClickedClearCache || mAdvancingToNext) return;

        // Step 1: Check if we are already on the Storage details screen (e.g. "Clear cache" button is visible)
        AccessibilityNodeInfo clearCacheNode = findAnyClearCacheNode(root);
        if (clearCacheNode != null) {
            Log.i(TAG, "Clear cache button detected for " + mCurrentPackage + "! isEnabled=" + clearCacheNode.isEnabled());
            if (!clearCacheNode.isEnabled()) {
                Log.i(TAG, "Clear cache button is already disabled (0 B cache) for " + mCurrentPackage + ". Moving to next.");
                mClickedClearCache = true;
                mHandler.removeCallbacks(mSafetyTimeoutRunnable);
                advanceToNext(100);
                return;
            }

            Log.i(TAG, "Clicking Clear cache for " + mCurrentPackage + "...");
            mClickedClearCache = true;
            mHandler.removeCallbacks(mSafetyTimeoutRunnable);
            performClick(clearCacheNode);

            // Wait 250ms for Settings to update and clear cache, then advance to next package
            mHandler.postDelayed(() -> {
                if (mIsCleaning && !mIsPaused) {
                    advanceToNext(150);
                }
            }, 250);
            return;
        }

        // Step 2: If we haven't clicked Storage yet, find and click "Storage & cache" / "Storage"
        if (!mClickedStorage) {
            AccessibilityNodeInfo storageNode = findAnyStorageNode(root);
            if (storageNode != null) {
                Log.i(TAG, "Storage entry found for " + mCurrentPackage + "! Clicking...");
                mClickedStorage = true;
                performClick(storageNode);
                return;
            } else {
                Log.i(TAG, "Storage entry not visible for " + mCurrentPackage + ", attempting scroll down...");
                boolean scrolled = scrollForward(root);
                if (!scrolled) {
                    Rect screenBounds = new Rect();
                    root.getBoundsInScreen(screenBounds);
                    if (screenBounds.width() > 0 && screenBounds.height() > 0) {
                        int cx = screenBounds.centerX();
                        int startY = screenBounds.centerY() + screenBounds.height() / 4;
                        int endY = screenBounds.centerY() - screenBounds.height() / 4;
                        dispatchSwipeGesture(cx, startY, cx, endY);
                    }
                }
            }
        }
    }

    private AccessibilityNodeInfo findForceStopNode(AccessibilityNodeInfo root) {
        if (root == null) return null;

        // 1. Search by known Settings View IDs
        String[] ids = new String[]{
            "com.android.settings:id/button1",
            "com.android.settings:id/button3",
            "com.android.settings:id/right_button",
            "com.android.settings:id/force_stop_button",
            "com.android.settings:id/btn_force_stop",
            "com.android.settings:id/widget_button"
        };
        for (String id : ids) {
            List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId(id);
            if (byId != null && !byId.isEmpty()) {
                for (AccessibilityNodeInfo n : byId) {
                    if (isForceStopRelated(n)) {
                        AccessibilityNodeInfo clickable = findClickableParent(n);
                        return clickable != null ? clickable : n;
                    }
                }
            }
        }

        // 2. Search by exact text / content descriptions in Compose Settings
        String[] texts = new String[]{"Force stop", "FORCE STOP", "Force Stop", "Detener", "Forzar detención", "Arrêter", "Beenden erzwingen"};
        for (String txt : texts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(txt);
            if (nodes != null && !nodes.isEmpty()) {
                Log.i(TAG, "findForceStopNode: text '" + txt + "' found " + nodes.size() + " matches");
                for (AccessibilityNodeInfo n : nodes) {
                    CharSequence nText = n.getText();
                    CharSequence nDesc = n.getContentDescription();
                    boolean match = (nText != null && nText.toString().trim().equalsIgnoreCase(txt)) ||
                                    (nDesc != null && nDesc.toString().trim().equalsIgnoreCase(txt));
                    if (match) {
                        AccessibilityNodeInfo clickable = findClickableParent(n);
                        return clickable != null ? clickable : n;
                    }
                }
            }
        }
        // 3. Fallback: Full recursive traversal into Compose virtual accessibility tree!
        return findForceStopRecursive(root, texts, 0);
    }

    private AccessibilityNodeInfo findForceStopRecursive(AccessibilityNodeInfo node, String[] texts, int depth) {
        if (node == null || depth > 12) return null;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        for (String txt : texts) {
            boolean match = (text != null && text.toString().trim().equalsIgnoreCase(txt)) ||
                            (desc != null && desc.toString().trim().equalsIgnoreCase(txt));
            if (match) {
                // Must not be the dialog title question "Force stop?"
                if (text != null && text.toString().trim().endsWith("?")) continue;
                Log.i(TAG, "findForceStopRecursive matched '" + txt + "' at depth " + depth + " on " + node.getClassName());
                AccessibilityNodeInfo clickable = findClickableParent(node);
                return clickable != null ? clickable : node;
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findForceStopRecursive(child, texts, depth + 1);
                if (found != null) {
                    return found;
                }
                child.recycle();
            }
        }
        return null;
    }

    private void logNodeTree(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 5) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(node.getClassName());
        if (node.getViewIdResourceName() != null) sb.append(" id=").append(node.getViewIdResourceName());
        if (node.getText() != null) sb.append(" text='").append(node.getText()).append("'");
        if (node.getContentDescription() != null) sb.append(" desc='").append(node.getContentDescription()).append("'");
        sb.append(" clickable=").append(node.isClickable()).append(" enabled=").append(node.isEnabled());
        Log.i(TAG, sb.toString());
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                logNodeTree(child, depth + 1);
                child.recycle();
            }
        }
    }

    private boolean isForceStopRelated(AccessibilityNodeInfo n) {
        if (n == null) return false;
        CharSequence text = n.getText();
        if (text != null && text.toString().toLowerCase().contains("force")) return true;
        CharSequence desc = n.getContentDescription();
        return desc != null && desc.toString().toLowerCase().contains("force");
    }

    private AccessibilityNodeInfo findAnyDialogConfirmNode(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo node = findDialogConfirmNode(root);
        if (node != null) return node;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo win : windows) {
                        if (win == null) continue;
                        AccessibilityNodeInfo wRoot = win.getRoot();
                        if (wRoot != null) {
                            AccessibilityNodeInfo n = findDialogConfirmNode(wRoot);
                            if (n != null) return n;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private AccessibilityNodeInfo findDialogConfirmNode(AccessibilityNodeInfo root) {
        if (root == null) return null;

        // 1. Android standard dialog positive button
        String[] okIds = new String[]{
            "android:id/button1",
            "com.android.settings:id/button1",
            "android:id/ok"
        };
        for (String id : okIds) {
            List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId(id);
            if (byId != null && !byId.isEmpty()) {
                for (AccessibilityNodeInfo n : byId) {
                    if (n.isEnabled()) return n;
                }
            }
        }

        // 2. Dialog confirmation buttons with text OK / Confirm
        String[] okTexts = new String[]{"OK", "Ok", "ok", "Aceptar", "Confirmer", "Bestätigen", "Yes", "Si"};
        for (String ok : okTexts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(ok);
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) {
                    CharSequence text = n.getText();
                    if (text != null && text.toString().trim().equalsIgnoreCase(ok)) {
                        if (text.toString().trim().endsWith("?")) continue;
                        AccessibilityNodeInfo clickable = findClickableParent(n);
                        if (clickable != null && clickable.isEnabled()) {
                            return clickable;
                        }
                        if (n.isEnabled()) return n;
                    }
                }
            }
        }

        // 3. Recursive search into Compose dialogs
        AccessibilityNodeInfo rec = findConfirmRecursive(root, okTexts, 0);
        if (rec != null) return rec;

        // 4. For custom OEM dialogs where the confirm button says "Force stop",
        // only match if we've clicked Force stop AND this dialog window contains a Cancel button!
        if (mClickedForceStop && hasCancelButton(root)) {
            String[] fsDialogTexts = new String[]{"Force stop", "FORCE STOP"};
            return findForceStopRecursive(root, fsDialogTexts, 0);
        }
        return null;
    }

    private AccessibilityNodeInfo findConfirmRecursive(AccessibilityNodeInfo node, String[] okTexts, int depth) {
        if (node == null || depth > 12) return null;

        CharSequence text = node.getText();
        if (text != null) {
            String s = text.toString().trim();
            for (String ok : okTexts) {
                if (ok.equalsIgnoreCase(s)) {
                    Log.i(TAG, "findConfirmRecursive matched '" + ok + "' at depth " + depth);
                    AccessibilityNodeInfo clickable = findClickableParent(node);
                    return (clickable != null && clickable.isEnabled()) ? clickable : node;
                }
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findConfirmRecursive(child, okTexts, depth + 1);
                if (found != null) {
                    return found;
                }
                child.recycle();
            }
        }
        return null;
    }

    private boolean hasCancelButton(AccessibilityNodeInfo root) {
        if (root == null) return false;
        String[] cancelTexts = new String[]{"Cancel", "Cancelar", "Annuler", "Abbrechen", "Annulla"};
        for (String c : cancelTexts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(c);
            if (nodes != null && !nodes.isEmpty()) return true;
        }
        List<AccessibilityNodeInfo> cancelById = root.findAccessibilityNodeInfosByViewId("android:id/button2");
        return cancelById != null && !cancelById.isEmpty();
    }

    private AccessibilityNodeInfo findAnyStorageNode(AccessibilityNodeInfo root) {
        if (root != null) {
            AccessibilityNodeInfo n = findStorageNode(root);
            if (n != null) return n;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo win : windows) {
                        if (win == null) continue;
                        AccessibilityNodeInfo wRoot = win.getRoot();
                        if (wRoot != null) {
                            AccessibilityNodeInfo n = findStorageNode(wRoot);
                            if (n != null) return n;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private AccessibilityNodeInfo findStorageNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        String[] texts = new String[]{"Storage & cache", "Storage & Cache", "Storage and cache", "Storage", "Almacenamiento", "Speicher", "Stockage"};
        for (String txt : texts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(txt);
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) {
                    CharSequence text = n.getText();
                    if (text != null && text.toString().toLowerCase().contains("storage")) {
                        AccessibilityNodeInfo clickable = findClickableParent(n);
                        return clickable != null ? clickable : n;
                    }
                }
            }
        }
        // Fallback: Full recursive traversal into Compose virtual tree
        return findStorageRecursive(root, texts, 0);
    }

    private AccessibilityNodeInfo findStorageRecursive(AccessibilityNodeInfo node, String[] keywords, int depth) {
        if (node == null || depth > 12) return null;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        for (String kw : keywords) {
            boolean match = (text != null && text.toString().toLowerCase().contains(kw.toLowerCase())) ||
                            (desc != null && desc.toString().toLowerCase().contains(kw.toLowerCase()));
            if (match) {
                Log.i(TAG, "findStorageRecursive matched keyword '" + kw + "' at depth " + depth + " on " + node.getClassName());
                AccessibilityNodeInfo clickable = findClickableParent(node);
                return clickable != null ? clickable : node;
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findStorageRecursive(child, keywords, depth + 1);
                if (found != null) return found;
                child.recycle();
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findAnyClearCacheNode(AccessibilityNodeInfo root) {
        if (root != null) {
            AccessibilityNodeInfo n = findClearCacheNode(root);
            if (n != null) return n;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo win : windows) {
                        if (win == null) continue;
                        AccessibilityNodeInfo wRoot = win.getRoot();
                        if (wRoot != null) {
                            AccessibilityNodeInfo n = findClearCacheNode(wRoot);
                            if (n != null) return n;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private AccessibilityNodeInfo findClearCacheNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        String[] ids = new String[]{
            "com.android.settings:id/button2",
            "com.android.settings:id/clear_cache_button",
            "android:id/button2"
        };
        for (String id : ids) {
            List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId(id);
            if (byId != null && !byId.isEmpty()) {
                for (AccessibilityNodeInfo n : byId) {
                    if (isCacheRelated(n)) {
                        AccessibilityNodeInfo clickable = findClickableParent(n);
                        return clickable != null ? clickable : n;
                    }
                }
            }
        }
        String[] texts = new String[]{"Clear cache", "CLEAR CACHE", "Clear Cache", "Borrar caché", "Vider le cache", "Cache leeren", "Svuota cache"};
        for (String txt : texts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(txt);
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) {
                    CharSequence text = n.getText();
                    if (text != null && text.toString().trim().equalsIgnoreCase(txt)) {
                        AccessibilityNodeInfo clickable = findClickableParent(n);
                        return clickable != null ? clickable : n;
                    }
                }
            }
        }
        // Fallback: Full recursive traversal into Compose virtual tree
        return findClearCacheRecursive(root, texts, 0);
    }

    private AccessibilityNodeInfo findClearCacheRecursive(AccessibilityNodeInfo node, String[] texts, int depth) {
        if (node == null || depth > 12) return null;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        for (String txt : texts) {
            boolean match = (text != null && text.toString().trim().equalsIgnoreCase(txt)) ||
                            (desc != null && desc.toString().trim().equalsIgnoreCase(txt));
            if (match) {
                Log.i(TAG, "findClearCacheRecursive matched '" + txt + "' at depth " + depth + " on " + node.getClassName());
                AccessibilityNodeInfo clickable = findClickableParent(node);
                return clickable != null ? clickable : node;
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findClearCacheRecursive(child, texts, depth + 1);
                if (found != null) return found;
                child.recycle();
            }
        }
        return null;
    }

    private boolean isCacheRelated(AccessibilityNodeInfo n) {
        if (n == null) return false;
        CharSequence text = n.getText();
        if (text != null && text.toString().toLowerCase().contains("cache")) return true;
        CharSequence desc = n.getContentDescription();
        return desc != null && desc.toString().toLowerCase().contains("cache");
    }

    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo curr = n;
        while (curr != null) {
            if (curr.isClickable()) return curr;
            curr = curr.getParent();
        }
        return n;
    }

    private void performClick(AccessibilityNodeInfo node) {
        if (node == null) return;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        boolean clicked = false;
        // 1. Direct click
        if (node.isClickable()) {
            clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.i(TAG, "performClick direct ACTION_CLICK: " + clicked);
        }

        // 2. Clickable parent
        if (!clicked) {
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && parent.isClickable()) {
                clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.i(TAG, "performClick parent ACTION_CLICK: " + clicked);
            }
        }

        // 3. Clickable children
        if (!clicked) {
            try {
                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        if (child.isClickable()) {
                            clicked = child.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.i(TAG, "performClick child ACTION_CLICK: " + clicked);
                            child.recycle();
                            if (clicked) break;
                        } else {
                            child.recycle();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. Also dispatch hardware tap gesture at coordinates to guarantee Compose click
        if (bounds.width() > 0 && bounds.height() > 0) {
            Log.i(TAG, "performClick dispatching hardware tap gesture at (" + bounds.centerX() + "," + bounds.centerY() + ")");
            dispatchTapGesture(bounds.centerX(), bounds.centerY());
        }
    }

    private void dispatchTapGesture(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                GestureDescription.Builder builder = new GestureDescription.Builder();
                Path path = new Path();
                path.moveTo(x, y);
                path.lineTo(x, y); // REQUIRED: makes path non-empty
                builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));

                final boolean wasOverlayActive = (mFullscreenView != null && mFullscreenParams != null);
                if (wasOverlayActive && mWindowManager != null) {
                    try {
                        mFullscreenParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                        mWindowManager.updateViewLayout(mFullscreenView, mFullscreenParams);
                    } catch (Exception ignored) {}
                }

                boolean sent = dispatchGesture(builder.build(), new GestureResultCallbackHandler(this, wasOverlayActive, x, y), mHandler);
                Log.i(TAG, "dispatchGesture invoked: " + sent + " at (" + x + "," + y + ")");

                mHandler.postDelayed(() -> restoreOverlayTouchability(wasOverlayActive), 120);
            } catch (Exception e) {
                Log.w(TAG, "dispatchTapGesture error at " + x + "," + y, e);
            }
        }
    }

    private void restoreOverlayTouchability(boolean wasActive) {
        if (wasActive && mFullscreenView != null && mFullscreenParams != null && mWindowManager != null) {
            try {
                mFullscreenParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                mWindowManager.updateViewLayout(mFullscreenView, mFullscreenParams);
            } catch (Exception ignored) {}
        }
    }

    private void dispatchSwipeGesture(int startX, int startY, int endX, int endY) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                GestureDescription.Builder builder = new GestureDescription.Builder();
                Path path = new Path();
                path.moveTo(startX, startY);
                path.lineTo(endX, endY);
                builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 200));

                final boolean wasOverlayActive = (mFullscreenView != null && mFullscreenParams != null);
                if (wasOverlayActive && mWindowManager != null) {
                    try {
                        mFullscreenParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                        mWindowManager.updateViewLayout(mFullscreenView, mFullscreenParams);
                    } catch (Exception ignored) {}
                }

                dispatchGesture(builder.build(), new GestureResultCallbackHandler(this, wasOverlayActive, startX, startY), mHandler);
                mHandler.postDelayed(() -> restoreOverlayTouchability(wasOverlayActive), 250);
            } catch (Exception e) {
                Log.w(TAG, "dispatchSwipeGesture error", e);
            }
        }
    }

    private boolean scrollForward(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isScrollable()) {
            boolean success = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            if (success) {
                Log.i(TAG, "scrollForward succeeded on " + node.getClassName());
                return true;
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean success = scrollForward(child);
                child.recycle();
                if (success) return true;
            }
        }
        return false;
    }

    // =========================================================================
    // UI OVERLAYS: Baxa-style Fullscreen Closing Screen & Floating Shortcut Pill
    // =========================================================================

    private void showFullscreenClosingWindow() {
        if (mFullscreenView != null) return;
        try {
            if (mWindowManager == null) {
                mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            }
            Context themedContext = new ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_NoActionBar);
            LayoutInflater inflater = LayoutInflater.from(themedContext);
            mFullscreenView = inflater.inflate(R.layout.fullscreen_closing_apps, null);

            int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            mFullscreenParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                    PixelFormat.TRANSLUCENT
            );

            mPbClosingCircle = mFullscreenView.findViewById(R.id.pb_closing_circle);
            mTvClosingPercent = mFullscreenView.findViewById(R.id.tv_closing_percent);
            mTvClosingAppName = mFullscreenView.findViewById(R.id.tv_closing_app_name);
            mTvClosingCount = mFullscreenView.findViewById(R.id.tv_closing_count);
            mPbClosingHorizontal = mFullscreenView.findViewById(R.id.pb_closing_horizontal);
            mBtnClosingStop = mFullscreenView.findViewById(R.id.btn_closing_stop);
            mBtnClosingMinimize = mFullscreenView.findViewById(R.id.btn_closing_minimize);

            if (mBtnClosingStop != null) {
                mBtnClosingStop.setOnClickListener(v -> stopCleaning());
            }

            if (mBtnClosingMinimize != null) {
                mBtnClosingMinimize.setOnClickListener(v -> {
                    mIsMinimized = true;
                    hideFullscreenWindow();
                    showFloatingWindow();
                    updateProgressUI(mCurrentPackage, mCurrentIndex, mTotalToClean);
                });
            }

            try {
                mWindowManager.addView(mFullscreenView, mFullscreenParams);
                Log.i(TAG, "Fullscreen Closing overlay added successfully");
            } catch (Exception e1) {
                Log.w(TAG, "Accessibility overlay failed, trying APPLICATION_OVERLAY", e1);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mFullscreenParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                }
                mWindowManager.addView(mFullscreenView, mFullscreenParams);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Fullscreen Closing window", e);
        }
    }

    private void hideFullscreenWindow() {
        if (mFullscreenView != null && mWindowManager != null) {
            try {
                mWindowManager.removeView(mFullscreenView);
            } catch (Exception ignored) {}
            mFullscreenView = null;
        }
    }

    private void showFloatingWindow() {
        if (mFloatingView != null) return;
        try {
            if (mWindowManager == null) {
                mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            }
            Context themedContext = new ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_NoActionBar);
            LayoutInflater inflater = LayoutInflater.from(themedContext);
            mFloatingView = inflater.inflate(R.layout.floating_cleaner_control, null);

            int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            mFloatingParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );

            mFloatingParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            mFloatingParams.y = 120;

            mTvFloatIcon = mFloatingView.findViewById(R.id.tv_float_icon);
            if (mTvFloatIcon != null) {
                mTvFloatIcon.setText(mCurrentMode == MODE_CLOSE_APPS ? "🛑" : "🧹");
            }
            mTvFloatCount = mFloatingView.findViewById(R.id.tv_float_count);
            mTvFloatAppName = mFloatingView.findViewById(R.id.tv_float_app_name);
            mBtnFloatPauseResume = mFloatingView.findViewById(R.id.btn_float_pause_resume);
            mBtnFloatStop = mFloatingView.findViewById(R.id.btn_float_stop);

            if (mBtnFloatPauseResume != null) {
                mBtnFloatPauseResume.setOnClickListener(v -> {
                    if (mIsPaused) {
                        resumeCleaning();
                    } else {
                        pauseCleaning();
                    }
                });
            }

            if (mBtnFloatStop != null) {
                mBtnFloatStop.setOnClickListener(v -> stopCleaning());
            }

            // Draggable with snap-to-edge docking & tap-to-expand
            mFloatingView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;
                private boolean isDragging = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = mFloatingParams.x;
                            initialY = mFloatingParams.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            isDragging = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - initialTouchX;
                            float dy = event.getRawY() - initialTouchY;
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isDragging = true;
                            }
                            mFloatingParams.x = initialX + (int) dx;
                            mFloatingParams.y = initialY + (int) dy;
                            if (mWindowManager != null && mFloatingView != null) {
                                try {
                                    mWindowManager.updateViewLayout(mFloatingView, mFloatingParams);
                                } catch (Exception ignored) {}
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (!isDragging) {
                                // Tap on floating pill restores Baxa fullscreen overlay!
                                mIsMinimized = false;
                                hideFloatingWindow();
                                showFullscreenClosingWindow();
                                updateProgressUI(mCurrentPackage, mCurrentIndex, mTotalToClean);
                                return true;
                            }
                            if (mWindowManager != null && mFloatingView != null) {
                                try {
                                    android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                                    int screenWidth = dm.widthPixels;
                                    int viewWidth = mFloatingView.getWidth();
                                    if (mFloatingParams.x + viewWidth / 2 < screenWidth / 2) {
                                        mFloatingParams.x = 24;
                                    } else {
                                        mFloatingParams.x = screenWidth - viewWidth - 24;
                                    }
                                    mWindowManager.updateViewLayout(mFloatingView, mFloatingParams);
                                } catch (Exception ignored) {}
                            }
                            return true;
                    }
                    return false;
                }
            });

            try {
                mWindowManager.addView(mFloatingView, mFloatingParams);
                Log.i(TAG, "Floating shortcut pill added successfully");
            } catch (Exception e1) {
                Log.w(TAG, "Accessibility overlay failed, trying APPLICATION_OVERLAY", e1);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mFloatingParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                }
                mWindowManager.addView(mFloatingView, mFloatingParams);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Floating Shortcut window", e);
        }
    }

    private void hideFloatingWindow() {
        if (mFloatingView != null && mWindowManager != null) {
            try {
                mWindowManager.removeView(mFloatingView);
            } catch (Exception ignored) {}
            mFloatingView = null;
        }
    }

    private void hideAllOverlays() {
        hideFullscreenWindow();
        hideFloatingWindow();
    }

    private void updateProgressUI(String packageName, int current, int total) {
        mHandler.post(() -> {
            String appLabel = packageName != null ? packageName : "Finishing...";
            if (packageName != null) {
                try {
                    PackageManager pm = getPackageManager();
                    appLabel = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
                } catch (Exception ignored) {}
            }

            int percent = total > 0 ? (current * 100) / total : 0;

            // 1. Update Fullscreen Baxa UI
            if (mFullscreenView != null) {
                if (mTvClosingPercent != null) mTvClosingPercent.setText(String.valueOf(percent));
                if (mPbClosingCircle != null) mPbClosingCircle.setProgress(percent);
                if (mTvClosingAppName != null) mTvClosingAppName.setText(appLabel);
                if (mTvClosingCount != null) {
                    mTvClosingCount.setText(current + " (done) / " + total + " (total)");
                }
                if (mPbClosingHorizontal != null) mPbClosingHorizontal.setProgress(percent);
            }

            // 2. Update Floating Shortcut Pill UI
            if (mFloatingView != null) {
                if (mTvFloatCount != null) {
                    mTvFloatCount.setText(current + "(done)/" + total + "(total)");
                }
                if (mTvFloatIcon != null) {
                    mTvFloatIcon.setText(mCurrentMode == MODE_CLOSE_APPS ? "🛑" : "🧹");
                }
                if (mTvFloatAppName != null) {
                    mTvFloatAppName.setText(appLabel + (mCurrentMode == MODE_CLOSE_APPS ? " (Force stop)" : ""));
                }
            }
        });
    }

    private void bringMainActivityToFront() {
        try {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(mainIntent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onInterrupt() {
        mIsCleaning = false;
        mIsPaused = false;
        mPackageQueue.clear();
        hideAllOverlays();
    }

    private static class GestureResultCallbackHandler extends AccessibilityService.GestureResultCallback {
        private final AppCacheCleanerAccessibilityService mService;
        private final boolean mWasOverlayActive;
        private final int mX;
        private final int mY;

        public GestureResultCallbackHandler(AppCacheCleanerAccessibilityService service, boolean wasOverlayActive, int x, int y) {
            this.mService = service;
            this.mWasOverlayActive = wasOverlayActive;
            this.mX = x;
            this.mY = y;
        }

        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            Log.i(TAG, new StringBuilder("Gesture completed at (").append(mX).append(",").append(mY).append(")").toString());
            if (mService != null) {
                mService.restoreOverlayTouchability(mWasOverlayActive);
            }
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            Log.w(TAG, new StringBuilder("Gesture cancelled at (").append(mX).append(",").append(mY).append(")").toString());
            if (mService != null) {
                mService.restoreOverlayTouchability(mWasOverlayActive);
            }
        }
    }
}
