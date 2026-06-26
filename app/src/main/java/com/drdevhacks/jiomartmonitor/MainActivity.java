package com.drdevhacks.jiomartmonitor;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.drdevhacks.jiomartmonitor.adapter.ProductAdapter;
import com.drdevhacks.jiomartmonitor.model.Product;
import com.drdevhacks.jiomartmonitor.service.StockMonitorService;
import com.drdevhacks.jiomartmonitor.util.ExpiryManager;
import com.drdevhacks.jiomartmonitor.util.ProductStorage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RecyclerView       recyclerView;
    private ProductAdapter     adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView           tvLastChecked;
    private TextView           tvDaysLeft;
    private LinearLayout       emptyState;
    private List<Product>      products;

    // ── Guard flag: true only when all views + adapter are ready ─────────────
    // Without this, onResume() / updateUI() crash on null refs when onCreate()
    // exits early (expired trial) or before setContentView() is reached.
    private boolean isInitialized = false;

    private static final int REQ_NOTIF = 100;
    private static final int REQ_ADD   = 200;

    private boolean batteryDialogShowing = false;
    private boolean notifDialogShowing   = false;

    private final Handler  refreshTimeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable stopRefreshRunnable   = () -> {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
    };

    private final BroadcastReceiver stockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (!isInitialized) return;
            String action = intent.getAction();
            if (StockMonitorService.ACTION_UPDATE.equals(action)) {
                refreshTimeoutHandler.removeCallbacks(stopRefreshRunnable);
                updateUI();
                swipeRefresh.setRefreshing(false);
            } else if (StockMonitorService.ACTION_CHECKING.equals(action)) {
                swipeRefresh.setRefreshing(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // ── STEP 1: setContentView ALWAYS first ───────────────────────────────
        // This MUST come before any early return or dialog so that onResume()
        // never runs against a null view tree.
        setContentView(R.layout.activity_main);

        // ── STEP 2: bind views ────────────────────────────────────────────────
        tvLastChecked = findViewById(R.id.tvLastChecked);
        tvDaysLeft    = findViewById(R.id.tvDaysLeft);
        swipeRefresh  = findViewById(R.id.swipeRefresh);
        recyclerView  = findViewById(R.id.recyclerView);
        emptyState    = findViewById(R.id.emptyState);

        setSupportActionBar(findViewById(R.id.toolbar));

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.appBarLayout), (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, sys.top, 0, 0);
                return insets;
            });
        ViewCompat.setOnApplyWindowInsetsListener(
            swipeRefresh, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, 0, 0, sys.bottom);
                return insets;
            });

        // ── STEP 3: check trial (safe to early-return now; views exist) ───────
        ExpiryManager.init(this);
        if (ExpiryManager.isExpired(this)) {
            showExpiredDialog();
            return; // safe — setContentView already called; onResume guarded below
        }

        // ── STEP 4: finish UI setup ───────────────────────────────────────────
        long days = ExpiryManager.getDaysRemaining(this);
        tvDaysLeft.setText(days > 1 ? "Trial: " + days + " days left" : "⚠️ Trial expires today!");
        tvDaysLeft.setVisibility(View.VISIBLE);

        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.colorPrimary),
            ContextCompat.getColor(this, R.color.colorInStock));
        swipeRefresh.setOnRefreshListener(this::triggerManualRefresh);

        products = ProductStorage.getAll(this);
        adapter  = new ProductAdapter(this, products);
        adapter.setListener(new ProductAdapter.OnProductActionListener() {
            @Override public void onDelete(Product p) { confirmDelete(p); }
            @Override public void onEdit(Product p)   { openEdit(p); }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(false);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v ->
            startActivityForResult(new Intent(this, AddProductActivity.class), REQ_ADD));

        // ── STEP 5: mark ready, then start ───────────────────────────────────
        isInitialized = true;

        startMonitor();
        updateUI();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        if (!isInitialized) return; // guard: views not ready (expired / mid-init)

        checkAndRequestNotifPermission();
        checkAndRequestBatteryOptimization();

        IntentFilter filter = new IntentFilter();
        filter.addAction(StockMonitorService.ACTION_UPDATE);
        filter.addAction(StockMonitorService.ACTION_CHECKING);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stockReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stockReceiver, filter);
        }
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(stockReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (!isInitialized) return; // guard
        if (req == REQ_ADD && res == RESULT_OK) {
            products.clear();
            products.addAll(ProductStorage.getAll(this));
            adapter.notifyDataSetChanged();
            updateEmptyState();
            Toast.makeText(this, "Product saved!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            triggerManualRefresh(); return true;
        }
        if (item.getItemId() == R.id.action_developer) {
            showDeveloperDialog(); return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Notification permission ───────────────────────────────────────────────

    private void checkAndRequestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotifPermission();
            }
        }
    }

    private void requestNotifPermission() {
        if (notifDialogShowing) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_NOTIF) {
            boolean granted = grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                notifDialogShowing = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && ActivityCompat.shouldShowRequestPermissionRationale(
                               this, Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                        .setTitle("🔔 Notification Permission Required")
                        .setMessage("Notifications are required to alert you when a product comes back in stock.\n\nWithout this, you will miss restock alerts!")
                        .setCancelable(false)
                        .setPositiveButton("Allow", (d, w) -> {
                            notifDialogShowing = false;
                            requestNotifPermission();
                        })
                        .setNegativeButton("Ask Later", (d, w) -> notifDialogShowing = false)
                        .show();
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("🔔 Notification Permission Required")
                        .setMessage("Notification permission is permanently denied.\n\nPlease open App Settings → Notifications and enable it to receive stock alerts.")
                        .setCancelable(false)
                        .setPositiveButton("Open Settings", (d, w) -> {
                            notifDialogShowing = false;
                            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        })
                        .setNegativeButton("Ask Later", (d, w) -> notifDialogShowing = false)
                        .show();
                }
            }
        }
    }

    // ── Battery optimization ──────────────────────────────────────────────────

    private void checkAndRequestBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            requestBatteryOptimization();
        }
    }

    private void requestBatteryOptimization() {
        if (batteryDialogShowing) return;
        batteryDialogShowing = true;
        new AlertDialog.Builder(this)
            .setTitle("⚡ Background Running Required")
            .setMessage("To keep monitoring JioMart stock in the background, this app must be excluded from battery optimization.\n\nWithout this, the app may stop checking and you will miss restock alerts.\n\nTap Allow → select \"Don't optimize\".")
            .setCancelable(false)
            .setPositiveButton("Allow", (d, w) -> {
                batteryDialogShowing = false;
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ex) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            })
            .setNegativeButton("Ask Me Later", (d, w) -> batteryDialogShowing = false)
            .show();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void updateUI() {
        if (!isInitialized) return; // guard
        products.clear();
        products.addAll(ProductStorage.getAll(this));
        adapter.notifyDataSetChanged();
        updateEmptyState();
        long ts = ProductStorage.getLastCheckedAt(this);
        if (ts > 0) {
            String t = new SimpleDateFormat("dd MMM  hh:mm:ss a", Locale.getDefault())
                .format(new Date(ts));
            tvLastChecked.setText("Last fetched: " + t);
        } else {
            tvLastChecked.setText("Fetching…");
        }
    }

    private void updateEmptyState() {
        if (emptyState == null || recyclerView == null) return;
        boolean isEmpty = products.isEmpty();
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void triggerManualRefresh() {
        if (!isInitialized) return;
        swipeRefresh.setRefreshing(true);
        refreshTimeoutHandler.removeCallbacks(stopRefreshRunnable);
        refreshTimeoutHandler.postDelayed(stopRefreshRunnable, 30_000);
        Intent svc = new Intent(this, StockMonitorService.class);
        svc.setAction(StockMonitorService.ACTION_MANUAL_CHECK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(svc);
        else
            startService(svc);
    }

    private void startMonitor() {
        Intent svc = new Intent(this, StockMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(svc);
        else
            startService(svc);
    }

    private void confirmDelete(Product p) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Product?")
            .setMessage("Remove \"" + p.getName() + "\" from tracking?")
            .setPositiveButton("Delete", (d, w) -> {
                ProductStorage.deleteProduct(this, p.getId());
                products.clear();
                products.addAll(ProductStorage.getAll(this));
                adapter.notifyDataSetChanged();
                updateEmptyState();
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void openEdit(Product p) {
        Intent i = new Intent(this, AddProductActivity.class);
        i.putExtra("edit_id", p.getId());
        startActivityForResult(i, REQ_ADD);
    }

    // ── Custom developer dialog ───────────────────────────────────────────────

    private void showDeveloperDialog() {
        Dialog dialog = new Dialog(this, R.style.DeveloperDialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_developer);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.findViewById(R.id.tvTelegramLink).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/drdevhacks")));
            dialog.dismiss();
        });
        ((MaterialButton) dialog.findViewById(R.id.btnOpenTelegram)).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/drdevhacks")));
            dialog.dismiss();
        });
        dialog.findViewById(R.id.btnCloseDialog).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ── Expired dialog ────────────────────────────────────────────────────────

    private void showExpiredDialog() {
        Dialog dialog = new Dialog(this, R.style.DeveloperDialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.dialog_trial_expired);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.findViewById(R.id.btnContactDev).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/drdevhacks")));
            finish();
        });
        dialog.findViewById(R.id.btnCloseApp).setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });
        dialog.show();
    }
}
