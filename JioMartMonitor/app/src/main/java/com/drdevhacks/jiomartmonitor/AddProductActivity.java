package com.drdevhacks.jiomartmonitor;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.drdevhacks.jiomartmonitor.model.Product;
import com.drdevhacks.jiomartmonitor.util.ProductStorage;
import java.util.Arrays;
import java.util.List;

public class AddProductActivity extends AppCompatActivity {

    private EditText etName, etUrl, etLat, etLon, etLocationName;
    private Spinner  spEmoji;
    private String   editId = null;

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

        ArrayAdapter<String> emojiAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, EMOJIS);
        emojiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEmoji.setAdapter(emojiAdapter);

        // Edit mode?
        editId = getIntent().getStringExtra("edit_id");
        if (editId != null) {
            getSupportActionBar().setTitle("Edit Product");
            loadExisting();
        } else {
            getSupportActionBar().setTitle("Add Product");
        }

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> save());
    }

    private void loadExisting() {
        List<Product> list = ProductStorage.getAll(this);
        for (Product p : list) {
            if (p.getId().equals(editId)) {
                etName.setText(p.getName());
                etUrl.setText(p.getUrl());
                etLat.setText(String.valueOf(p.getLat()));
                etLon.setText(String.valueOf(p.getLon()));
                etLocationName.setText(p.getLocationName());
                // Set emoji spinner
                for (int i = 0; i < EMOJIS.length; i++) {
                    if (EMOJIS[i].equals(p.getEmoji())) { spEmoji.setSelection(i); break; }
                }
                break;
            }
        }
    }

    private void save() {
        String name     = etName.getText().toString().trim();
        String url      = etUrl.getText().toString().trim();
        String latStr   = etLat.getText().toString().trim();
        String lonStr   = etLon.getText().toString().trim();
        String locName  = etLocationName.getText().toString().trim();
        String emoji    = (String) spEmoji.getSelectedItem();

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

        // Derive slug from URL (last path segment without query)
        String slug = deriveSlug(url);

        // Get nearest location info (store IDs, polygon IDs, cookie)
        Object[] locInfo = ProductStorage.getNearestLocationInfo(lat, lon);
        @SuppressWarnings("unchecked")
        List<Integer> storeIds   = (List<Integer>) locInfo[0];
        @SuppressWarnings("unchecked")
        List<String>  polyIds    = (List<String>)  locInfo[1];
        String        cookie     = (String)         locInfo[2];

        if (editId != null) {
            // Update existing
            List<Product> list = ProductStorage.getAll(this);
            for (Product p : list) {
                if (p.getId().equals(editId)) {
                    p.setName(name);
                    p.setEmoji(emoji);
                    p.setSlug(slug);
                    p.setUrl(url);
                    p.setLocationName(TextUtils.isEmpty(locName) ? "Custom Location" : locName);
                    p.setLat(lat);
                    p.setLon(lon);
                    p.setStoreIds(storeIds);
                    p.setPolygonIds(polyIds);
                    p.setCookie(cookie);
                    break;
                }
            }
            ProductStorage.save(this, list);
            Toast.makeText(this, "Product updated!", Toast.LENGTH_SHORT).show();
        } else {
            // Add new
            Product p = new Product(
                name, emoji, slug, url,
                TextUtils.isEmpty(locName) ? "Custom Location" : locName,
                lat, lon, storeIds, polyIds, cookie, false
            );
            ProductStorage.addProduct(this, p);
        }

        setResult(RESULT_OK);
        finish();
    }

    private String deriveSlug(String url) {
        try {
            String path = url.replaceAll("https?://[^/]+", "");
            // If it contains /products/ extract slug
            if (path.contains("/products/")) {
                String after = path.split("/products/")[1];
                return after.split("[?#/]")[0];
            }
            // Otherwise use last non-empty segment as a best-effort slug
            String[] parts = path.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!parts[i].isEmpty()) return parts[i];
            }
        } catch (Exception ignored) {}
        return url; // fallback: use full URL (API will reject, user must fix)
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }
}
