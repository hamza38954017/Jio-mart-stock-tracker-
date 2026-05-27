package com.drdevhacks.jiomartmonitor.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import com.drdevhacks.jiomartmonitor.AlarmActivity;
import com.drdevhacks.jiomartmonitor.MainActivity;
import com.drdevhacks.jiomartmonitor.R;
import com.drdevhacks.jiomartmonitor.model.Product;
import com.drdevhacks.jiomartmonitor.model.StockResult;

public class NotificationHelper {

    public static final String CH_SERVICE  = "jm_service";
    public static final String CH_STOCK    = "jm_stock";
    public static final int    ID_SERVICE  = 1;
    private static int         notifIdSeq  = 1000;

    public static void createChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);

        NotificationChannel svc = new NotificationChannel(
            CH_SERVICE, "Monitor Service", NotificationManager.IMPORTANCE_LOW);
        svc.setDescription("Keeps JioMart monitor running in background");
        svc.setShowBadge(false);
        nm.createNotificationChannel(svc);

        NotificationChannel stock = new NotificationChannel(
            CH_STOCK, "Stock Alerts", NotificationManager.IMPORTANCE_HIGH);
        stock.setDescription("Alerts when product stock changes");
        stock.enableVibration(true);
        stock.setShowBadge(true);
        nm.createNotificationChannel(stock);
    }

    public static Notification buildServiceNotification(Context ctx, String status) {
        Intent intent = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("JioMart Monitor Active")
            .setContentText(status)
            .setOngoing(true)
            .setContentIntent(pi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build();
    }

    public static void notifyInStock(Context ctx, Product p, StockResult r) {
        Intent alarmIntent = new Intent(ctx, AlarmActivity.class);
        alarmIntent.putExtra("product_id", p.getId());
        alarmIntent.putExtra("product_name", p.getName());
        alarmIntent.putExtra("product_url", p.getUrl());
        alarmIntent.putExtra("product_emoji", p.getEmoji());
        alarmIntent.putExtra("location_name", p.getLocationName());
        alarmIntent.putExtra("quantity", r.getTotalQuantity());
        alarmIntent.putExtra("price", r.getPrice());
        alarmIntent.putExtra("store_ids", r.getAvailableStoreIds());
        alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(ctx, notifIdSeq++, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_STOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("✅ " + p.getName() + " — IN STOCK!")
            .setContentText(p.getLocationName() + "  •  " + r.getTotalQuantity() + " units  •  " + r.getPrice())
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("📍 " + p.getLocationName() + "\n📦 " + r.getTotalQuantity()
                    + " units  •  💰 " + r.getPrice() + "\n🏪 " + r.getAvailableStoreIds()))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi);

        ctx.getSystemService(NotificationManager.class).notify(notifIdSeq++, b.build());
    }

    public static void notifyStockChange(Context ctx, Product p, StockResult r, String changeDesc) {
        Intent intent = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, notifIdSeq++, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = r.isAvailable()
            ? "🔼 Stock Update: " + p.getName()
            : "❌ Out of Stock: " + p.getName();

        new NotificationCompat.Builder(ctx, CH_STOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(changeDesc)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build();

        ctx.getSystemService(NotificationManager.class).notify(notifIdSeq++,
            new NotificationCompat.Builder(ctx, CH_STOCK)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(changeDesc)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build());
    }
}
