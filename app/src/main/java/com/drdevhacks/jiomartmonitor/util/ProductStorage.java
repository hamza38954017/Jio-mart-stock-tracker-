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
import java.util.List;

public class ProductStorage {
    private static final String PREF_PRODUCTS = "jm_products";
    private static final String PREF_STOCK    = "jm_stock";
    private static final String KEY_PRODUCTS  = "products_json";
    private static final Gson   GSON          = new Gson();

    /** Returns all user-added products (no hardcoded defaults). */
    public static List<Product> getAll(Context ctx) {
        SharedPreferences p  = ctx.getSharedPreferences(PREF_PRODUCTS, Context.MODE_PRIVATE);
        String json = p.getString(KEY_PRODUCTS, null);
        if (json == null) return new ArrayList<>();
        Type t = new TypeToken<List<Product>>(){}.getType();
        List<Product> list = GSON.fromJson(json, t);
        return list != null ? list : new ArrayList<>();
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

    /**
     * Returns {storeIds, polygonIds, cookie} for the coordinate pair.
     * Finds the nearest of the 3 reference locations by Euclidean distance.
     */
    public static Object[] getNearestLocationInfo(double lat, double lon) {
        double[][] locCoords = {
            {29.310786, 76.347200},
            {29.309498, 76.313706},
            {29.322001, 76.340424},
        };
        @SuppressWarnings("unchecked")
        List<Integer>[] storeIdsList = new List[]{
            Arrays.asList(9435, 14651, 15190),
            Arrays.asList(382,  15190, 14651),
            Arrays.asList(8504, 14651, 15190),
        };
        @SuppressWarnings("unchecked")
        List<String>[] polyIdsList = new List[]{
            Arrays.asList("U1NF_QC_91662111", "T9RH_QC_3c37f428"),
            Arrays.asList("U1GI_QC_18f00da5", "T9RH_QC_3c37f428"),
            Arrays.asList("TXVR_QC_4bd2e403", "T9RH_QC_3c37f428"),
        };
        String[] cookies = {buildCookie(29.310786, 76.347200, "U1NF_QC_91662111"),
                            buildCookie(29.309498, 76.313706, "U1GI_QC_18f00da5"),
                            buildCookie(29.322001, 76.340424, "TXVR_QC_4bd2e403")};

        int best = 0; double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < locCoords.length; i++) {
            double d = Math.pow(lat - locCoords[i][0], 2) + Math.pow(lon - locCoords[i][1], 2);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        return new Object[]{ storeIdsList[best], polyIdsList[best], cookies[best] };
    }

    private static String buildCookie(double lat, double lon, String polyId) {
        return "AKA_A2=A; app_geolocation=%7B%22latitude%22%3A%22" + lat +
               "%22%2C%22longitude%22%3A%22" + lon +
               "%22%2C%22polygon_ids%22%3A%5B%22" + polyId + "%22%2C%22T9RH_QC_3c37f428%22%5D%7D; " +
               "anonymous_id=e20dabeca64c4e868e61f91e81516f76; " +
               "_ga=GA1.1.99937716.1779673603";
    }
}
