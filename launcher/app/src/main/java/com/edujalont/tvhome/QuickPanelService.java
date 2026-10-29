package com.edujalont.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityOptions;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
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
 * <li>Keeps Google TV Home off screen. On Google TV (Chromecast, Android 14, measured) the Home button
 * and boot open Google TV Home's own home activity whatever app holds the HOME role or the preferred
 * home. So the Home key itself is taken here: a short press opens this launcher before Google TV Home
 * can show (no flash), a held press opens the quick settings panel as Google TV does. As a fallback (at
 * boot, or if the key ever bypasses the filter) whenever that home activity comes to the front this
 * launcher replaces it at once. Only that activity: the quick settings panel is another one
 * (`.dashboard.DashboardHandler`), and it and every other window stay untouched, so Google TV Home can
 * stay enabled and keep serving that panel.</li>
 * </ul>
 * On a plain Android TV (no Google TV Home, see isGoogleTv) the service does neither: it drops its key
 * filter and window events as soon as it is bound, and Home is left to the system's default home app.
 * Window changes of Google TV Home and key events (res/xml/quick_panel_service.xml); every key but Home
 * is handed straight back untouched; no window content. Opt-in: only bound once enabled in
 * Accessibility settings or with `settings put secure enabled_accessibility_services` over adb.
 */
public class QuickPanelService extends AccessibilityService {

    static final String GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx";
    /** One appearance of Google TV Home can report several window changes: one replacement for them. */
    private static final long DUPLICATE_MS = 250;
    /** Re-read Google TV Home's activities at most this often when an unknown one shows (app update). */
    private static final long REFRESH_MS = 30_000;

    private static volatile QuickPanelService instance;
    /** Google TV Home's HOME activities (aliases and their targets), read from the package manager. */
    private final Set<String> stockHomeActivities = new HashSet<>();
    private long lastRefresh, lastReplace;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Home is down and its long press already opened the panel: its key-up does nothing. */
    private boolean homeHeld;
    /** The key filter sees no key repeats, so a held Home is timed here. */
    private final Runnable homeLongPress = () -> {
        homeHeld = true;
        openPanel();
    };

    /** Home pressed twice within this window: the quick settings panel instead of a second Home. */
    static final long DOUBLE_HOME_MS = 500;
    /** Set on the Home intents sent from here, so MainActivity does not count the double press again. */
    static final String EXTRA_FROM_KEY = "com.edujalont.tvhome.FROM_HOME_KEY";
    private long lastHomeUp;
    private boolean googleTv;

    /** True when the service is enabled on Google TV and SystemUI accepted the request. */
    static boolean openPanel() {
        QuickPanelService s = instance;
        if (s == null || !s.googleTv) return false;
        return s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                || s.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }

    /**
     * Google TV (Chromecast with Google TV and Google TV sets): Google TV Home is installed, enabled or
     * not. On a plain Android TV (an Android TV 9 set, say) the system honours the default home app and
     * has no Google TV panel, so both jobs of this service are off there.
     */
    static boolean isGoogleTv(Context ctx) {
        try {
            ctx.getPackageManager().getApplicationInfo(GOOGLE_TV_HOME, PackageManager.MATCH_DISABLED_COMPONENTS);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        googleTv = isGoogleTv(this);
        if (!googleTv) {
            // Plain Android TV: take no keys and no window changes; Home stays the system's.
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.eventTypes = 0;
                info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
            }
            return;
        }
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
        handler.removeCallbacks(homeLongPress);
        return super.onUnbind(intent);
    }

    /**
     * Home only; every other key goes on untouched (false) without any work here. The double press is
     * told apart here, from the key itself: counted in MainActivity it missed the first press whenever
     * the launcher was not running yet (Home from inside an app starts it, no new intent).
     */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!googleTv || event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                homeHeld = false;
                handler.removeCallbacks(homeLongPress);
                handler.postDelayed(homeLongPress, ViewConfiguration.getLongPressTimeout());
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(homeLongPress);
            if (!homeHeld && !event.isCanceled()) {
                long now = SystemClock.elapsedRealtime();
                showingContent = false;
                lastContentAt = 0;
                if (now - lastHomeUp <= DOUBLE_HOME_MS) {
                    lastHomeUp = 0;
                    if (!openPanel()) goHome();
                } else {
                    lastHomeUp = now;
                    goHome();
                }
            }
            homeHeld = false;
        }
        return true;
    }

    /**
     * The Assistant's search results, and every tv.google.com link, open inside Google TV Home's home
     * activity (through its `.gateway.LauncherDeepLink`). Replacing that made the results vanish the
     * moment they showed. So after the Assistant or such a link, Google TV Home is left alone until the
     * viewer is back in this launcher (contentDone) or presses Home.
     */
    private static final long CONTENT_MS = 15_000;
    static final String ASSISTANT = "com.google.android.katniss";
    private long lastContentAt;
    private boolean showingContent;

    /** This launcher is in front again: Google TV Home goes back to being replaced. */
    static void contentDone() {
        QuickPanelService s = instance;
        if (s != null) s.showingContent = false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!googleTv) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = String.valueOf(event.getPackageName());
        CharSequence cls = event.getClassName();
        long now = SystemClock.elapsedRealtime();
        if (ASSISTANT.equals(pkg)) {
            lastContentAt = now;
            return;
        }
        if (!GOOGLE_TV_HOME.equals(pkg) || cls == null) return;
        if (cls.toString().contains(".gateway.")) {
            lastContentAt = now;
            return;
        }
        if (!stockHomeActivities.contains(cls.toString())) {
            // Unknown window of Google TV Home: usually the panel or a dialog. After an update its home
            // activity may have moved, so look again now and then; still unknown means leave it alone.
            if (now - lastRefresh < REFRESH_MS) return;
            refreshHomeActivities();
            if (!stockHomeActivities.contains(cls.toString())) return;
        }
        if (showingContent) return;
        if (lastContentAt != 0 && now - lastContentAt <= CONTENT_MS) {
            showingContent = true;              // results or a linked page: leave them on screen
            return;
        }
        if (now - lastReplace < DUPLICATE_MS) return;
        lastReplace = now;
        goHome();
    }

    /**
     * This launcher, as a Home intent so MainActivity counts it as a Home press (double press, recovery
     * triple). No transition: when it replaces Google TV Home, the less of that shows the better.
     */
    private void goHome() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setClass(this, MainActivity.class)
                    .putExtra(EXTRA_FROM_KEY, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION),
                    ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        } catch (Exception ignored) {
        }
    }

    /**
     * Whether this launcher holds the HOME role (Android 10+), else whether Home resolves to it. Used
     * only to tell the viewer the setup was lost: on Google TV holding the role does not make the Home
     * button open this launcher (the replacement above does), but losing it means the setup was undone.
     */
    static boolean isDefaultHome(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = ctx.getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME);
        }
        ResolveInfo ri = ctx.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && ctx.getPackageName().equals(ri.activityInfo.packageName);
    }

    @Override
    public void onInterrupt() {
    }
}
