package com.drdevhacks.jiomartmonitor.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.drdevhacks.jiomartmonitor.model.Product;
import com.drdevhacks.jiomartmonitor.model.StockResult;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductStorage {
    private static final String PREF_PRODUCTS = "jm_products";
    private static final String PREF_STOCK    = "jm_stock";
    private static final String KEY_PRODUCTS  = "products_json";
    private static final Gson GSON = new Gson();

    // Default cookies per location
    private static final String COOKIE_DAV =
        "AKA_A2=A; app_location_details=%7B%22country%22%3A%22INDIA%22%2C%22country_iso_code%22%3A%22IN%22%2C" +
        "%22city%22%3A%22JIND%22%2C%22pincode%22%3A%22126102%22%2C%22state%22%3A%22HARYANA%22%7D; " +
        "app_geolocation=%7B%22latitude%22%3A%2229.310786554961744%22%2C%22longitude%22%3A" +
        "%2276.3472004892227%22%2C%22polygon_ids%22%3A%5B%22U1NF_QC_91662111%22%2C%22T9RH_QC_3c37f428%22%5D%7D; " +
        "anonymous_id=e20dabeca64c4e868e61f91e81516f76; " +
        "anonymous_sig=4ff8344a7a79f0b29140728c47e795a9b73166374d098d5225c9328070cc87c9; " +
        "_ga=GA1.1.99937716.1779673603";
    private static final String COOKIE_APOLLO =
        "AKA_A2=A; app_location_details=%7B%22country%22%3A%22INDIA%22%2C%22country_iso_code%22%3A%22IN%22%2C" +
        "%22city%22%3A%22JIND%22%2C%22pincode%22%3A%22126102%22%2C%22state%22%3A%22HARYANA%22%7D; " +
        "app_geolocation=%7B%22latitude%22%3A%2229.309498131362464%22%2C%22longitude%22%3A" +
        "%2276.31370579498912%22%2C%22polygon_ids%22%3A%5B%22U1GI_QC_18f00da5%22%2C%22T9RH_QC_3c37f428%22%5D%7D; " +
        "anonymous_id=d0d857655c3f42369d6d8dc079aab761; " +
        "anonymous_sig=4346e96bde30c71efa5341afe992dcc85d3010415ea86e1013ff7aa0d05cefc5; " +
        "_ga=GA1.1.514069456.1779673951";
    private static final String COOKIE_BRAHMAN =
        "AKA_A2=A; app_location_details=%7B%22country%22%3A%22INDIA%22%2C%22country_iso_code%22%3A%22IN%22%2C" +
        "%22city%22%3A%22JIND%22%2C%22pincode%22%3A%22126102%22%2C%22state%22%3A%22HARYANA%22%7D; " +
        "app_geolocation=%7B%22latitude%22%3A%2229.32200106845254%22%2C%22longitude%22%3A" +
        "%2276.34042413068849%22%2C%22polygon_ids%22%3A%5B%22TXVR_QC_4bd2e403%22%2C%22T9RH_QC_3c37f428%22%5D%7D; " +
        "anonymous_id=1883bea16cac435980d23a5f2aff32f9; " +
        "anonymous_sig=772ebd9b6c8d2bfed18d00d060ea0f5f67b1ccc042e7ada1eb99818a94959b43; " +
        "_ga=GA1.1.1210312432.1779674289";

    /** Return all products (defaults + user-added). */
    public static List<Product> getAll(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF_PRODUCTS, Context.MODE_PRIVATE);
        String json = p.getString(KEY_PRODUCTS, null);
        if (json == null) {
            List<Product> defaults = buildDefaults();
            save(ctx, defaults);
            return defaults;
        }
        Type t = new TypeToken<List<Product>>(){}.getType();
        List<Product> list = GSON.fromJson(json, t);
        return list != null ? list : buildDefaults();
    }

    public static void save(Context ctx, List<Product> products) {
        ctx.getSharedPreferences(PREF_PRODUCTS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PRODUCTS, GSON.toJson(products)).apply();
    }

    public static void addProduct(Context ctx, Product p) {
        List<Product> list = getAll(ctx);
        list.add(p);
        save(ctx, list);
    }

    public static void updateProduct(Context ctx, Product updated) {
        List<Product> list = getAll(ctx);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(updated.getId())) {
                list.set(i, updated);
                break;
            }
        }
        save(ctx, list);
    }

    public static void deleteProduct(Context ctx, String id) {
        List<Product> list = getAll(ctx);
        list.removeIf(p -> p.getId().equals(id));
        save(ctx, list);
    }

    // ── Stock result cache ────────────────────────────────────────────────────
    public static void saveResult(Context ctx, StockResult r) {
        ctx.getSharedPreferences(PREF_STOCK, Context.MODE_PRIVATE)
            .edit().putString(r.getProductId(), GSON.toJson(r)).apply();
    }

    public static StockResult getResult(Context ctx, String productId) {
        String json = ctx.getSharedPreferences(PREF_STOCK, Context.MODE_PRIVATE)
            .getString(productId, null);
        if (json == null) return null;
        return GSON.fromJson(json, StockResult.class);
    }

    public static long getLastCheckedAt(Context ctx) {
        return ctx.getSharedPreferences(PREF_STOCK, Context.MODE_PRIVATE)
            .getLong("_last_checked", 0L);
    }

    public static void setLastCheckedAt(Context ctx, long ts) {
        ctx.getSharedPreferences(PREF_STOCK, Context.MODE_PRIVATE)
            .edit().putLong("_last_checked", ts).apply();
    }

    /** Returns the closest default location's store IDs & cookie based on lat/lon. */
    public static Object[] getNearestLocationInfo(double lat, double lon) {
        double[][] locCoords = {
            {29.310786, 76.347200},  // DAV
            {29.309498, 76.313706},  // Apollo
            {29.322001, 76.340424},  // Brahman
        };
        List<Integer>[] storeIdsList = new List[]{
            Arrays.asList(9435, 14651, 15190),
            Arrays.asList(382, 15190, 14651),
            Arrays.asList(8504, 14651, 15190),
        };
        List<String>[] polyIdsList = new List[]{
            Arrays.asList("U1NF_QC_91662111", "T9RH_QC_3c37f428"),
            Arrays.asList("U1GI_QC_18f00da5", "T9RH_QC_3c37f428"),
            Arrays.asList("TXVR_QC_4bd2e403", "T9RH_QC_3c37f428"),
        };
        String[] cookies = {COOKIE_DAV, COOKIE_APOLLO, COOKIE_BRAHMAN};

        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < locCoords.length; i++) {
            double d = Math.pow(lat - locCoords[i][0], 2) + Math.pow(lon - locCoords[i][1], 2);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        return new Object[]{ storeIdsList[best], polyIdsList[best], cookies[best] };
    }

    // ── Default 7 products (all 3 locations = 21 products total) ─────────────
    private static List<Product> buildDefaults() {
        List<Product> list = new ArrayList<>();
        // Each product appears 3 times (once per location)
        Object[][] prods = {
            {"Mirinda Orange 2.25 L",      "🟠", "mirinda-orange-225-l-mffmzl-7519054",   "https://store1.jiomartjcp.com/l/8Bpr"},
            {"Frooti Mango Drink 2 L",     "🥭", "frooti-mango-drink-2-l-mffmv9-7510506",  "https://store1.jiomartjcp.com/l/9YlN"},
            {"Slice Mango Drink 1.75 L",   "🍹", "slice-mango-drink-175-l-mffnan-7543736", "https://store1.jiomartjcp.com/l/7UJ9"},
            {"Onion (Approx. 900g-1000g)", "🧅", "onion-1-kg-pack-mffneb-7550741",         "https://store1.jiomartjcp.com/l/6eZI"},
            {"Mountain Dew 2.25 L",        "💚", "mountain-dew-225-l-mffmvp-7511432",      "https://store1.jiomartjcp.com/l/6sXt"},
            {"Coca Cola 2 L",              "🥤", "coca-cola-2-l-mffn78-7536375",            "https://store1.jiomartjcp.com/l/7FwF"},
            {"Limca 2 L",                  "🍋", "limca-2-l-mffmzv-7520404",               "https://store1.jiomartjcp.com/l/8d4j"},
        };
        Object[][] locs = {
            {"DAV Police Public School Road", 29.310786, 76.347200,
             Arrays.asList(9435, 14651, 15190),
             Arrays.asList("U1NF_QC_91662111", "T9RH_QC_3c37f428"), COOKIE_DAV},
            {"Apollo Tyre Jain", 29.309498, 76.313706,
             Arrays.asList(382, 15190, 14651),
             Arrays.asList("U1GI_QC_18f00da5", "T9RH_QC_3c37f428"), COOKIE_APOLLO},
            {"Brahman Dharamshala", 29.322001, 76.340424,
             Arrays.asList(8504, 14651, 15190),
             Arrays.asList("TXVR_QC_4bd2e403", "T9RH_QC_3c37f428"), COOKIE_BRAHMAN},
        };
        for (Object[] loc : locs) {
            for (Object[] prod : prods) {
                Product p = new Product(
                    (String) prod[0], (String) prod[1],
                    (String) prod[2], (String) prod[3],
                    (String) loc[0],
                    (double) loc[1], (double) loc[2],
                    (List<Integer>) loc[3],
                    (List<String>) loc[4],
                    (String) loc[5],
                    true
                );
                list.add(p);
            }
        }
        return list;
    }
}
