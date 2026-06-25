package com.drdevhacks.jiomartmonitor.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

public class ExpiryManager {
    private static final String PREF         = "jm_expiry";
    private static final String KEY_FIRST    = "first_launch";
    private static final String SETTINGS_KEY = "drdevhacks_jm_v1_trial";   // persists in system DB
    private static final long   TRIAL_MS     = 7L * 24 * 60 * 60 * 1000;

    /**
     * Call on first launch.
     * Writes the install timestamp to Settings.System (survives reinstall) AND
     * SharedPreferences (fast local fallback).
     */
    public static void init(Context ctx) {
        long now = System.currentTimeMillis();

        // ── Settings.System branch (persists through app reinstall) ──────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(ctx)) {
            String existing = Settings.System.getString(ctx.getContentResolver(), SETTINGS_KEY);
            if (existing == null) {
                // First ever install on this device — record now
                Settings.System.putString(ctx.getContentResolver(), SETTINGS_KEY,
                    String.valueOf(now));
            }
            // Sync Settings.System value back into SharedPreferences so both match
            long sysTime = parseSystemTime(ctx);
            SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            if (!p.contains(KEY_FIRST)) {
                p.edit().putLong(KEY_FIRST, sysTime).apply();
            }
        } else {
            // No WRITE_SETTINGS yet — use SharedPreferences only
            SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            if (!p.contains(KEY_FIRST)) {
                p.edit().putLong(KEY_FIRST, now).apply();
            }
        }
    }

    /** True once the 7-day trial window has passed. */
    public static boolean isExpired(Context ctx) {
        return System.currentTimeMillis() - getFirstLaunch(ctx) > TRIAL_MS;
    }

    /** Days remaining (0 = expires today / already expired). */
    public static long getDaysRemaining(Context ctx) {
        long elapsed = System.currentTimeMillis() - getFirstLaunch(ctx);
        long remain  = TRIAL_MS - elapsed;
        return Math.max(0, remain / (24 * 60 * 60 * 1000L));
    }

    /** True if WRITE_SETTINGS has not been granted yet (anti-reset lock not active). */
    public static boolean needsWriteSettings(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        return !Settings.System.canWrite(ctx);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Returns the canonical first-launch timestamp.
     * Priority: Settings.System (persistent) > SharedPreferences (fallback).
     */
    private static long getFirstLaunch(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(ctx)) {
            long sysTime = parseSystemTime(ctx);
            if (sysTime > 0) return sysTime;
        }
        // Fallback — may be reset on reinstall if WRITE_SETTINGS was never granted
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(KEY_FIRST, System.currentTimeMillis());
    }

    private static long parseSystemTime(Context ctx) {
        String val = Settings.System.getString(ctx.getContentResolver(), SETTINGS_KEY);
        if (val == null) return 0L;
        try { return Long.parseLong(val); } catch (Exception ignored) { return 0L; }
    }
}
