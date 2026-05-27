package com.drdevhacks.jiomartmonitor.model;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class Product {
    private String id;
    private String name;
    private String emoji;
    private String slug;
    private String url;
    private String locationName;
    private double lat;
    private double lon;
    private List<Integer> storeIds;
    private List<String> polygonIds;
    private String cookie;
    private boolean isDefault;
    private boolean enabled;
    private long addedAt;

    public Product() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
        this.addedAt = System.currentTimeMillis();
    }

    public Product(String name, String emoji, String slug, String url,
                   String locationName, double lat, double lon,
                   List<Integer> storeIds, List<String> polygonIds,
                   String cookie, boolean isDefault) {
        this();
        this.name = name;
        this.emoji = emoji;
        this.slug = slug;
        this.url = url;
        this.locationName = locationName;
        this.lat = lat;
        this.lon = lon;
        this.storeIds = storeIds;
        this.polygonIds = polygonIds;
        this.cookie = cookie;
        this.isDefault = isDefault;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmoji() { return emoji != null ? emoji : "📦"; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getLocationName() { return locationName != null ? locationName : ""; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }
    public List<Integer> getStoreIds() { return storeIds; }
    public void setStoreIds(List<Integer> storeIds) { this.storeIds = storeIds; }
    public List<String> getPolygonIds() { return polygonIds; }
    public void setPolygonIds(List<String> polygonIds) { this.polygonIds = polygonIds; }
    public String getCookie() { return cookie != null ? cookie : ""; }
    public void setCookie(String cookie) { this.cookie = cookie; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getAddedAt() { return addedAt; }
    public void setAddedAt(long addedAt) { this.addedAt = addedAt; }

    public String getStoreIdsString() {
        if (storeIds == null || storeIds.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < storeIds.size(); i++) {
            sb.append(storeIds.get(i));
            if (i < storeIds.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }
}
