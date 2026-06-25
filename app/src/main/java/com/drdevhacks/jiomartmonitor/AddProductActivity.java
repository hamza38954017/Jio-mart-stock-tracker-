package com.drdevhacks.jiomartmonitor;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.drdevhacks.jiomartmonitor.model.Product;
import com.drdevhacks.jiomartmonitor.util.ProductStorage;
import java.util.List;

public class AddProductActivity extends AppCompatActivity {

    private EditText   etName, etUrl, etLat, etLon, etLocationName;
    private Spinner    spEmoji;
    private Button     btnGetLocation;
    private ProgressBar pbLocation;
    private String     editId = null;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private static final int    REQ_LOCATION = 301;
    private static final String[] EMOJIS = {
        "📦","🟠","🥭","🍹","🧅","💚","🥤","🍋","🍔","🛒","🥛","🧴","🍫","🍎","🧃"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);
        setSupportActionBar(findViewById(R.id.toolbarAdd));
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        etName         = findViewById(R.id.etName);
        etUrl          = findViewById(R.id.etUrl);
        etLat          = findViewById(R.id.etLat);
        etLon          = findViewById(R.id.etLon);
        etLocationName = findViewById(R.id.etLocationName);
        spEmoji        = findViewById(R.id.spEmoji);
        btnGetLocation = findViewById(R.id.btnGetLocation);
        pbLocation     = findViewById(R.id.pbLocation);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        ArrayAdapter<String> emojiAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, EMOJIS);
        emojiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEmoji.setAdapter(emojiAdapter);

        editId = getIntent().getStringExtra("edit_id");
        if (editId != null) {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Edit Product");
            loadExisting();
        } else {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Add Product");
        }

        btnGetLocation.setOnClickListener(v -> requestLocationPermission());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
    }

    // ── Location Permission Flow ──────────────────────────────────────────────

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation();
            return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                .setTitle("📍 Location Permission")
                .setMessage("Location permission is needed to automatically fill your latitude and longitude.\n\nThis makes it easy to track products near you without typing coordinates manually.")
                .setCancelable(false)
                .setPositiveButton("Allow", (d, w) -> ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION))
                .setNegativeButton("Enter Manually", (d, w) -> d.dismiss())
                .show();
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                             Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_LOCATION) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                fetchCurrentLocation();
            } else if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Permanently denied → send to settings
                new AlertDialog.Builder(this)
                    .setTitle("📍 Location Permission Denied")
                    .setMessage("Location permission is permanently denied.\n\nTo auto-fill coordinates:\n• Open App Settings\n• Tap Permissions → Location → Allow")
                    .setCancelable(false)
                    .setPositiveButton("Open Settings", (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    })
                    .setNegativeButton("Enter Manually", (d, w) -> d.dismiss())
                    .show();
            } else {
                // Denied but not permanently — ask again
                new AlertDialog.Builder(this)
                    .setTitle("📍 Location Needed")
                    .setMessage("Without location permission, you'll need to enter coordinates manually.\n\nAlternatively, long-press on Google Maps to get your lat,lon and enter them below.")
                    .setPositiveButton("Allow Location", (d, w) -> requestLocationPermission())
                    .setNegativeButton("Enter Manually", (d, w) -> d.dismiss())
                    .show();
            }
        }
    }

    private void fetchCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        btnGetLocation.setEnabled(false);
        pbLocation.setVisibility(View.VISIBLE);
        btnGetLocation.setText("📡 Fetching…");

        // Try last known location first (instant)
        Location last = null;
        try { last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        if (last == null) try { last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
        if (last != null) {
            fillLocation(last);
            return;
        }

        // Request fresh fix
        locationListener = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location loc) {
                fillLocation(loc);
                try { locationManager.removeUpdates(this); } catch (Exception ignored) {}
            }
            @Override public void onProviderDisabled(@NonNull String provider) {
                runOnUiThread(() -> {
                    resetLocationButton();
                    Toast.makeText(AddProductActivity.this,
                        "⚠️ Please enable GPS / Location services", Toast.LENGTH_LONG).show();
                });
            }
        };

        boolean gpsOk = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean netOk = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!gpsOk && !netOk) {
            resetLocationButton();
            new AlertDialog.Builder(this)
                .setTitle("📍 Location Disabled")
                .setMessage("GPS / Location services are turned off.\n\nPlease enable them to auto-fill coordinates.")
                .setPositiveButton("Open Settings", (d, w) ->
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("Enter Manually", (d, w) -> d.dismiss())
                .show();
            return;
        }

        try {
            if (gpsOk) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER,
                    locationListener, Looper.getMainLooper());
            } else {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,
                    locationListener, Looper.getMainLooper());
            }
            // Auto-timeout after 15 s
            etLat.postDelayed(() -> {
                if (pbLocation.getVisibility() == View.VISIBLE) {
                    try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
                    resetLocationButton();
                    Toast.makeText(this, "⏱ GPS timeout. Enter coordinates manually.", Toast.LENGTH_LONG).show();
                }
            }, 15_000);
        } catch (Exception e) {
            resetLocationButton();
            Toast.makeText(this, "Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fillLocation(Location loc) {
        runOnUiThread(() -> {
            etLat.setText(String.format("%.7f", loc.getLatitude()));
            etLon.setText(String.format("%.7f", loc.getLongitude()));
            resetLocationButton();
            Toast.makeText(this, "✅ Location filled!", Toast.LENGTH_SHORT).show();
        });
    }

    private void resetLocationButton() {
        runOnUiThread(() -> {
            btnGetLocation.setEnabled(true);
            btnGetLocation.setText("📍  Use My Location");
            pbLocation.setVisibility(View.GONE);
        });
    }

    @Override protected void onPause() {
        super.onPause();
        if (locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        }
    }

    // ── Load existing product (edit mode) ────────────────────────────────────

    private void loadExisting() {
        for (Product p : ProductStorage.getAll(this)) {
            if (p.getId().equals(editId)) {
                etName.setText(p.getName());
                etUrl.setText(p.getUrl());
                etLat.setText(String.valueOf(p.getLat()));
                etLon.setText(String.valueOf(p.getLon()));
                etLocationName.setText(p.getLocationName());
                for (int i = 0; i < EMOJIS.length; i++) {
                    if (EMOJIS[i].equals(p.getEmoji())) { spEmoji.setSelection(i); break; }
                }
                break;
            }
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    private void save() {
        String name    = etName.getText().toString().trim();
        String url     = etUrl.getText().toString().trim();
        String latStr  = etLat.getText().toString().trim();
        String lonStr  = etLon.getText().toString().trim();
        String locName = etLocationName.getText().toString().trim();
        String emoji   = (String) spEmoji.getSelectedItem();

        if (TextUtils.isEmpty(name)) { etName.setError("Product name required"); return; }
        if (TextUtils.isEmpty(url) || !url.startsWith("http")) {
            etUrl.setError("Valid JioMart URL required (https://…)"); return; }
        if (TextUtils.isEmpty(latStr)) { etLat.setError("Latitude required"); return; }
        if (TextUtils.isEmpty(lonStr)) { etLon.setError("Longitude required"); return; }

        double lat, lon;
        try { lat = Double.parseDouble(latStr); } catch (Exception e) {
            etLat.setError("Invalid latitude"); return; }
        try { lon = Double.parseDouble(lonStr); } catch (Exception e) {
            etLon.setError("Invalid longitude"); return; }

        String slug = deriveSlug(url);

        Object[] locInfo = ProductStorage.getNearestLocationInfo(lat, lon);
        @SuppressWarnings("unchecked")
        List<Integer> storeIds  = (List<Integer>) locInfo[0];
        @SuppressWarnings("unchecked")
        List<String>  polyIds   = (List<String>)  locInfo[1];
        String        cookie    = (String)         locInfo[2];

        if (editId != null) {
            List<Product> list = ProductStorage.getAll(this);
            for (Product p : list) {
                if (p.getId().equals(editId)) {
                    p.setName(name); p.setEmoji(emoji); p.setSlug(slug); p.setUrl(url);
                    p.setLocationName(TextUtils.isEmpty(locName) ? "Custom Location" : locName);
                    p.setLat(lat); p.setLon(lon);
                    p.setStoreIds(storeIds); p.setPolygonIds(polyIds); p.setCookie(cookie);
                    break;
                }
            }
            ProductStorage.save(this, list);
            Toast.makeText(this, "Product updated!", Toast.LENGTH_SHORT).show();
        } else {
            Product p = new Product(name, emoji, slug, url,
                TextUtils.isEmpty(locName) ? "Custom Location" : locName,
                lat, lon, storeIds, polyIds, cookie, false);
            ProductStorage.addProduct(this, p);
        }
        setResult(RESULT_OK);
        finish();
    }

    private String deriveSlug(String url) {
        try {
            String path = url.replaceAll("https?://[^/]+", "");
            if (path.contains("/products/")) {
                String after = path.split("/products/")[1];
                return after.split("[?#/]")[0];
            }
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!parts[i].isEmpty()) return parts[i];
            }
        } catch (Exception ignored) {}
        return url;
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
