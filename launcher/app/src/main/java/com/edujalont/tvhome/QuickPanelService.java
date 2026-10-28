package com.edujalont.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Chromecast with Google TV, two jobs:
 * <ul>
 * <li>Opens the system's own quick settings panel for a double press of Home (MainActivity). An app
 * cannot start that panel itself: on Google TV it belongs to the system's notification handler and is
 * guarded by a system permission. performGlobalAction asks SystemUI to open it, the same path the
 * system uses.</li>
 * <li>Keeps Google TV Home off screen: Google TV has no setting for another home screen and may take
 * the default home back (an update, a reset). Only then, when its home activity comes to the front, this
 * launcher replaces it at once and offers to become the default home again. Its quick settings panel and
 * every other window stay untouched, so Google TV Home can stay enabled and keep serving that panel.</li>
 * </ul>
 * Window changes of Google TV Home only (res/xml/quick_panel_service.xml): no keys, no window content.
 * Opt-in: only bound once enabled in Accessibility settings or with
 * `settings put secure enabled_accessibility_services` over adb.
 */
public class QuickPanelService extends AccessibilityService {

    static final String GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx";
    /** One appearance of Google TV Home can report several window changes: one replacement for them. */
    private static final long DUPLICATE_MS = 250;
    /** Re-read Google TV Home's activities at most this often when an unknown one shows (app update). */
    private static final long REFRESH_MS = 30_000;
    /** Upper bound of the hands-off time after opening the panel, in case Home never regains focus. */
    private static final long PANEL_MAX_MS = 10 * 60_000L;

    private static volatile QuickPanelService instance;
    /**
     * While the quick settings panel we asked for is open, Google TV Home is left alone: the panel is
     * Google TV Home's own, and on some builds it may be hosted by its home activity. Cleared when this
     * launcher gets the window focus back (MainActivity.onWindowFocusChanged).
     */
    private static volatile long panelOpenedAt;
    /** Google TV Home's HOME activities (aliases and their targets), read from the package manager. */
    private final Set<String> stockHomeActivities = new HashSet<>();
    private long lastRefresh, lastReplace;

    /** True when the service is enabled and SystemUI accepted the request. */
    static boolean openPanel() {
        QuickPanelService s = instance;
        if (s == null) return false;
        panelOpenedAt = SystemClock.elapsedRealtime();
        boolean ok = s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                || s.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
        if (!ok) panelOpenedAt = 0;
        return ok;
    }

    /** Home has the focus again: the panel is closed. Ignores the focus bounce of the first second. */
    static void homeFocused() {
        long opened = panelOpenedAt;
        if (opened != 0 && SystemClock.elapsedRealtime() - opened > 1000) panelOpenedAt = 0;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        refreshHomeActivities();
    }

    private void refreshHomeActivities() {
        lastRefresh = SystemClock.elapsedRealtime();
        stockHomeActivities.clear();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, 0)) {
            ActivityInfo a = ri.activityInfo;
            if (a == null || !GOOGLE_TV_HOME.equals(a.packageName)) continue;
            stockHomeActivities.add(a.name);
            if (a.targetActivity != null) stockHomeActivities.add(a.targetActivity);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!GOOGLE_TV_HOME.equals(String.valueOf(event.getPackageName()))) return;
        CharSequence cls = event.getClassName();
        if (cls == null) return;
        long now = SystemClock.elapsedRealtime();
        if (!stockHomeActivities.contains(cls.toString())) {
            // Unknown window of Google TV Home: usually the panel or a dialog. After an update its home
            // activity may have moved, so look again now and then; still unknown means leave it alone.
            if (now - lastRefresh < REFRESH_MS) return;
            refreshHomeActivities();
            if (!stockHomeActivities.contains(cls.toString())) return;
        }
        // While this launcher is the default home, Home and boot already come here: Google TV Home on
        // screen is then its quick settings panel (held Home) or something the viewer opened, never a
        // lost home screen. Only step in once Google TV has taken the default home back.
        if (isDefaultHome()) return;
        long opened = panelOpenedAt;
        if (opened != 0 && now - opened < PANEL_MAX_MS) return;
        if (now - lastReplace < DUPLICATE_MS) return;
        lastReplace = now;
        // As a Home intent, so MainActivity counts it as a Home press (double press, recovery triple);
        // the extra lets it offer to make this launcher the default home again. No transition: the
        // shorter Google TV Home is on screen, the less of it flashes.
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setClass(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_HOME_LOST, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION),
                    ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        } catch (Exception ignored) {
        }
    }

    /** What the Home button opens now: true when that is this launcher. */
    private boolean isDefaultHome() {
        ResolveInfo ri = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    @Override
    public void onInterrupt() {
    }
}
