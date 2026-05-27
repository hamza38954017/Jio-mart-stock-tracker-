package com.drdevhacks.jiomartmonitor;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.drdevhacks.jiomartmonitor.adapter.ProductAdapter;
import com.drdevhacks.jiomartmonitor.model.Product;
import com.drdevhacks.jiomartmonitor.service.StockMonitorService;
import com.drdevhacks.jiomartmonitor.util.ExpiryManager;
import com.drdevhacks.jiomartmonitor.util.NotificationHelper;
import com.drdevhacks.jiomartmonitor.util.ProductStorage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RecyclerView      recyclerView;
    private ProductAdapter    adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView          tvLastChecked;
    private TextView          tvDaysLeft;
    private List<Product>     products;
    private static final int  REQ_NOTIF = 100;
    private static final int  REQ_ADD   = 200;

    private final BroadcastReceiver stockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (StockMonitorService.ACTION_UPDATE.equals(action)) {
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

        ExpiryManager.init(this);
        if (ExpiryManager.isExpired(this)) {
            showExpiredDialog();
            return;
        }

        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        tvLastChecked = findViewById(R.id.tvLastChecked);
        tvDaysLeft    = findViewById(R.id.tvDaysLeft);
        swipeRefresh  = findViewById(R.id.swipeRefresh);
        recyclerView  = findViewById(R.id.recyclerView);

        // Days remaining banner
        long days = ExpiryManager.getDaysRemaining(this);
        tvDaysLeft.setText(days > 1 ? "Trial: " + days + " days left" : "⚠️ Trial expires today!");
        tvDaysLeft.setVisibility(View.VISIBLE);

        products = ProductStorage.getAll(this);
        adapter  = new ProductAdapter(this, products);
        adapter.setListener(new ProductAdapter.OnProductActionListener() {
            @Override public void onDelete(Product p) { confirmDelete(p); }
            @Override public void onEdit(Product p)   { openEdit(p); }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(false);

        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.colorPrimary),
            ContextCompat.getColor(this, R.color.colorInStock));
        swipeRefresh.setOnRefreshListener(this::triggerManualRefresh);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v ->
            startActivityForResult(new Intent(this, AddProductActivity.class), REQ_ADD));

        requestNotifPermission();
        startMonitor();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ADD && resultCode == RESULT_OK) {
            products.clear();
            products.addAll(ProductStorage.getAll(this));
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Product added!", Toast.LENGTH_SHORT).show();
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
            triggerManualRefresh();
            return true;
        }
        if (item.getItemId() == R.id.action_developer) {
            showDeveloperDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateUI() {
        products.clear();
        products.addAll(ProductStorage.getAll(this));
        adapter.notifyDataSetChanged();

        long ts = ProductStorage.getLastCheckedAt(this);
        if (ts > 0) {
            String t = new SimpleDateFormat("dd MMM  hh:mm:ss a", Locale.getDefault())
                .format(new Date(ts));
            tvLastChecked.setText("Last fetched: " + t);
        } else {
            tvLastChecked.setText("Fetching…");
        }
    }

    private void triggerManualRefresh() {
        swipeRefresh.setRefreshing(true);
        // Restart service to trigger immediate check
        Intent svc = new Intent(this, StockMonitorService.class);
        stopService(svc);
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
            .setMessage("Remove "" + p.getName() + "" from tracking?")
            .setPositiveButton("Delete", (d, w) -> {
                ProductStorage.deleteProduct(this, p.getId());
                products.clear();
                products.addAll(ProductStorage.getAll(this));
                adapter.notifyDataSetChanged();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openEdit(Product p) {
        Intent i = new Intent(this, AddProductActivity.class);
        i.putExtra("edit_id", p.getId());
        startActivityForResult(i, REQ_ADD);
    }

    private void showDeveloperDialog() {
        new AlertDialog.Builder(this)
            .setTitle("👨‍💻 Developer")
            .setMessage("This JioMart Stock Tracker app is made by\n\n"
                + "Dr. Dev  ||  Dr. Hamza\n\n"
                + "Contact: @drdevhacks\n\n"
                + "For custom apps, automation & bots.")
            .setPositiveButton("Open Telegram", (d, w) -> {
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://t.me/drdevhacks")));
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void showExpiredDialog() {
        new AlertDialog.Builder(this)
            .setTitle("⏰ Trial Expired")
            .setMessage("Your 7-day trial has expired.\n\nContact the developer to purchase a license.\n\n@drdevhacks")
            .setCancelable(false)
            .setPositiveButton("Contact Developer", (d, w) -> {
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://t.me/drdevhacks")));
                finish();
            })
            .setNegativeButton("Close App", (d, w) -> finish())
            .show();
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            }
        }
    }
}
