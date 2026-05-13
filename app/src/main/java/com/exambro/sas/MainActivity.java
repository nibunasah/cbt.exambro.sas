package cbt.exambro.sas; // GANTI SESUAI PACKAGE NAME ANDA

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
import android.text.InputType;
import android.content.SharedPreferences;
import android.app.ActivityManager;
import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import android.net.NetworkInfo;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageButton btnSettings;
    private String URL_ONLINE;
    private static final String PREF_NAME = "CBTPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private boolean isAlertShowing = false;
    private boolean isInExamMode = false;
    private AlertDialog securityWarningDialog;
    private android.view.View splashScreen;

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
                        // Jika tidak ada history, biarkan default Back (bisa keluar app)
                        setEnabled(false);
                        MainActivity.super.onBackPressed();
                        setEnabled(true);
                    }
                }
            }
        });

        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Jika sedang ujian dan offline, paksa buka dari cache saat ditarik (refresh)
            if (isInExamMode) {
                webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            } else {
                webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            }
            webView.reload();
        });
        // 3. Setup Splash Screen
        splashScreen = findViewById(R.id.splash_screen);
        btnSettings = findViewById(R.id.btn_settings);

        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // 4. Konfigurasi WebView LEBIH AWAL (Penting!)
        setupWebView();

        // ANTI-CACHE TRAPPING: Bersihkan cache saat app dibuka agar tidak terjebak di halaman download
        webView.clearCache(true);

        // 5. Load Server (Cek koneksi tetap dilakukan untuk logging/toast, tapi biarkan WebView mencoba load)
        loadServerUrl();

        if (URL_ONLINE == null || URL_ONLINE.isEmpty()) {
            // Sembunyikan splash screen agar pesan terlihat
            if (splashScreen != null) splashScreen.setVisibility(android.view.View.GONE);

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
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // Wajib untuk Dexie.js
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT); // Kembali ke default agar tidak ERR_CACHE_MISS
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // IDENTITAS: Ambil Android ID dan pasang di User Agent
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
                        .setTitle("CBT P171")
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
                // Jangan blokir navigasi secara paksa di sini agar Service Worker punya kesempatan.
                // Masalah koneksi akan ditangani secara cerdas di onReceivedError.
                return false;
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // KEAMANAN: Batalkan koneksi jika ada masalah SSL
                handler.cancel();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    String url = request.getUrl().toString();
                    int code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? error.getErrorCode() : -1;

                    // STRATEGI ANTI-TRAP: Jika gagal load, coba lari ke Cache dulu.
                    // Jangan langsung menyerah ke error.html.

                    // Strike 2: Jika kita SUDAH mencoba mode CACHE_ONLY dan masih error, artinya benar-benar tidak ada data.
                    if (view.getSettings().getCacheMode() == WebSettings.LOAD_CACHE_ONLY) {
                        runOnUiThread(() -> {
                            view.setAlpha(1.0f);
                            // KHUSUS LOGIN: Baru tampilkan error.html jika cache pun gagal
                            if (url.equals(URL_ONLINE) || url.equals(URL_ONLINE + "/") || url.contains("index.php")) {
                                view.stopLoading();
                                view.clearHistory();
                                view.loadUrl("file:///android_asset/error.html");
                            } else {
                                // Selain login (Dashboard/Ujian), arahkan ke dashboard saja dari cache (Last resort)
                                view.loadUrl(URL_ONLINE + "/siswa/index.php");
                            }
                        });
                        return;
                    }

                    // Strike 1: Terjadi error koneksi (Disconnected, Timeout, dll)
                    if (code == ERROR_CONNECT || code == ERROR_HOST_LOOKUP || code == ERROR_TIMEOUT || code == -2 || code == -6) {
                        runOnUiThread(() -> {
                            // Paksa WebView baca dari cache saja (Memberi kesempatan PWA/ServiceWorker)
                            view.setAlpha(0.1f);
                            view.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ONLY);

                            if (url.contains("ujian.php")) {
                                String cleanUrl = url.split("\\?")[0];
                                view.loadUrl(cleanUrl);
                            } else if (url.contains("index.php") || url.equals(URL_ONLINE) || url.equals(URL_ONLINE + "/")) {
                                view.loadUrl(url); // Coba load login dari cache
                            } else {
                                // Default fallback ke dashboard daripada stuck atau Chrome Error
                                view.loadUrl(URL_ONLINE + "/siswa/index.php");
                            }
                        });
                    }

                    // Sembunyikan splash screen
                    runOnUiThread(() -> {
                        if (splashScreen != null && splashScreen.getVisibility() == android.view.View.VISIBLE) {
                            splashScreen.animate().alpha(0f).setDuration(500).withEndAction(() -> splashScreen.setVisibility(android.view.View.GONE)).start();
                        }
                    });
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.animate().alpha(1f).setDuration(200).start();

                // Jika halaman pertama selesai dimuat, hilangkan splash screen
                if (splashScreen != null && splashScreen.getVisibility() == android.view.View.VISIBLE) {
                    splashScreen.animate().alpha(0f).setDuration(800).withEndAction(() -> splashScreen.setVisibility(android.view.View.GONE)).start();
                }

                // Jika sedang ujian, gunakan mode yang lebih "berani" baca cache
                if (isInExamMode) {
                    view.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                } else {
                    view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                }
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        // 3. Khusus Service Worker Configuration (Android 24+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            ServiceWorkerController swController = ServiceWorkerController.getInstance();
            swController.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public android.webkit.WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return super.shouldInterceptRequest(request);
                }
            });
        }
    }

    private void loadServerUrl() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        URL_ONLINE = prefs.getString(KEY_SERVER_URL, "");
    }

    private void saveServerUrl(String url) {
        if (url != null) {
            url = url.trim();
            // Auto prefix http if missing
            if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
        }
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
        URL_ONLINE = url;
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pengaturan Server");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("Contoh: https://cbt.p171.net");
        input.setText(URL_ONLINE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);
        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton("Simpan", (dialog, which) -> {
            String newUrl = input.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                saveServerUrl(newUrl);
                Toast.makeText(MainActivity.this, "Server disimpan: " + URL_ONLINE, Toast.LENGTH_SHORT).show();
                determineServer();
            } else {
                Toast.makeText(MainActivity.this, "URL tidak boleh kosong!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Batal", (dialog, which) -> {
            if (URL_ONLINE == null || URL_ONLINE.isEmpty()) {
                Toast.makeText(MainActivity.this, "Server belum diatur!", Toast.LENGTH_SHORT).show();
            }
            dialog.cancel();
        });

        builder.show();
    }

    private void determineServer() {
        if (URL_ONLINE == null || URL_ONLINE.isEmpty()) return;

        new Thread(() -> {
            boolean onlineOk = checkUrl(URL_ONLINE);

            runOnUiThread(() -> {
                if (!onlineOk) {
                    // Jika offline, JANGAN ganti URL ke IP Lokal.
                    // Tetap di origin cbt.exambro.cbt.exambro.sas.exambro.cbt.exambro.sas.p171.net agar Service Worker bisa baca cache-nya.
                    webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ONLY);
                    Toast.makeText(MainActivity.this, "Offline: Mengambil data dari memori...", Toast.LENGTH_LONG).show();
                } else {
                    webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                }
                // Tambahkan Header Keamanan khusus agar server tahu ini benar-benar aplikasi resmi
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

            // SSL Bypass untuk cek koneksi ke IP lokal if HTTPS
            if (connection instanceof HttpsURLConnection) {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                }, new java.security.SecureRandom());
                ((HttpsURLConnection) connection).setSSLSocketFactory(sc.getSocketFactory());
                ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
            }

            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            return (connection.getResponseCode() < 400); // 200, 301, 302 ok
        } catch (Exception e) {
            return false;
        }
    }

    // Fungsi yang bisa dipanggil dari PHP/JS di Server
    public class WebAppInterface {
        @JavascriptInterface
        public void setExamMode(boolean examMode) {
            android.util.Log.d("CBT_DEBUG", "Exam Mode request: " + examMode + " (Current: " + isInExamMode + ")");

            runOnUiThread(() -> {
                if (examMode) {
                    isInExamMode = true;
                    try {
                        enableLockTask();
                        // Masuk ke Mode Fullscreen (Immersive Mode)
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

                    } catch (Exception e) {
                        android.util.Log.e("CBT_LOCK", "Gagal startLockTask: " + e.getMessage());
                    }
                } else {
                    try {
                        stopLockTask();
                        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_VISIBLE);

                        // Hanya tampilkan toast jika sebelumnya sedang dalam mode ujian
                        if (isInExamMode) {
                            Toast.makeText(MainActivity.this, "Ujian Selesai: Navigasi Terbuka", Toast.LENGTH_SHORT).show();
                        }
                        isInExamMode = false;
                    } catch (Exception e) {
                        android.util.Log.e("CBT_LOCK", "Gagal stopLockTask: " + e.getMessage());
                    }
                }
            });
        }

        @JavascriptInterface
        public void openSecuritySettings() {
            try {
                // Mencoba membuka menu instalasi sertifikat secara langsung
                android.content.Intent intent = new android.content.Intent("android.credentials.INSTALL_CERTIFICATE");
                // Jika intent di atas gagal di versi android tertentu, fallback ke security settings
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                try {
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e2) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Gagal membuka pengaturan. Silakan buka secara manual.", Toast.LENGTH_LONG).show());
                }
            }
        }


        @JavascriptInterface
        public void retryConnection() {
            runOnUiThread(() -> {
                // PENGAMAN: Cek internet sebelum mencoba reload
                if (!isNetworkAvailable()) {
                    Toast.makeText(MainActivity.this, "Masih tidak ada koneksi internet.", Toast.LENGTH_SHORT).show();
                    return; // Tetap di error.html
                }

                if (URL_ONLINE == null || URL_ONLINE.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Alamat server belum diatur!", Toast.LENGTH_SHORT).show();
                    showSettingsDialog();
                    return;
                }

                // ANTI-CACHE logic: Clear cache for the main URL and set mode to NO_CACHE temporarily
                webView.clearCache(true);
                webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

                // Reload the main URL
                java.util.Map<String, String> extraHeaders = new java.util.HashMap<>();
                extraHeaders.put("X-CBT-SECRET", "P171-Secure-Auth-2024");
                webView.loadUrl(URL_ONLINE, extraHeaders);

                webView.postDelayed(() -> {
                    webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                }, 2000);
            });
        }
    }


    // 3. KEAMANAN: Kunci Tombol Home/Recent (Kiosk Mode)
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isInExamMode) {
            if (isAppPinned()) {
                // Jika sudah di-pin, tutup peringatan jika ada
                if (securityWarningDialog != null && securityWarningDialog.isShowing()) {
                    securityWarningDialog.dismiss();
                    securityWarningDialog = null;
                }
            } else {
                // User kembali fokus tapi tidak di-pin (Artinya menolak atau un-pinned)
                showSecurityWarning();
            }

            try {
                // Re-apply Lock Task (Hanya jika belum locked)
                enableLockTask();
                // Re-apply Immersive Mode
                getWindow().getDecorView().setSystemUiVisibility(
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                                | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            } catch (Exception e) { }
        }

        if (!hasFocus) {
            if (!isAlertShowing) {
                webView.evaluateJavascript("window.dispatchEvent(new Event('blur'));", null);
            }
        }
    }

    // Menampilkan pesan peringatan keamanan jika tidak di-pin
    private void showSecurityWarning() {
        if (securityWarningDialog != null && securityWarningDialog.isShowing()) return;

        securityWarningDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("KEAMANAN UJIAN")
                .setMessage("Aplikasi WAJIB disematkan (Pinned) untuk mengerjakan ujian. Karena Anda tidak mengizinkan atau melepas sematan, aplikasi akan ditutup demi keamanan.")
                .setCancelable(false)
                .setPositiveButton("KELUAR", (dialog, which) -> {
                    finishAffinity();
                    System.exit(0);
                })
                .setNeutralButton("COBA LAGI", (dialog, which) -> {
                    startLockTask();
                })
                .show();
    }

    // Fungsi untuk mengaktifkan Lock Task dengan pengecekan (Mencegah Loop di Android 10)
    private void enableLockTask() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activityManager.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                try {
                    startLockTask();
                } catch (Exception e) {
                    android.util.Log.e("CBT_LOCK", "Gagal startLockTask: " + e.getMessage());
                }
            }
        } else {
            try {
                startLockTask();
            } catch (Exception e) { }
        }
    }

    // Helper untuk cek apakah aplikasi sedang di-pin (Lock Task Mode)
    private boolean isAppPinned() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return activityManager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
        }
        return false; // Versi lama abaikan saja atau implementasi lain
    }

    // Helper: Cek apakah internet tersedia
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    // Pengecekan Perangkat
    private void checkDeviceRequirements() {
        int minRAM_GB = 2;
        int minChromeVersion = 80;
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();

        // 1. Cek RAM
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long totalRAM = memoryInfo.totalMem / (1024 * 1024 * 1024); // GB

        if (totalRAM < minRAM_GB) {
            errors.add("- RAM HP kamu terdeteksi " + totalRAM + "GB. Disarankan minimal " + minRAM_GB + "GB agar tidak lag saat putar video.");
        }

        // 2. Cek Versi Chrome/WebView
        String ua = webView.getSettings().getUserAgentString();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Chrom(?:e|ium)/(\\d+)\\.");
        java.util.regex.Matcher matcher = pattern.matcher(ua);
        int chromeVersion = 0;
        if (matcher.find()) {
            chromeVersion = Integer.parseInt(matcher.group(1));
        }

        if (chromeVersion > 0 && chromeVersion < minChromeVersion) {
            errors.add("- Versi WebView/Chrome kamu (" + chromeVersion + ") terlalu jadul. Minimal v" + minChromeVersion + ".");
        }

        // 3. Cek HTTPS
        if (URL_ONLINE != null && !URL_ONLINE.isEmpty() && !URL_ONLINE.startsWith("https://")) {
            errors.add("- Koneksi tidak aman (Bukan HTTPS). Fitur Offline CBT tidak akan jalan.");
        }

        // Tampilkan Dialog jika ada masalah
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder("Ujian mungkin terkendala karena HP kamu tidak memenuhi syarat:\n\n");
            for (String error : errors) {
                message.append(error).append("\n");
            }
            message.append("\nSaran: Update Google Chrome di Play Store dan tutup aplikasi lain yang sedang berjalan.");

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
        // Saat APK di-minimize, trigger blur hanya jika tidak sedang ada alert
        if (!isAlertShowing) {
            webView.evaluateJavascript("window.dispatchEvent(new Event('blur'));", null);

            // FAIL-SAFE: Jika sedang dalam mode ujian, tampilkan Toast peringatan secara asli
            // Ini berguna jika koneksi mati/browser hang sehingga JS tidak merespons
            if (isInExamMode) {
                Toast.makeText(this, "PERINGATAN: Jangan keluar dari aplikasi saat ujian!", Toast.LENGTH_LONG).show();
            }
        }
    }

}