package cbt.exambro.sas; // Fixed package name to match gradle namespace ok

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.VideoView;
import android.net.Uri;
import android.text.InputType;
import android.content.SharedPreferences;
import android.widget.TextView;
import android.widget.ListView;
import android.app.ActivityManager;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import android.os.Build;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;
import org.json.JSONArray;
import android.os.Environment;
import android.widget.ProgressBar;
import androidx.core.content.FileProvider;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private String URL_ONLINE;
    private static final String PREF_NAME = "CBTPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private boolean isAlertShowing = false;
    private boolean isInExamMode = false;
    private AlertDialog securityWarningDialog;
    private android.view.View splashScreen;
    private ImageButton btnSettings;
    private boolean isVideoFinished = false;
    private boolean pageLoadFinished = false;
    private static final String API_URL = "https://update.p171.net/api.php";
    private static final String UPDATE_API_URL = "https://update.p171.net/version.json";
    private static final int DEFAULT_BAR_COLOR = Color.parseColor("#0d6efd");

    // ---- Update & Instal APK (download langsung di dalam aplikasi) ----
    private static final String FILE_PROVIDER_AUTHORITY = "cbt.exambro.sas.fileprovider";
    private ActivityResultLauncher<android.content.Intent> installPermissionLauncher;
    private String pendingDownloadUrl;
    private AlertDialog downloadDialog;
    private ProgressBar downloadProgressBar;
    private TextView downloadStatusText;
    private boolean updateCheckDone = false;
    private boolean dashboardReloadDone = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. KEAMANAN: Blokir Screenshot & Layar Tetap Nyala
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        // AKTIFKAN SOLUSI KEYBOARD DISINI (Android Bug 5497 Workaround)
        AndroidBug5497Workaround.assistActivity(this);

        webView = findViewById(R.id.webview_cbt);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);

        // 2. Migrasi OnBackPressed ke modern OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isInExamMode) {
                    Toast.makeText(MainActivity.this, "Selesaikan ujian terlebih dahulu sebelum kembali ke Dashboard!", Toast.LENGTH_SHORT).show();
                } else {
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        // Jika tidak ada history, biarkan default Back
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                        setEnabled(true);
                    }
                }
            }
        });

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isInExamMode) {
                webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            } else {
                webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            }
            webView.reload();
        });

        // 3. Setup Splash Screen
        splashScreen = findViewById(R.id.splash_screen);
        VideoView splashVideo = findViewById(R.id.splash_video);
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean hasPlayedSplash = prefs.getBoolean("has_played_splash", false);

        if (!hasPlayedSplash && splashVideo != null) {
            // Set Z-order to ensure video draws correctly
            splashVideo.setZOrderOnTop(true);
            String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.splash_video;
            Uri uri = Uri.parse(videoPath);
            splashVideo.setVideoURI(uri);
            splashVideo.setOnCompletionListener(mp -> {
                isVideoFinished = true;
                prefs.edit().putBoolean("has_played_splash", true).apply();
                initializeAppLogic();
            });
            splashVideo.setOnErrorListener((mp, what, extra) -> {
                isVideoFinished = true;
                prefs.edit().putBoolean("has_played_splash", true).apply();
                initializeAppLogic();
                return true;
            });
            splashVideo.start();
        } else {
            isVideoFinished = true;
            if (splashVideo != null) {
                splashVideo.setVisibility(android.view.View.GONE);
            }
            initializeAppLogic();
        }
        
        btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // Launcher untuk kembali dari halaman izin "Instal aplikasi tidak dikenal"
        installPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canRequestPackageInstalls()) {
                        if (pendingDownloadUrl != null) {
                            String url = pendingDownloadUrl;
                            pendingDownloadUrl = null;
                            downloadAndInstallApk(url);
                        }
                    }
                });

        // 7. SOLUSI EDGE-TO-EDGE (Android 15+/16): konten WebView tidak boleh
        //    menutupi status bar (ikon kamera/sinyal) & navigation bar.
        applySystemBarInsets();

        // 8. WARNA BAR (status/navigation): biru brand, konsisten di semua halaman
        applyBarColor();
    }

    /**
     * Android 15+ memaksa edge-to-edge untuk app targetSdk 35/36, sehingga
     * konten WebView menggambar di belakang status bar & navigation bar.
     * Handler ini menerapkan padding insets system bars ke kontainer WebView
     * agar konten PWA tidak tertutup. Saat mode ujian (immersive) menyembunyikan
     * system bars, inset otomatis menjadi 0 sehingga WebView tetap fullscreen.
     */
    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(swipeRefreshLayout, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(0, bars.top, 0, bars.bottom);

            // Tombol pengaturan juga harus turun mengikuti status bar
            if (btnSettings != null) {
                ViewGroup.MarginLayoutParams lp =
                        (ViewGroup.MarginLayoutParams) btnSettings.getLayoutParams();
                lp.topMargin = bars.top + dp(8);
                btnSettings.setLayoutParams(lp);
            }
            return insets;
        });
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @SuppressWarnings("deprecation")
    private void applyBarColor() {
        int color = DEFAULT_BAR_COLOR;
        // Android 15+: status bar transparan, warna tampil via background decor
        getWindow().getDecorView().setBackgroundColor(color);
        // Android 14 ke bawah: warnai bar secara eksplisit (minSdk 24, aman)
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
    }

    /**
     * Sembunyikan tombol pengaturan server jika siswa sudah login.
     * Deteksi berbasis DOM: halaman login biasanya punya input password atau
     * judul mengandung "login"/"masuk". Setelah login (dashboard/ujian) tombol
     * otomatis disembunyikan. Tombol tetap tampil saat server belum diatur /
     * halaman error.
     */
    private void updateSettingsButtonVisibilityByDom() {
        if (btnSettings == null || webView == null) return;

        String url = webView.getUrl();
        boolean forceVisible = (URL_ONLINE == null || URL_ONLINE.isEmpty())
                || url == null
                || url.startsWith("data:")
                || url.contains("error.html");
        if (forceVisible) {
            btnSettings.setVisibility(android.view.View.VISIBLE);
            return;
        }

        webView.evaluateJavascript(
                "(function(){" +
                "var hasPw=!!document.querySelector('input[type=password]');" +
                "var t=(document.title||'').toLowerCase();" +
                "var inTitle=t.indexOf('login')>=0||t.indexOf('masuk')>=0;" +
                "return (hasPw||inTitle)?'login':'other';" +
                "})()",
                value -> {
                    if (value == null || "null".equals(value)) return;
                    boolean isLoginPage = value.contains("login");
                    btnSettings.setVisibility(isLoginPage
                            ? android.view.View.VISIBLE
                            : android.view.View.GONE);
                });
    }

    private void initializeAppLogic() {
        // 4. Konfigurasi WebView LEBIH AWAL
        setupWebView();
        webView.clearCache(true);

        // 5. Load Server
        loadServerUrl();

        if (URL_ONLINE == null || URL_ONLINE.isEmpty()) {
            pageLoadFinished = true;
            checkAndHideSplash();

            String html = "<html><body style='display:flex;justify-content:center;align-items:center;height:100vh;flex-direction:column;font-family:sans-serif;text-align:center;padding:20px;'>" +
                    "<h2 style='color:#d32f2f;'>Server Belum Diatur</h2>" +
                    "<p>Silakan klik tombol <b>Pengaturan</b> (ikon gear) di pojok kanan atas untuk memasukkan alamat server CBT Anda.</p>" +
                    "</body></html>";
            webView.loadData(html, "text/html", "UTF-8");

            Toast.makeText(this, "Alamat server belum di input!", Toast.LENGTH_LONG).show();
            showSettingsDialog();
        } else {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "Offline: Mengandalkan cache data...", Toast.LENGTH_SHORT).show();
            }
            determineServer();
        }

        // 6. Pengecekan Perangkat
        checkDeviceRequirements();

        // 6b. Cek update otomatis saat aplikasi dibuka
        checkForUpdateSilently();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        @SuppressLint("HardwareIds")
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        s.setUserAgentString(s.getUserAgentString() + " CBT-P171-ID:" + androidId + " P171-CBT-APP");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                android.util.Log.d("WebViewJS", cm.message());
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                if (isAlertShowing) {
                    result.cancel();
                    return true;
                }
                isAlertShowing = true;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("CBT Exambro SAS")
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            isAlertShowing = false;
                            result.confirm();
                        })
                        .setCancelable(false)
                        .show();
                return true;
            }
        });

        WebView.setWebContentsDebuggingEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidControl");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    String url = request.getUrl().toString();
                    int code = error.getErrorCode();

                    if (view.getSettings().getCacheMode() == WebSettings.LOAD_CACHE_ONLY) {
                        runOnUiThread(() -> {
                            view.setAlpha(1.0f);
                            if (url.equals(URL_ONLINE) || url.equals(URL_ONLINE + "/") || url.contains("index.php")) {
                                view.stopLoading();
                                view.clearHistory();
                                view.loadUrl("file:///android_asset/error.html");
                            } else {
                                view.loadUrl(URL_ONLINE + "/siswa/index.php");
                            }
                        });
                        return;
                    }

                    if (code == ERROR_CONNECT || code == ERROR_HOST_LOOKUP || code == ERROR_TIMEOUT) {
                        runOnUiThread(() -> {
                            view.setAlpha(0.1f);
                            view.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ONLY);

                            if (url.contains("ujian.php")) {
                                String cleanUrl = url.split("\\?")[0];
                                view.loadUrl(cleanUrl);
                            } else if (url.contains("index.php") || url.equals(URL_ONLINE) || url.equals(URL_ONLINE + "/")) {
                                view.loadUrl(url);
                            } else {
                                view.loadUrl(URL_ONLINE + "/siswa/index.php");
                            }
                        });
                    }

                    runOnUiThread(() -> {
                        if (splashScreen != null && splashScreen.getVisibility() == android.view.View.VISIBLE) {
                            splashScreen.animate().alpha(0f).setDuration(500).withEndAction(() -> {
                                splashScreen.setVisibility(android.view.View.GONE);
                                VideoView v = findViewById(R.id.splash_video);
                                if (v != null) v.stopPlayback();
                            }).start();
                        }
                    });
                }
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                if (btnSettings == null) return;
                // Halaman yang selalu membutuhkan tombol pengaturan server
                boolean forceVisible = (URL_ONLINE == null || URL_ONLINE.isEmpty())
                        || url == null
                        || url.startsWith("data:")
                        || url.contains("error.html");
                if (forceVisible) {
                    btnSettings.setVisibility(android.view.View.VISIBLE);
                }
                // Selain itu: onPageFinished memutuskan via deteksi DOM (lebih akurat)
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.animate().alpha(1f).setDuration(200).start();

                pageLoadFinished = true;
                checkAndHideSplash();

                if (isInExamMode) {
                    view.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                } else {
                    view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                }
                swipeRefreshLayout.setRefreshing(false);

                // Sembunyikan tombol pengaturan server jika siswa sudah login
                updateSettingsButtonVisibilityByDom();
                // Reload 1x setelah dashboard terload sempurna agar file tercache
                reloadDashboardOnceForCache();
            }
        });

        ServiceWorkerController swController = ServiceWorkerController.getInstance();
        swController.setServiceWorkerClient(new ServiceWorkerClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                return super.shouldInterceptRequest(request);
            }
        });
    }

    private void loadServerUrl() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        URL_ONLINE = prefs.getString(KEY_SERVER_URL, "");
    }

    private void saveServerUrl(String url) {
        if (url != null) {
            url = url.trim();
            if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
        }

        final String finalUrl = url;
        // Tampilkan peringatan jika tidak menggunakan HTTPS
        if (finalUrl != null && !finalUrl.startsWith("https://")) {
            new AlertDialog.Builder(this)
                    .setTitle("Peringatan Ujian Offline")
                    .setMessage("Server ini tidak menggunakan HTTPS. Aplikasi Ujian tidak akan bisa berjalan dalam mode OFFLINE.\n\nHTTPS wajib digunakan agar fitur ujian offline berfungsi.")
                    .setPositiveButton("Tetap Simpan", (dialog, which) -> {
                        performSaveAndLoad(finalUrl);
                    })
                    .setNegativeButton("Ganti ke HTTPS", (dialog, which) -> {
                        String httpsUrl = finalUrl.replace("http://", "https://");
                        performSaveAndLoad(httpsUrl);
                    })
                    .setCancelable(false)
                    .show();
        } else {
            performSaveAndLoad(finalUrl);
        }
    }

    private void performSaveAndLoad(String url) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
        URL_ONLINE = url;
        determineServer(); // Jalankan loading server setelah disimpan
    }

    private static final String KEY_CACHED_SERVERS = "cached_servers";
    private static final String TAG = "CBT_DEBUG";

    /**
     * Menu utama Pengaturan (ikon gear).
     * Menampilkan pilihan: Pilih Server, Cek Update, Tentang SAS, Versi.
     */
    private void showSettingsDialog() {
        android.util.Log.d(TAG, "showSettingsDialog (menu utama) called");

        final List<ServerItem> menuItems = new ArrayList<>();
        menuItems.add(new ServerItem("Pilih Server", "Pilih atau ubah server CBT", true));
        menuItems.add(new ServerItem("Cek Update", "Periksa versi aplikasi terbaru", true));
        menuItems.add(new ServerItem("Tentang SAS", "Informasi aplikasi CBT Exambro SAS", true));
        menuItems.add(new ServerItem("Versi", "Detail versi aplikasi saat ini", true));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        TextView titleView = new TextView(this);
        titleView.setText("Menu Pengaturan");
        titleView.setPadding(60, 50, 60, 20);
        titleView.setTextSize(20);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(Color.BLACK);
        builder.setCustomTitle(titleView);

        ListView listView = new ListView(this);
        listView.setDivider(new ColorDrawable(Color.parseColor("#EEEEEE")));
        listView.setDividerHeight(1);

        ServerAdapter adapter = new ServerAdapter(this, menuItems);
        listView.setAdapter(adapter);

        builder.setView(listView);
        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            switch (position) {
                case 0:
                    showServerSelectionMenu();
                    break;
                case 1:
                    checkForUpdate();
                    break;
                case 2:
                    showAboutDialog();
                    break;
                case 3:
                    showVersionDialog();
                    break;
                default:
                    break;
            }
        });

        builder.setNegativeButton("Tutup", (d, w) -> d.dismiss());
        dialog.show();
    }

    /**
     * Dialog pemilihan server (daftar sekolah dari API / cache).
     */
    private void showServerSelectionMenu() {
        android.util.Log.d(TAG, "showServerSelectionMenu called");
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String cachedJson = prefs.getString(KEY_CACHED_SERVERS, null);

        if (cachedJson != null) {
            android.util.Log.d(TAG, "Cache found. Parsing local data...");
            try {
                List<ServerItem> serverList = parseServerJson(cachedJson);
                showServerSelectionDialog(serverList);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Cache corrupted: " + e.getMessage());
                refreshServerList();
            }
        } else {
            android.util.Log.d(TAG, "No cache found. Fetching from server...");
            refreshServerList();
        }
    }

    private void refreshServerList() {
        android.util.Log.d(TAG, "Starting refreshServerList from " + API_URL);
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setMessage("Menghubungkan ke pusat server...")
                .setCancelable(false)
                .show();

        final AtomicReference<String> errorMessage = new AtomicReference<>("");

        new Thread(() -> {
            final String jsonResponse = fetchServerJsonFromServer(errorMessage);
            runOnUiThread(() -> {
                loadingDialog.dismiss();
                if (jsonResponse != null) {
                    android.util.Log.d(TAG, "Data received successfully. Saving to cache.");
                    getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
                            .putString(KEY_CACHED_SERVERS, jsonResponse).apply();
                    
                    try {
                        showServerSelectionDialog(parseServerJson(jsonResponse));
                    } catch (Exception e) {
                        android.util.Log.e(TAG, "Parse error: " + e.getMessage());
                        Toast.makeText(this, "Format data tidak valid.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    android.util.Log.e(TAG, "Fetch failed: " + errorMessage.get());
                    Toast.makeText(MainActivity.this, "Gagal: " + errorMessage.get(), Toast.LENGTH_LONG).show();
                    showServerSelectionDialog(new ArrayList<>());
                }
            });
        }).start();
    }

    private void showServerSelectionDialog(List<ServerItem> servers) {
        android.util.Log.d(TAG, "Displaying Selection Dialog with " + servers.size() + " servers");
        
        // Buat list baru yang menggabungkan server dari API + Opsi khusus
        final List<ServerItem> displayItems = new ArrayList<>(servers);
        displayItems.add(new ServerItem("Input Server Manual", "Ketik alamat server sendiri", true));
        displayItems.add(new ServerItem("Perbarui Daftar Sekolah", "Ambil data terbaru dari pusat", false));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        // Custom Title View
        TextView titleView = new TextView(this);
        titleView.setText("Pilih Server CBT");
        titleView.setPadding(60, 50, 60, 20);
        titleView.setTextSize(20);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(Color.BLACK);
        builder.setCustomTitle(titleView);

        ListView listView = new ListView(this);
        listView.setDivider(new ColorDrawable(Color.parseColor("#EEEEEE")));
        listView.setDividerHeight(1);
        
        ServerAdapter adapter = new ServerAdapter(this, displayItems);
        listView.setAdapter(adapter);

        builder.setView(listView);
        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            ServerItem selected = displayItems.get(position);
            
            if (selected.name.equals("Input Server Manual")) {
                showManualInputDialog();
            } else if (selected.name.equals("Perbarui Daftar Sekolah")) {
                refreshServerList();
            } else {
                android.util.Log.d(TAG, "Server Selected: " + selected.name + " -> " + selected.url);
                saveServerUrl(selectedUrl(selected.url));
                Toast.makeText(MainActivity.this, "Terhubung ke " + selected.name, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Tutup", (d, w) -> d.dismiss());
        dialog.show();
    }

    private String selectedUrl(String url) {
        if (!url.startsWith("http")) return "http://" + url;
        return url;
    }

    private void showManualInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Input Server Manual");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://alamat-cbt.sch.id");
        input.setText(URL_ONLINE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 10);
        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton("Simpan", (dialog, which) -> {
            String newUrl = input.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                saveServerUrl(newUrl);
            }
        });
        builder.setNegativeButton("Kembali", (dialog, which) -> showServerSelectionMenu());
        builder.show();
    }

    // ================= MENU: CEK UPDATE =================

    private void checkForUpdate() {
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setMessage("Memeriksa pembaruan...")
                .setCancelable(false)
                .show();

        new Thread(() -> {
            final String jsonResponse = fetchUpdateJson();
            runOnUiThread(() -> {
                loadingDialog.dismiss();
                if (jsonResponse == null) {
                    Toast.makeText(MainActivity.this, "Gagal memeriksa update. Periksa koneksi internet.", Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    JSONObject obj = new JSONObject(jsonResponse);
                    int latestCode = obj.optInt("versionCode", 0);
                    String latestName = obj.optString("versionName", "");
                    String downloadUrl = obj.optString("url", "");
                    String notes = obj.optString("notes", "");

                    if (latestCode > getAppVersionCode()) {
                        showUpdateAvailableDialog(latestName, downloadUrl, notes);
                    } else {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Aplikasi Terbaru")
                                .setMessage("Anda sudah menggunakan versi terbaru (v" + getAppVersionName() + ").")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Data update tidak valid.", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /**
     * Cek update otomatis saat aplikasi dibuka (senyap, tanpa dialog loading).
     * Jika ada versi baru, dialog update muncul setelah splash hilang.
     */
    private void checkForUpdateSilently() {
        if (updateCheckDone) return;
        updateCheckDone = true;

        new Thread(() -> {
            final String jsonResponse = fetchUpdateJson();
            runOnUiThread(() -> {
                if (jsonResponse == null) return; // gagal/offline → diam saja
                try {
                    JSONObject obj = new JSONObject(jsonResponse);
                    int latestCode = obj.optInt("versionCode", 0);
                    String latestName = obj.optString("versionName", "");
                    String downloadUrl = obj.optString("url", "");
                    String notes = obj.optString("notes", "");
                    if (latestCode > getAppVersionCode()) {
                        showUpdateDialogAfterSplash(latestName, downloadUrl, notes, 0);
                    }
                } catch (Exception ignored) { }
            });
        }).start();
    }

    /**
     * Tampilkan dialog update TAPI tunggu sampai splash screen hilang
     * agar tidak menimpa video splash. Maksimal 10x percobaan (1,5 detik).
     */
    private void showUpdateDialogAfterSplash(String name, String url, String notes, int attempt) {
        boolean splashStillVisible = splashScreen != null
                && splashScreen.getVisibility() == android.view.View.VISIBLE;
        if (splashStillVisible && attempt < 10) {
            splashScreen.postDelayed(() -> showUpdateDialogAfterSplash(name, url, notes, attempt + 1), 1500);
            return;
        }
        if (!isFinishing() && !isDestroyed() && !isInExamMode) {
            showUpdateAvailableDialog(name, url, notes);
        }
    }

    /**
     * Setelah siswa login & dashboard terload sempurna (bukan halaman login),
     * reload halaman 1x saja agar semua file (JS/CSS/service worker) tercache
     * sempurna untuk kebutuhan ujian offline.
     */
    private void reloadDashboardOnceForCache() {
        if (dashboardReloadDone || isInExamMode || webView == null) return;

        String url = webView.getUrl();
        if (url == null || url.startsWith("data:") || url.contains("error.html")) return;

        webView.evaluateJavascript(
                "(function(){" +
                "var hasPw=!!document.querySelector('input[type=password]');" +
                "var t=(document.title||'').toLowerCase();" +
                "var inTitle=t.indexOf('login')>=0||t.indexOf('masuk')>=0;" +
                "return (hasPw||inTitle)?'login':'other';" +
                "})()",
                value -> {
                    if (value == null || "null".equals(value)) return;
                    if (value.contains("other")) {
                        dashboardReloadDone = true;
                        webView.postDelayed(() -> {
                            if (!isInExamMode && !isFinishing()) {
                                android.util.Log.d(TAG, "Reload dashboard 1x untuk caching sempurna");
                                webView.reload();
                            }
                        }, 3000);
                    }
                });
    }

    private String fetchUpdateJson() {
        try {
            URL url = new URL(UPDATE_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "P171-CBT-APP");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return sb.toString();
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void showUpdateAvailableDialog(String latestName, String downloadUrl, String notes) {
        String message = "Versi baru tersedia: v" + latestName + "\n\n" +
                "Versi saat ini: v" + getAppVersionName() + "\n\n" +
                (notes != null && !notes.isEmpty() ? "Catatan:\n" + notes + "\n\n" : "");
        new AlertDialog.Builder(this)
                .setTitle("Update Tersedia")
                .setMessage(message)
                .setPositiveButton("Download & Install", (d, w) -> downloadAndInstallApk(downloadUrl))
                .setNegativeButton("Nanti", null)
                .show();
    }

    // ================= DOWNLOAD & INSTALL APK =================

    private void downloadAndInstallApk(String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "Link download tidak tersedia.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Android 8+ (API 26+): butuh izin "Instal aplikasi tidak dikenal"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canRequestPackageInstalls()) {
            pendingDownloadUrl = url;
            new AlertDialog.Builder(this)
                    .setTitle("Izin Instal Aplikasi")
                    .setMessage("Agar bisa menginstal update secara otomatis, aktifkan izin 'Instal aplikasi tidak dikenal' untuk aplikasi ini.")
                    .setPositiveButton("Buka Pengaturan", (d, w) -> {
                        try {
                            android.content.Intent intent = new android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName()));
                            installPermissionLauncher.launch(intent);
                        } catch (Exception e) {
                            Toast.makeText(this, "Gagal membuka pengaturan izin.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Batal", (d, w) -> pendingDownloadUrl = null)
                    .show();
            return;
        }

        showDownloadProgressDialog(url);
    }

    private boolean canRequestPackageInstalls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        try {
            return getPackageManager().canRequestPackageInstalls();
        } catch (Exception e) {
            return false;
        }
    }

    private void showDownloadProgressDialog(String url) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(8));

        downloadProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        downloadProgressBar.setMax(100);
        downloadProgressBar.setProgress(0);
        layout.addView(downloadProgressBar);

        downloadStatusText = new TextView(this);
        downloadStatusText.setText("Menyiapkan unduhan...");
        downloadStatusText.setPadding(0, dp(12), 0, 0);
        layout.addView(downloadStatusText);

        downloadDialog = new AlertDialog.Builder(this)
                .setTitle("Mengunduh Update")
                .setView(layout)
                .setCancelable(false)
                .show();

        new Thread(() -> {
            File apkFile = downloadApk(url);
            runOnUiThread(() -> {
                if (downloadDialog != null && downloadDialog.isShowing()) downloadDialog.dismiss();
                if (apkFile != null) {
                    // Verifikasi APK sebelum instal: cegah error "paket bentrok" & APK palsu
                    String verifyError = verifyUpdateApk(apkFile);
                    if (verifyError != null) {
                        try { apkFile.delete(); } catch (Exception ignored) { }
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Update Ditolak")
                                .setMessage(verifyError + "\n\nPastikan file APK update di server dibangun dari project yang sama dan ditandatangani dengan file kunci yang sama (cbt-sas.keystore).")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    Toast.makeText(MainActivity.this, "Unduhan selesai. Menginstal...", Toast.LENGTH_SHORT).show();
                    installApk(apkFile);
                } else {
                    Toast.makeText(MainActivity.this, "Gagal mengunduh APK. Coba lagi.", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private File downloadApk(String urlStr) {
        File targetDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (targetDir == null) targetDir = getFilesDir();
        final File apkFile = new File(targetDir, "cbt-exambro-sas-update.apk");
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "P171-CBT-APP");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                runOnUiThread(() -> {
                    if (downloadStatusText != null) downloadStatusText.setText("Gagal (HTTP " + responseCode + ")");
                });
                return null;
            }

            int totalLength = conn.getContentLength();
            InputStream input = conn.getInputStream();
            FileOutputStream output = new FileOutputStream(apkFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (totalLength > 0) {
                    final int percent = (int) ((totalRead * 100) / totalLength);
                    final long mb = totalRead / (1024 * 1024);
                    runOnUiThread(() -> {
                        if (downloadProgressBar != null) downloadProgressBar.setProgress(percent);
                        if (downloadStatusText != null) downloadStatusText.setText("Mengunduh... " + percent + "% (" + mb + " MB)");
                    });
                }
            }
            output.flush();
            output.close();
            input.close();

            if (apkFile.exists() && apkFile.length() > 0) {
                runOnUiThread(() -> {
                    if (downloadStatusText != null) downloadStatusText.setText("Selesai 100%");
                });
                return apkFile;
            }
        } catch (Exception e) {
            runOnUiThread(() -> {
                if (downloadStatusText != null) downloadStatusText.setText("Gagal: " + e.getMessage());
            });
            try { if (apkFile.exists()) apkFile.delete(); } catch (Exception ignored) { }
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    /**
     * Verifikasi APK hasil unduhan SEBELUM diinstal:
     * 1. Nama paket harus sama
     * 2. Versi harus lebih baru (anti-downgrade)
     * 3. Tanda tangan (signature) harus cocok dengan aplikasi terpasang
     *
     * @return null jika valid, atau pesan error jika ada masalah
     */
    @SuppressWarnings("deprecation")
    private String verifyUpdateApk(File apkFile) {
        try {
            // Baca info APK yang baru diunduh
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo apkInfo = getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), flags);
            if (apkInfo == null) {
                return "File APK tidak valid (bukan aplikasi Android).";
            }

            // 1. Cek nama paket
            if (!getPackageName().equals(apkInfo.packageName)) {
                return "Nama paket APK berbeda (" + apkInfo.packageName + ").";
            }

            // 2. Cek versi (anti-downgrade)
            int apkVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? (int) apkInfo.getLongVersionCode()
                    : apkInfo.versionCode;
            if (apkVersion <= getAppVersionCode()) {
                return "Versi APK update (" + apkVersion + ") tidak lebih baru dari versi terpasang (" + getAppVersionCode() + ").";
            }

            // 3. Cek tanda tangan (signature)
            List<Signature> apkSignatures = extractSignatures(apkInfo);
            PackageInfo installedInfo = getPackageManager().getPackageInfo(getPackageName(), flags);
            List<Signature> installedSignatures = extractSignatures(installedInfo);

            if (apkSignatures.isEmpty() || installedSignatures.isEmpty()) {
                return "Tidak dapat membaca tanda tangan APK.";
            }

            boolean match = false;
            for (Signature s1 : apkSignatures) {
                for (Signature s2 : installedSignatures) {
                    if (s1.equals(s2)) { match = true; break; }
                }
                if (match) break;
            }
            if (!match) {
                return "Tanda tangan (signature) APK update TIDAK cocok dengan aplikasi yang terpasang.\n\n" +
                        "APK update kemungkinan dibangun dengan kunci (keystore) yang berbeda.";
            }

            return null; // Valid
        } catch (Exception e) {
            android.util.Log.e(TAG, "verifyUpdateApk error: " + e.getMessage());
            return "Gagal memverifikasi APK: " + e.getMessage();
        }
    }

    @SuppressWarnings("deprecation")
    private List<Signature> extractSignatures(PackageInfo info) {
        List<Signature> result = new ArrayList<>();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
                Signature[] sigs = info.signingInfo.getApkContentsSigners();
                if (sigs != null) for (Signature s : sigs) result.add(s);
            } else if (info.signatures != null) {
                for (Signature s : info.signatures) result.add(s);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "extractSignatures error: " + e.getMessage());
        }
        return result;
    }

    private void installApk(File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, apkFile);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuka installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ================= MENU: TENTANG SAS =================

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Tentang SAS")
                .setMessage(
                        "CBT Exambro SAS\n\n" +
                        "Aplikasi ujian berbasis PWA (Computer Based Test) untuk sekolah.\n\n" +
                        "Fitur:\n" +
                        "• Ujian Online & Offline\n" +
                        "• Mode Ujian Terkunci (Lock Task)\n" +
                        "• Pencegahan Screenshot\n" +
                        "• Pemilihan Server Sekolah\n\n" +
                        "AI Chef by nibunasah © 2026")
                .setPositiveButton("OK", null)
                .show();
    }

    // ================= MENU: VERSI =================

    private void showVersionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Versi Aplikasi")
                .setMessage(
                        "CBT Exambro SAS\n" +
                        "Versi: v" + getAppVersionName() + "\n" +
                        "Kode Versi: " + getAppVersionCode() + "\n" +
                        "Sistem: Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
                .setPositiveButton("OK", null)
                .show();
    }

    private String getAppVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    @SuppressWarnings("deprecation")
    private int getAppVersionCode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            }
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private String fetchServerJsonFromServer(AtomicReference<String> errorRef) {
        try {
            android.util.Log.d(TAG, "Requesting API: " + API_URL);
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "P171-CBT-APP");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int code = conn.getResponseCode();
            android.util.Log.d(TAG, "Response Code: " + code);

            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                String result = sb.toString();
                android.util.Log.d(TAG, "Raw JSON: " + result);
                return result;
            } else {
                errorRef.set("HTTP " + code);
            }
        } catch (java.net.SocketTimeoutException e) {
            errorRef.set("Waktu koneksi habis. Server mungkin sibuk.");
        } catch (Exception e) {
            errorRef.set(e.getMessage());
        }
        return null;
    }

    private List<ServerItem> parseServerJson(String jsonStr) throws Exception {
        List<ServerItem> list = new ArrayList<>();
        JSONObject json = new JSONObject(jsonStr);
        if (json.getBoolean("success")) {
            JSONArray data = json.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject obj = data.getJSONObject(i);
                // Filter: lewati website berstatus Inactive (jangan ditampilkan)
                if (!isWebsiteActive(obj)) continue;
                list.add(new ServerItem(obj.getString("name"), obj.getString("url")));
            }
        }
        return list;
    }

    /**
     * Cek status website dari kolom "status" (tabel websites).
     * - Active/aktif/1/true/yes  => aktif (ditampilkan)
     * - Inactive/nonaktif/0/false => tidak aktif (disembunyikan)
     * - Kolom "status" tidak ada  => dianggap aktif (backward compatible)
     */
    private boolean isWebsiteActive(JSONObject obj) {
        if (!obj.has("status")) return true;
        Object raw = obj.opt("status");
        if (raw == null) return true;
        String s = String.valueOf(raw).trim().toLowerCase();
        if (s.isEmpty()) return true;
        return s.equals("active") || s.equals("aktif")
                || s.equals("1") || s.equals("true") || s.equals("yes") || s.equals("a");
    }

    // --- Custom Adapter untuk Tampilan Pro ---
    private class ServerAdapter extends BaseAdapter {
        private final Context context;
        private final List<ServerItem> items;

        ServerAdapter(Context context, List<ServerItem> items) {
            this.context = context;
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_server, parent, false);
            }

            ServerItem item = items.get(position);
            TextView name = convertView.findViewById(R.id.item_name);
            TextView url = convertView.findViewById(R.id.item_url);
            ImageView icon = convertView.findViewById(R.id.item_icon);

            name.setText(item.name);
            url.setText(item.url);

            // Ganti ikon & warna berdasarkan jenis item
            if (item.name.equals("Input Server Manual")) {
                icon.setImageResource(android.R.drawable.ic_menu_edit);
                name.setTextColor(Color.parseColor("#3b82f6"));
            } else if (item.name.equals("Perbarui Daftar Sekolah") || item.name.equals("Cek Update")) {
                icon.setImageResource(android.R.drawable.ic_popup_sync);
                name.setTextColor(Color.parseColor("#10b981"));
            } else if (item.name.equals("Pilih Server")) {
                icon.setImageResource(android.R.drawable.ic_menu_directions);
                name.setTextColor(Color.parseColor("#0d6efd"));
            } else if (item.name.equals("Tentang SAS")) {
                icon.setImageResource(android.R.drawable.ic_menu_info_details);
                name.setTextColor(Color.parseColor("#7c3aed"));
            } else if (item.name.equals("Versi")) {
                icon.setImageResource(android.R.drawable.ic_menu_manage);
                name.setTextColor(Color.parseColor("#f59e0b"));
            } else {
                icon.setImageResource(android.R.drawable.ic_menu_directions);
                name.setTextColor(Color.BLACK);
            }

            return convertView;
        }
    }

    private static class ServerItem {
        String name;
        String url;
        ServerItem(String n, String u) { this.name = n; this.url = u; }
        ServerItem(String n, String u, boolean isSpecial) { this.name = n; this.url = u; }
    }

    private void determineServer() {
        if (URL_ONLINE == null || URL_ONLINE.isEmpty()) return;

        new Thread(() -> {
            boolean onlineOk = checkUrl(URL_ONLINE);

            runOnUiThread(() -> {
                if (!onlineOk) {
                    webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ONLY);
                    Toast.makeText(MainActivity.this, "Offline: Mengambil data dari memori...", Toast.LENGTH_LONG).show();
                } else {
                    webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                }
                java.util.Map<String, String> extraHeaders = new java.util.HashMap<>();
                extraHeaders.put("X-CBT-SECRET", "P171-Secure-Auth-2024");
                webView.loadUrl(URL_ONLINE, extraHeaders);
            });
        }).start();
    }

    private boolean checkUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            if (connection instanceof HttpsURLConnection) {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, new TrustManager[]{
                        new X509TrustManager() {
                            @SuppressLint("TrustAllX509TrustManager")
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            @SuppressLint("TrustAllX509TrustManager")
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            @SuppressLint("TrustAllX509TrustManager")
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                }, new java.security.SecureRandom());
                ((HttpsURLConnection) connection).setSSLSocketFactory(sc.getSocketFactory());
                ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
            }

            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            return (connection.getResponseCode() < 400);
        } catch (Exception e) {
            return false;
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void setExamMode(boolean examMode) {
            runOnUiThread(() -> {
                if (examMode) {
                    isInExamMode = true;
                    enableLockTask();
                    getWindow().getDecorView().setSystemUiVisibility(
                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    );

                    new android.os.Handler().postDelayed(() -> {
                        if (isInExamMode && !isAppPinned()) {
                            showSecurityWarning();
                        }
                    }, 5000);
                } else {
                    stopLockTask();
                    getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_VISIBLE);
                    if (isInExamMode) {
                        Toast.makeText(MainActivity.this, "Ujian Selesai: Navigasi Terbuka", Toast.LENGTH_SHORT).show();
                    }
                    isInExamMode = false;
                }
            });
        }

        @JavascriptInterface
        public void openSecuritySettings() {
            try {
                android.content.Intent intent = new android.content.Intent("android.credentials.INSTALL_CERTIFICATE");
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                try {
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e2) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Gagal membuka pengaturan.", Toast.LENGTH_LONG).show());
                }
            }
        }

        @JavascriptInterface
        public void retryConnection() {
            runOnUiThread(() -> {
                if (!isNetworkAvailable()) {
                    Toast.makeText(MainActivity.this, "Masih tidak ada koneksi internet.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (URL_ONLINE == null || URL_ONLINE.isEmpty()) {
                    showSettingsDialog();
                    return;
                }

                webView.clearCache(true);
                webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

                java.util.Map<String, String> extraHeaders = new java.util.HashMap<>();
                extraHeaders.put("X-CBT-SECRET", "P171-Secure-Auth-2024");
                webView.loadUrl(URL_ONLINE, extraHeaders);

                webView.postDelayed(() -> webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT), 2000);
            });
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isInExamMode) {
            if (isAppPinned()) {
                if (securityWarningDialog != null && securityWarningDialog.isShowing()) {
                    securityWarningDialog.dismiss();
                    securityWarningDialog = null;
                }
            } else {
                showSecurityWarning();
            }

            enableLockTask();
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        if (!hasFocus && !isAlertShowing) {
            webView.evaluateJavascript("window.dispatchEvent(new Event('blur'));", null);
        }
    }

    private void showSecurityWarning() {
        if (securityWarningDialog != null && securityWarningDialog.isShowing()) return;

        securityWarningDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("KEAMANAN UJIAN")
                .setMessage("Aplikasi WAJIB disematkan (Pinned) untuk mengerjakan ujian.")
                .setCancelable(false)
                .setPositiveButton("KELUAR", (dialog, which) -> {
                    finishAffinity();
                    System.exit(0);
                })
                .setNeutralButton("COBA LAGI", (dialog, which) -> startLockTask())
                .show();
    }

    private void enableLockTask() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                startLockTask();
            } catch (Exception ignored) { }
        }
    }

    private boolean isAppPinned() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        return activityManager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }

    private void checkDeviceRequirements() {
        int minRAM_GB = 2;
        int minChromeVersion = 80;
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();

        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long totalRAM = memoryInfo.totalMem / (1024 * 1024 * 1024);

        if (totalRAM < minRAM_GB) {
            errors.add("- RAM HP kamu terdeteksi " + totalRAM + "GB. Disarankan minimal " + minRAM_GB + "GB.");
        }

        String ua = webView.getSettings().getUserAgentString();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Chrom(?:e|ium)/(\\d+)\\.");
        java.util.regex.Matcher matcher = pattern.matcher(ua);
        int chromeVersion = 0;
        if (matcher.find()) {
            String versionGroup = matcher.group(1);
            if (versionGroup != null) chromeVersion = Integer.parseInt(versionGroup);
        }

        if (chromeVersion > 0 && chromeVersion < minChromeVersion) {
            errors.add("- Versi WebView/Chrome kamu (" + chromeVersion + ") terlalu jadul. Minimal v" + minChromeVersion + ".");
        }

        if (URL_ONLINE != null && !URL_ONLINE.isEmpty() && !URL_ONLINE.startsWith("https://")) {
            errors.add("- Koneksi tidak aman (Bukan HTTPS). Fitur Offline CBT tidak akan jalan.");
        }

        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder("Ujian mungkin terkendala:\n\n");
            for (String error : errors) message.append(error).append("\n");
            new AlertDialog.Builder(this)
                    .setTitle("Peringatan Perangkat")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(message.toString())
                    .setPositiveButton("Saya Mengerti", null)
                    .show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isAlertShowing) {
            webView.evaluateJavascript("window.dispatchEvent(new Event('blur'));", null);
            if (isInExamMode) {
                Toast.makeText(this, "PERINGATAN: Jangan keluar dari aplikasi saat ujian!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkAndHideSplash() {
        runOnUiThread(() -> {
            if (isVideoFinished && pageLoadFinished) {
                if (splashScreen != null && splashScreen.getVisibility() == android.view.View.VISIBLE) {
                    splashScreen.animate().alpha(0f).setDuration(800).withEndAction(() -> {
                        splashScreen.setVisibility(android.view.View.GONE);
                        VideoView v = findViewById(R.id.splash_video);
                        if (v != null) {
                            v.stopPlayback();
                            v.setVisibility(android.view.View.GONE);
                        }
                    }).start();
                }
            }
        });
    }
}