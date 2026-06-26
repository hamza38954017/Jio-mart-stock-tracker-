package com.drdevhacks.jiomartmonitor.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

public class ExpiryManager {
    private static final String PREF         = "jm_expiry";
    private static final String KEY_FIRST    = "first_launch";
    private static final String SETTINGS_KEY = "drdevhacks_jm_v1_trial";
    private static final long   TRIAL_MS     = 7L * 24 * 60 * 60 * 1000;

    /**
     * Call once on app start.
     * Silently writes trial-start to Settings.System when the permission is
     * already granted (survives reinstall).  Falls back to SharedPreferences.
     * Never shows any dialog — no UI side-effects.
     */
    public static void init(Context ctx) {
        long now = System.currentTimeMillis();

        // ── Settings.System branch (survives reinstall) ───────────────────────
        if (canWriteSettings(ctx)) {
            String existing = Settings.System.getString(
                ctx.getContentResolver(), SETTINGS_KEY);
            if (existing == null) {
                // First ever install on this device — write now
                Settings.System.putString(
                    ctx.getContentResolver(), SETTINGS_KEY, String.valueOf(now));
            }
        }

        // ── SharedPreferences branch (always written as fallback) ─────────────
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!p.contains(KEY_FIRST)) {
            // Seed from Settings.System so both stay in sync after reinstall
            long seed = readSystemTime(ctx);
            p.edit().putLong(KEY_FIRST, seed > 0 ? seed : now).apply();
        }
    }

    /** True once the 7-day trial window has passed. */
    public static boolean isExpired(Context ctx) {
        return System.currentTimeMillis() - getFirstLaunch(ctx) > TRIAL_MS;
    }

    /** Whole days remaining (0 = expires today or already expired). */
    public static long getDaysRemaining(Context ctx) {
        long elapsed = System.currentTimeMillis() - getFirstLaunch(ctx);
        return Math.max(0, (TRIAL_MS - elapsed) / (24 * 60 * 60 * 1000L));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static long getFirstLaunch(Context ctx) {
        // Prefer Settings.System (persists through reinstall)
        long sysTime = readSystemTime(ctx);
        if (sysTime > 0) return sysTime;
        // Fallback to SharedPreferences
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(KEY_FIRST, System.currentTimeMillis());
    }

    private static long readSystemTime(Context ctx) {
        if (!canWriteSettings(ctx)) return 0L;
        String val = Settings.System.getString(
            ctx.getContentResolver(), SETTINGS_KEY);
        if (val == null) return 0L;
        try { return Long.parseLong(val); } catch (Exception e) { return 0L; }
    }

    private static boolean canWriteSettings(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return Settings.System.canWrite(ctx);
    }
}
