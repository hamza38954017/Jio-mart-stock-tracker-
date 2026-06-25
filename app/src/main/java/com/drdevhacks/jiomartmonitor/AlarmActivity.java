package com.drdevhacks.jiomartmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class AlarmActivity extends AppCompatActivity {

    public static final String ACTION_DISMISS   = "com.drdevhacks.jiomartmonitor.DISMISS_ALARM";
    private static final long  AUTO_DISMISS_MS  = 5 * 60 * 1000L; // 5 minutes

    private MediaPlayer    mediaPlayer;
    private Vibrator       vibrator;
    private CountDownTimer countDown;
    private TextView       tvCountdown;

    private final BroadcastReceiver dismissReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (ACTION_DISMISS.equals(intent.getAction())) dismissAlarm();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        setContentView(R.layout.activity_alarm);

        // ── Extract extras ────────────────────────────────────────────────────
        String productName  = getIntent().getStringExtra("product_name");
        String productUrl   = getIntent().getStringExtra("product_url");
        String productEmoji = getIntent().getStringExtra("product_emoji");
        String locationName = getIntent().getStringExtra("location_name");
        int    quantity     = getIntent().getIntExtra("quantity", 0);
        String price        = getIntent().getStringExtra("price");
        String storeIds     = getIntent().getStringExtra("store_ids");

        // ── Bind views ────────────────────────────────────────────────────────
        tvCountdown = findViewById(R.id.tvCountdown);

        ((TextView) findViewById(R.id.tvAlarmEmoji)).setText(
            productEmoji != null ? productEmoji : "📦");
        ((TextView) findViewById(R.id.tvAlarmProduct)).setText(
            productName != null ? productName : "");
        ((TextView) findViewById(R.id.tvAlarmLocation)).setText(
            locationName != null ? locationName : "—");
        ((TextView) findViewById(R.id.tvAlarmStores)).setText(
            storeIds != null && !storeIds.isEmpty() ? storeIds : "—");
        ((TextView) findViewById(R.id.tvAlarmQty)).setText(quantity + " units");
        ((TextView) findViewById(R.id.tvAlarmPrice)).setText(
            price != null && !price.isEmpty() ? price : "N/A");

        // URL row — show product URL or "not available" note
        LinearLayout rowUrl = findViewById(R.id.rowProductUrl);
        TextView tvUrl = findViewById(R.id.tvAlarmUrl);
        if (productUrl != null && !productUrl.isEmpty()) {
            tvUrl.setText(productUrl);
            rowUrl.setOnClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(productUrl)));
            });
        } else {
            tvUrl.setText("URL not available");
            rowUrl.setClickable(false);
        }

        // ── Buy Now button — opens product URL in default browser ─────────────
        MaterialButton btnBuy = findViewById(R.id.btnAlarmBuy);
        if (productUrl != null && !productUrl.isEmpty()) {
            btnBuy.setOnClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(productUrl)));
                dismissAlarm();
            });
        } else {
            btnBuy.setEnabled(false);
            btnBuy.setAlpha(0.5f);
        }

        // ── Dismiss button ────────────────────────────────────────────────────
        findViewById(R.id.btnAlarmDismiss).setOnClickListener(v -> dismissAlarm());

        // ── Register dismiss receiver ─────────────────────────────────────────
        IntentFilter f = new IntentFilter(ACTION_DISMISS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dismissReceiver, f);
        }

        startAlarmSound();
        startVibration();
        startCountdown();
    }

    // ── Countdown ─────────────────────────────────────────────────────────────
    private void startCountdown() {
        countDown = new CountDownTimer(AUTO_DISMISS_MS, 1000) {
            @Override public void onTick(long remaining) {
                long sec = remaining / 1000, m = sec / 60, s = sec % 60;
                if (tvCountdown != null)
                    tvCountdown.setText(String.format("Auto-dismiss in  %02d:%02d", m, s));
            }
            @Override public void onFinish() { dismissAlarm(); }
        }.start();
    }

    // ── Alarm sound ───────────────────────────────────────────────────────────
    private void startAlarmSound() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null)
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception ignored) {}
    }

    // ── Vibration ─────────────────────────────────────────────────────────────
    private void startVibration() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;
        long[] pattern = {0, 500, 300, 500, 300, 500};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            vibrator.vibrate(pattern, 0);
        }
    }

    // ── Dismiss ───────────────────────────────────────────────────────────────
    private void dismissAlarm() {
        if (countDown != null)   { countDown.cancel(); countDown = null; }
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (vibrator != null) { vibrator.cancel(); vibrator = null; }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(dismissReceiver); } catch (Exception ignored) {}
        dismissAlarm();
    }
}
