# JioMart Monitor — Android App

**JioMart Stock Tracker for Android**  
By **Dr. Dev || Dr. Hamza** · Contact: [@drdevhacks](https://t.me/drdevhacks)

---

## 🔨 Build APK via GitHub Actions (no PC needed)

1. **Fork or push** this entire folder to a GitHub repository
2. Go to **Actions** tab in your repo
3. Click **Build JioMart Monitor APK** → **Run workflow**
4. Wait ~5 minutes for build to complete
5. Download **JioMartMonitor-debug-apk** from the Artifacts section
6. Install the APK on your Android phone (enable Unknown Sources)

---

## ✨ Features

- 📦 **7 default products × 3 locations** tracked automatically
- 🔔 **Alarm + notification** when product comes in stock
- ⏰ **Auto-dismiss alarm** after 5 minutes
- 🔄 **Background service** — monitors every 5 minutes, 24/7
- 📲 **Boot persistence** — restarts automatically after phone reboot
- ➕ **Add custom products** with your own JioMart URL + location
- ✏️ **Edit / delete** user-added products
- 📊 **Live dashboard** with last-fetched time
- 💰 **Price display** for in-stock items
- 🔗 **Buy Now** opens JioMart directly
- ⏳ **7-day trial** — shows expiry dialog with developer contact

---

## 📱 Requirements

- Android 8.0+ (API 26+)
- Internet connection

---

## 📂 Project Structure

```
JioMartMonitor/
├── app/src/main/
│   ├── java/com/drdevhacks/jiomartmonitor/
│   │   ├── MainActivity.java           — Dashboard
│   │   ├── AlarmActivity.java          — Alarm screen (auto-dismiss 5 min)
│   │   ├── AddProductActivity.java     — Add/edit product
│   │   ├── adapter/ProductAdapter.java — RecyclerView cards
│   │   ├── model/Product.java          — Product data model
│   │   ├── model/StockResult.java      — Stock result model
│   │   ├── service/StockMonitorService — Background 24/7 monitor
│   │   ├── receiver/BootReceiver       — Auto-start on boot
│   │   ├── receiver/AlarmDismissReceiver
│   │   └── util/
│   │       ├── ExpiryManager.java      — 7-day trial logic
│   │       ├── JioMartApiHelper.java   — API calls
│   │       ├── NotificationHelper.java — Notifications + alarms
│   │       └── ProductStorage.java     — SharedPreferences storage
│   └── res/
│       ├── layout/                     — All XML layouts
│       ├── values/                     — Colors, strings, themes
│       └── drawable/                   — Icons, backgrounds
└── .github/workflows/build.yml         — GitHub Actions CI
```

---

## ⚙️ Configuration

Edit **`ProductStorage.java`** to update cookies/tokens when they expire:
- `COOKIE_DAV`, `COOKIE_APOLLO`, `COOKIE_BRAHMAN`
- Bearer token in **`JioMartApiHelper.java`** → `BEARER` constant

---

*Built with ❤️ by Dr. Dev || Dr. Hamza · @drdevhacks*
