package com.drdevhacks.jiomartmonitor.model;

import java.util.HashMap;
import java.util.Map;

public class StockResult {
    private String productId;
    private boolean available;          // true if ANY store has stock
    private int totalQuantity;
    private String price;
    private Map<Integer, StoreStock> storeStocks;
    private long checkedAt;
    private boolean error;
    private String errorMsg;
    // Previous state for change detection
    private boolean prevAvailable;
    private int prevQuantity;

    public StockResult(String productId) {
        this.productId = productId;
        this.storeStocks = new HashMap<>();
        this.checkedAt = System.currentTimeMillis();
        this.price = "N/A";
    }

    public static class StoreStock {
        public int storeId;
        public boolean inStock;
        public int quantity;
        public String price;

        public StoreStock(int storeId, boolean inStock, int quantity, String price) {
            this.storeId = storeId;
            this.inStock = inStock;
            this.quantity = quantity;
            this.price = price != null ? price : "N/A";
        }
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public String getProductId() { return productId; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public int getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = totalQuantity; }
    public String getPrice() { return price != null ? price : "N/A"; }
    public void setPrice(String price) { this.price = price; }
    public Map<Integer, StoreStock> getStoreStocks() { return storeStocks; }
    public void addStoreStock(StoreStock ss) {
        storeStocks.put(ss.storeId, ss);
        if (ss.inStock) {
            available = true;
            totalQuantity += ss.quantity;
            if (ss.price != null && !ss.price.equals("N/A") && price.equals("N/A"))
                price = ss.price;
        }
    }
    public long getCheckedAt() { return checkedAt; }
    public boolean isError() { return error; }
    public void setError(boolean error) { this.error = error; }
    public String getErrorMsg() { return errorMsg != null ? errorMsg : ""; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; this.error = true; }
    public boolean isPrevAvailable() { return prevAvailable; }
    public void setPrevAvailable(boolean v) { this.prevAvailable = v; }
    public int getPrevQuantity() { return prevQuantity; }
    public void setPrevQuantity(int v) { this.prevQuantity = v; }

    public String getStatusText() {
        if (error) return "Error";
        if (available) return "In Stock";
        return "Out of Stock";
    }

    public String getAvailableStoreIds() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, StoreStock> e : storeStocks.entrySet()) {
            if (e.getValue().inStock) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("S").append(e.getKey());
            }
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }
}
