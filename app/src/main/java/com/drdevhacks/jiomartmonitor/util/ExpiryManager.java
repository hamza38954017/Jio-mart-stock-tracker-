package com.drdevhacks.jiomartmonitor.util;

import android.content.Context;
import android.content.SharedPreferences;

public class ExpiryManager {
    private static final String PREF       = "jm_expiry";
    private static final String KEY_FIRST  = "first_launch";
    private static final long   TRIAL_MS   = 7L * 24 * 60 * 60 * 1000; // 7 days

    /** Call on first launch. Records timestamp only once. */
    public static void init(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!p.contains(KEY_FIRST)) {
            p.edit().putLong(KEY_FIRST, System.currentTimeMillis()).apply();
        }
    }

    public static boolean isExpired(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long first = p.getLong(KEY_FIRST, System.currentTimeMillis());
        return System.currentTimeMillis() - first > TRIAL_MS;
    }

    public static long getDaysRemaining(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long first   = p.getLong(KEY_FIRST, System.currentTimeMillis());
        long elapsed = System.currentTimeMillis() - first;
        long remain  = TRIAL_MS - elapsed;
        return Math.max(0, remain / (24 * 60 * 60 * 1000L));
    }
}
