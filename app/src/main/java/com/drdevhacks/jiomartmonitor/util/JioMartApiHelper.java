package com.drdevhacks.jiomartmonitor.util;

import com.drdevhacks.jiomartmonitor.model.Product;
import com.drdevhacks.jiomartmonitor.model.StockResult;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class JioMartApiHelper {

    private static final String BASE_URL     = "https://store1.jiomartjcp.com";
    private static final String BEARER       = "Njg1OTQ1ZjQ2YzhjN2FlZTNmM2FmNjA1OlRwS3c3d0Q5aA==";
    private static final String SDK_VER      = "1.10.3-60";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build();

    /** Synchronous — call from background thread only. */
    public static StockResult check(Product product) {
        StockResult result = new StockResult(product.getId());

        try {
            String storeIdsParam = buildStoreIdsParam(product.getStoreIds());
            String url = BASE_URL + "/api/service/application/catalog/v2.0/products/"
                + product.getSlug() + "/sizes?store_ids=" + storeIdsParam;

            String geoJson   = buildGeoJson(product);
            String locDetail = buildLocDetail();

            Request request = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "en-GB")
                .addHeader("Authorization", "Bearer " + BEARER)
                .addHeader("x-geolocation", geoJson)
                .addHeader("x-location-detail", locDetail)
                .addHeader("x-currency-code", "INR")
                .addHeader("x-fp-sdk-version", SDK_VER)
                .addHeader("User-Agent", "Mozilla/5.0 (Android 15; Mobile; rv:151.0) Gecko/151.0 Firefox/151.0")
                .addHeader("Cookie", product.getCookie())
                .addHeader("Sec-Fetch-Dest", "empty")
                .addHeader("Sec-Fetch-Mode", "cors")
                .addHeader("Sec-Fetch-Site", "same-origin")
                .build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.code() == 401) {
                    result.setErrorMsg("Token expired (401)");
                    return result;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    result.setErrorMsg("HTTP " + response.code());
                    return result;
                }
                String body = response.body().string();
                parseResponse(body, product, result);
            }

        } catch (Exception e) {
            result.setErrorMsg(e.getMessage() != null ? e.getMessage() : "Network error");
        }
        return result;
    }

    private static void parseResponse(String body, Product product, StockResult result) {
        try {
            JSONObject root = new JSONObject(body);

            // Extract price
            String price = extractPrice(root);
            result.setPrice(price);

            List<Integer> storeIds = product.getStoreIds();

            // Strategy 1: store_meta per-store
            if (root.has("store_meta")) {
                JSONArray meta = root.getJSONArray("store_meta");
                for (int i = 0; i < meta.length(); i++) {
                    JSONObject sm = meta.getJSONObject(i);
                    int uid = sm.optInt("uid", sm.optInt("id", sm.optInt("store_id", -1)));
                    if (storeIds.contains(uid)) {
                        int qty = sm.optInt("count", sm.optInt("quantity", 0));
                        String sp = extractPriceFromObj(sm.optJSONObject("price"));
                        result.addStoreStock(new StockResult.StoreStock(uid, qty > 0, qty,
                            sp != null ? sp : price));
                    }
                }
                if (!result.getStoreStocks().isEmpty()) {
                    for (Integer sid : storeIds) {
                        if (!result.getStoreStocks().containsKey(sid))
                            result.addStoreStock(new StockResult.StoreStock(sid, false, 0, price));
                    }
                    return;
                }
            }

            // Strategy 2: sizes[].stores per-store qty
            if (root.has("sizes")) {
                JSONArray sizes = root.getJSONArray("sizes");
                java.util.Map<Integer, Integer> perStore = new java.util.HashMap<>();
                for (Integer sid : storeIds) perStore.put(sid, 0);

                for (int i = 0; i < sizes.length(); i++) {
                    JSONObject sz = sizes.getJSONObject(i);
                    JSONObject stores = sz.optJSONObject("stores");
                    if (stores != null) {
                        for (Integer sid : storeIds) {
                            JSONObject storeData = stores.optJSONObject(String.valueOf(sid));
                            if (storeData != null)
                                perStore.merge(sid, storeData.optInt("quantity", 0), Integer::sum);
                        }
                    }
                }

                boolean anyFound = perStore.values().stream().anyMatch(q -> q > 0);
                if (anyFound) {
                    for (Integer sid : storeIds)
                        result.addStoreStock(new StockResult.StoreStock(sid,
                            perStore.get(sid) > 0, perStore.get(sid), price));
                    return;
                }

                // Strategy 3: total sellable_quantity
                int total = 0;
                for (int i = 0; i < sizes.length(); i++) {
                    JSONObject sz = sizes.getJSONObject(i);
                    total += sz.optInt("sellable_quantity", sz.optInt("quantity", 0));
                }
                boolean sellable = root.optBoolean("sellable", total > 0);
                for (Integer sid : storeIds)
                    result.addStoreStock(new StockResult.StoreStock(sid, sellable, total, price));
            }

        } catch (Exception e) {
            result.setErrorMsg("Parse error: " + e.getMessage());
        }
    }

    private static String extractPrice(JSONObject root) {
        // Try top-level price
        String p = extractPriceFromObj(root.optJSONObject("price"));
        if (p != null) return p;
        // Try sizes[0].price
        JSONArray sizes = root.optJSONArray("sizes");
        if (sizes != null && sizes.length() > 0) {
            try {
                p = extractPriceFromObj(sizes.getJSONObject(0).optJSONObject("price"));
                if (p != null) return p;
                // Try string fields
                JSONObject sz = sizes.getJSONObject(0);
                for (String key : new String[]{"price_effective", "effective_price"}) {
                    if (sz.has(key)) return "₹" + sz.get(key);
                }
            } catch (Exception ignored) {}
        }
        return "N/A";
    }

    private static String extractPriceFromObj(JSONObject po) {
        if (po == null) return null;
        JSONObject eff = po.optJSONObject("effective");
        JSONObject mrp = po.optJSONObject("marked");
        double raw = 0;
        if (eff != null) raw = eff.optDouble("min", eff.optDouble("max", 0));
        if (raw == 0 && mrp != null) raw = mrp.optDouble("min", mrp.optDouble("max", 0));
        if (raw > 0) return raw == (long) raw ? "₹" + (long) raw : "₹" + String.format("%.2f", raw);
        return null;
    }

    private static String buildStoreIdsParam(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append("%2C");
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private static String buildGeoJson(Product p) {
        StringBuilder poly = new StringBuilder("[");
        List<String> pids = p.getPolygonIds();
        if (pids != null) {
            for (int i = 0; i < pids.size(); i++) {
                if (i > 0) poly.append(",");
                poly.append("\"").append(pids.get(i)).append("\"");
            }
        }
        poly.append("]");
        return "{\"latitude\":\"" + p.getLat() + "\"," +
               "\"longitude\":\"" + p.getLon() + "\"," +
               "\"polygon_ids\":" + poly + "}";
    }

    private static String buildLocDetail() {
        return "{\"country\":\"INDIA\"," +
               "\"country_iso_code\":\"IN\"," +
               "\"city\":\"JIND\"," +
               "\"pincode\":\"126102\"," +
               "\"state\":\"HARYANA\"}";
    }
}
