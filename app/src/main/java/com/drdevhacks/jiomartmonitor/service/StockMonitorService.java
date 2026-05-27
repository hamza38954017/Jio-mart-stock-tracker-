package com.drdevhacks.jiomartmonitor.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import com.drdevhacks.jiomartmonitor.model.Product;
import com.drdevhacks.jiomartmonitor.model.StockResult;
import com.drdevhacks.jiomartmonitor.util.JioMartApiHelper;
import com.drdevhacks.jiomartmonitor.util.NotificationHelper;
import com.drdevhacks.jiomartmonitor.util.ProductStorage;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StockMonitorService extends Service {

    public static final String TAG                = "StockMonitorService";
    public static final String ACTION_UPDATE      = "com.drdevhacks.jiomartmonitor.STOCK_UPDATE";
    public static final String ACTION_CHECKING    = "com.drdevhacks.jiomartmonitor.STOCK_CHECKING";
    public static final String ACTION_MANUAL_CHECK = "com.drdevhacks.jiomartmonitor.MANUAL_CHECK";
    public static final long   INTERVAL_MS        = 5 * 60 * 1000L;  // 5 minutes

    private Handler         handler;
    private ExecutorService executor;
    private boolean         running = false;

    private final Runnable checkRunnable = new Runnable() {
        @Override public void run() {
            if (!running) return;
            sendBroadcast(new Intent(ACTION_CHECKING));
            executor.submit(() -> performCheck());
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
        handler  = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NotificationHelper.ID_SERVICE,
            NotificationHelper.buildServiceNotification(this, "Monitoring…"));

        boolean isManual = intent != null
            && ACTION_MANUAL_CHECK.equals(intent.getAction());

        if (!running) {
            running = true;
            handler.post(checkRunnable);   // fire immediately, then every 5 min
        } else if (isManual) {
            // Already running — cancel pending tick and run a check right now,
            // then reschedule the 5-min interval from this moment.
            handler.removeCallbacks(checkRunnable);
            handler.post(checkRunnable);
        }
        return START_STICKY;               // auto-restart if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        handler.removeCallbacks(checkRunnable);
        executor.shutdownNow();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Core polling logic ────────────────────────────────────────────────────
    private void performCheck() {
        Log.d(TAG, "Starting stock check…");
        List<Product> products = ProductStorage.getAll(this);

        int inStockCount  = 0;
        int outStockCount = 0;

        for (Product product : products) {
            if (!product.isEnabled()) continue;

            StockResult prev   = ProductStorage.getResult(this, product.getId());
            StockResult result = JioMartApiHelper.check(product);

            // Carry over previous state for comparison
            if (prev != null) {
                result.setPrevAvailable(prev.isAvailable());
                result.setPrevQuantity(prev.getTotalQuantity());
            }

            boolean firstCheck = (prev == null);

            if (!firstCheck) {
                boolean wasIn  = prev.isAvailable();
                boolean nowIn  = result.isAvailable();
                int     wasQty = prev.getTotalQuantity();
                int     nowQty = result.getTotalQuantity();

                if (!wasIn && nowIn) {
                    // ── OUT-OF-STOCK → IN-STOCK: trigger alarm + notification ──
                    NotificationHelper.notifyInStock(this, product, result);
                    triggerAlarm(product, result);
                    Log.d(TAG, "🚨 BACK IN STOCK (alarm): " + product.getName());

                } else if (wasIn && !nowIn) {
                    // In-stock → out-of-stock: silent notification only, no alarm
                    NotificationHelper.notifyStockChange(this, product, result,
                        product.getName() + " is now out of stock.");
                    Log.d(TAG, "❌ OUT OF STOCK: " + product.getName());

                } else if (wasIn && nowIn && nowQty < wasQty && wasQty > 0) {
                    // Stock reduced (still available): silent notification, no alarm
                    String desc = "Stock reduced: " + wasQty + " → " + nowQty
                        + " [" + wasQty + "−" + (wasQty - nowQty) + "=" + nowQty + " remaining]";
                    NotificationHelper.notifyStockChange(this, product, result, desc);

                } else if (wasIn && nowIn && nowQty > wasQty) {
                    // Stock increased (still available): silent notification, no alarm
                    String desc = "Stock increased: " + wasQty + " → " + nowQty
                        + " [" + wasQty + "+" + (nowQty - wasQty) + "=" + nowQty + " remaining]";
                    NotificationHelper.notifyStockChange(this, product, result, desc);
                }
                // Else: state unchanged → completely silent (no notification)

            } else {
                // First check ever — just save the result, no alarm regardless of status
                Log.d(TAG, "First check for: " + product.getName()
                    + " | in stock: " + result.isAvailable());
            }

            if (result.isAvailable()) inStockCount++;
            else outStockCount++;

            ProductStorage.saveResult(this, result);
        }

        long now = System.currentTimeMillis();
        ProductStorage.setLastCheckedAt(this, now);

        // ── Update foreground notification ────────────────────────────────────
        String fgStatus;
        if (inStockCount > 0) {
            fgStatus = "✅ " + inStockCount + " in stock  •  ❌ " + outStockCount + " out of stock";
        } else {
            fgStatus = "⏳ Monitoring • next check in 5 min";
        }
        startForeground(NotificationHelper.ID_SERVICE,
            NotificationHelper.buildServiceNotification(this, fgStatus));

        // ── Summary notification after every periodic check ───────────────────
        // Shows total counts — no alarm, no sound. Replaces the previous summary.
        NotificationHelper.notifySummary(this, inStockCount, outStockCount);

        // Broadcast update to UI
        sendBroadcast(new Intent(ACTION_UPDATE));
        Log.d(TAG, "Check complete. In stock: " + inStockCount
            + ", Out of stock: " + outStockCount);
    }

    private void triggerAlarm(Product product, StockResult result) {
        Intent i = new Intent(this, com.drdevhacks.jiomartmonitor.AlarmActivity.class);
        i.putExtra("product_id",    product.getId());
        i.putExtra("product_name",  product.getName());
        i.putExtra("product_url",   product.getUrl());
        i.putExtra("product_emoji", product.getEmoji());
        i.putExtra("location_name", product.getLocationName());
        i.putExtra("quantity",      result.getTotalQuantity());
        i.putExtra("price",         result.getPrice());
        i.putExtra("store_ids",     result.getAvailableStoreIds());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }
}
