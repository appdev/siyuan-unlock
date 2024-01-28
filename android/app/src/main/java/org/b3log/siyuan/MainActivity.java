/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.siyuan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.StringUtils;
import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.util.Charsets;
import com.zackratos.ultimatebarx.ultimatebarx.java.UltimateBarX;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.TimeZone;

import mobile.Mobile;

/**
 * 主程序.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.6.1, Jan 28, 2024
 * @since 1.0.0
 */
public class MainActivity extends AppCompatActivity implements com.blankj.utilcode.util.Utils.OnAppStatusChangedListener {

    private AsyncHttpServer server;
    private int serverPort = 6906;
    private WebView webView;
    private ImageView bootLogo;
    private ProgressBar bootProgressBar;
    private TextView bootDetailsText;
    private String webViewVer;
    private String userAgent;
    private ValueCallback<Uri[]> uploadMessage;
    private static final int REQUEST_SELECT_FILE = 100;

    @Override
    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (null != webView) {
            final String blockURL = intent.getStringExtra("blockURL");
            if (!StringUtils.isEmpty(blockURL)) {
                webView.evaluateJavascript("javascript:window.openFileByURL('" + blockURL + "')", null);
            }
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.i("boot", "create main activity");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 启动 HTTP Server
        startHttpServer();

        // 初始化 UI 元素
        initUIElements();

        // 拉起内核
        startKernel();

        // 初始化外观资源
        initAppearance();

        AppUtils.registerAppStatusChangedListener(this);

        // 使用 Chromium 调试 WebView
        // WebView.setWebContentsDebuggingEnabled(true);

        // 注册工具栏显示/隐藏跟随软键盘状态
        // Fix https://github.com/siyuan-note/siyuan/issues/9765
        Utils.registerSoftKeyboardToolbar(this, webView);

        // 沉浸式状态栏设置
        UltimateBarX.statusBarOnly(this).transparent().light(false).color(Color.parseColor("#1e1e1e")).apply();
        ((ViewGroup) webView.getParent()).setPadding(0, UltimateBarX.getStatusBarHeight(), 0, 0);

        // Fix https://github.com/siyuan-note/siyuan/issues/9726
        // KeyboardUtils.fixAndroidBug5497(this);
        AndroidBug5497Workaround.assistActivity(this);
    }

    private void initUIElements() {
        bootLogo = findViewById(R.id.bootLogo);
        bootProgressBar = findViewById(R.id.progressBar);
        bootDetailsText = findViewById(R.id.bootDetails);
        webView = findViewById(R.id.webView);
        webView.setBackgroundColor(Color.parseColor("#1e1e1e"));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(final WebView mWebView, final ValueCallback<Uri[]> filePathCallback, final FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                }
                uploadMessage = filePathCallback;
                final Intent intent = fileChooserParams.createIntent();
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (final Exception e) {
                    uploadMessage = null;
                    Toast.makeText(getApplicationContext(), "Cannot open file chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }

        });

        webView.setOnDragListener((v, event) -> {
            // 禁用拖拽 https://github.com/siyuan-note/siyuan/issues/6436
            return DragEvent.ACTION_DRAG_ENDED != event.getAction();
        });

        final WebSettings ws = webView.getSettings();
        checkWebViewVer(ws);
        userAgent = ws.getUserAgentString();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showBootIndex() {
        webView.setVisibility(View.VISIBLE);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, final WebResourceRequest request) {
                final Uri uri = request.getUrl();
                final String url = uri.toString();
                if (url.contains("127.0.0.1")) {
                    view.loadUrl(url);
                    return true;
                }

                if (url.contains("siyuan://api/system/exit")) {
                    exit();
                    return true;
                }

                if (uri.getScheme().toLowerCase().startsWith("http")) {
                    final Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(i);
                    return true;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                new Handler().postDelayed(() -> {
                    bootLogo.setVisibility(View.GONE);
                    bootProgressBar.setVisibility(View.GONE);
                    bootDetailsText.setVisibility(View.GONE);
                    final ImageView bootLogo = findViewById(R.id.bootLogo);
                    bootLogo.setVisibility(View.GONE);
                }, 666);
            }
        });

        final JSAndroid JSAndroid = new JSAndroid(this);
        webView.addJavascriptInterface(JSAndroid, "JSAndroid");
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        final WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        ws.setTextZoom(100);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUserAgentString("SiYuan/" + Utils.version + " https://b3log.org/siyuan Android " + ws.getUserAgentString());

        waitFotKernelHttpServing();
        webView.loadUrl("http://127.0.0.1:6806/appearance/boot/index.html?v=" + Utils.version);

        new Thread(this::keepLive).start();
    }

    private Handler bootHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(final Message msg) {
            final String cmd = msg.getData().getString("cmd");
            if ("startKernel".equals(cmd)) {
                bootKernel();
            } else {
                showBootIndex();
            }
        }
    };

    private void startHttpServer() {
        if (null != server) {
            server.stop();
        }

        try {
            // 解决乱码问题 https://github.com/koush/AndroidAsync/issues/656#issuecomment-523325452
            final Class<Charsets> charsetClass = Charsets.class;
            Field usAscii = charsetClass.getDeclaredField("US_ASCII");
            usAscii.setAccessible(true);
            usAscii.set(Charsets.class, Charsets.UTF_8);
        } catch (final Exception e) {
            Utils.LogError("http", "init charset failed", e);
        }

        server = new AsyncHttpServer();
        server.post("/api/walkDir", (request, response) -> {
            try {
                final long start = System.currentTimeMillis();
                final JSONObject requestJSON = (JSONObject) request.getBody().get();
                final String dir = requestJSON.optString("dir");
                final JSONObject data = new JSONObject();
                final JSONArray files = new JSONArray();
                FileUtils.listFilesAndDirs(new File(dir), TrueFileFilter.INSTANCE, DirectoryFileFilter.DIRECTORY).forEach(file -> {
                    final String path = file.getAbsolutePath();
                    final JSONObject info = new JSONObject();
                    try {
                        info.put("path", path);
                        info.put("name", file.getName());
                        info.put("size", file.length());
                        info.put("updated", file.lastModified());
                        info.put("isDir", file.isDirectory());
                    } catch (final Exception e) {
                        Utils.LogError("http", "walk dir failed", e);
                    }
                    files.put(info);
                });
                data.put("files", files);
                final JSONObject responseJSON = new JSONObject().put("code", 0).put("msg", "").put("data", data);
                response.send(responseJSON);
                Utils.LogInfo("http", "walk dir [" + dir + "] in [" + (System.currentTimeMillis() - start) + "] ms");
            } catch (final Exception e) {
                Utils.LogError("http", "walk dir failed", e);
                try {
                    response.send(new JSONObject().put("code", -1).put("msg", e.getMessage()));
                } catch (final Exception e2) {
                    Utils.LogError("http", "walk dir failed", e2);
                }
            }
        });

        serverPort = getAvailablePort();
        final AsyncServer s = AsyncServer.getDefault();
        // 生产环境绑定 ipv6 回环地址 [::1] 以防止被远程访问
        s.listen(InetAddress.getLoopbackAddress(), serverPort, server.getListenCallback());
        // 开发环境绑定所有网卡以便调试
        //s.listen(null, serverPort, server.getListenCallback());
        Utils.LogInfo("http", "HTTP server is listening on port [" + serverPort + "]");
    }

    private int getAvailablePort() {
        int ret = 6906;
        try {
            ServerSocket s = new ServerSocket(0);
            ret = s.getLocalPort();
            s.close();
        } catch (final Exception e) {
            Utils.LogError("http", "get available port failed", e);
        }
        return ret;
    }

    private void startKernel() {
        final Bundle b = new Bundle();
        b.putString("cmd", "startKernel");
        final Message msg = new Message();
        msg.setData(b);
        bootHandler.sendMessage(msg);
    }

    private void bootKernel() {
        Mobile.setHttpServerPort(serverPort);
        if (Mobile.isHttpServing()) {
            Utils.LogInfo("boot", "kernel HTTP server is running");
            showBootIndex();
            return;
        }

        final String appDir = getFilesDir().getAbsolutePath() + "/app";
        final Locale locale = getResources().getConfiguration().locale;
        final String workspaceBaseDir = getExternalFilesDir(null).getAbsolutePath();
        final String timezone = TimeZone.getDefault().getID();
        new Thread(() -> {
            final String localIPs = Utils.getIPAddressList();
            String lang = locale.getLanguage() + "_" + locale.getCountry();
            if (lang.toLowerCase().contains("cn")) {
                lang = "zh_CN";
            } else {
                lang = "en_US";
            }

            Mobile.startKernel("android", appDir, workspaceBaseDir, timezone, localIPs, lang,
                    Build.VERSION.RELEASE +
                            "/SDK " + Build.VERSION.SDK_INT +
                            "/WebView " + webViewVer +
                            "/Manufacturer " + android.os.Build.MANUFACTURER +
                            "/Brand " + android.os.Build.BRAND +
                            "/UA " + userAgent);
        }).start();

        final Bundle b = new Bundle();
        b.putString("cmd", "bootIndex");
        final Message msg = new Message();
        msg.setData(b);
        bootHandler.sendMessage(msg);
    }

    /**
     * 通知栏保活。
     */
    private void keepLive() {
        while (true) {
            try {
                final Intent intent = new Intent(MainActivity.this, KeepLiveService.class);
                ContextCompat.startForegroundService(this, intent);
                sleep(45 * 1000);
                stopService(intent);
            } catch (final Throwable t) {
            }
        }
    }

    /**
     * 等待内核 HTTP 服务伺服。
     */
    private void waitFotKernelHttpServing() {
        while (true) {
            sleep(10);
            if (Mobile.isHttpServing()) {
                break;
            }
        }
    }

    private void initAppearance() {
        if (needUnzipAssets()) {
            bootLogo.setVisibility(View.VISIBLE);
            // 不要进度条更平滑一些
            //bootProgressBar.setVisibility(View.VISIBLE);
            //bootDetailsText.setVisibility(View.VISIBLE);

            final String dataDir = getFilesDir().getAbsolutePath();
            final String appDir = dataDir + "/app";
            final File appVerFile = new File(appDir, "VERSION");

            setBootProgress("Clearing appearance...", 20);
            try {
                FileUtils.deleteDirectory(new File(appDir));
            } catch (final Exception e) {
                Utils.LogError("boot", "delete dir [" + appDir + "] failed, exit application", e);
                exit();
                return;
            }

            setBootProgress("Initializing appearance...", 60);
            Utils.unzipAsset(getAssets(), "app.zip", appDir + "/app");

            try {
                FileUtils.writeStringToFile(appVerFile, Utils.version, StandardCharsets.UTF_8);
            } catch (final Exception e) {
                Utils.LogError("boot", "write version failed", e);
            }

            setBootProgress("Booting kernel...", 80);
        }
    }

    private void setBootProgress(final String text, final int progressPercent) {
        runOnUiThread(() -> {
            bootDetailsText.setText(text);
            bootProgressBar.setProgress(progressPercent);
        });
    }

    private void sleep(final long time) {
        try {
            Thread.sleep(time);
        } catch (final Exception e) {
            Utils.LogError("runtime", "sleep failed", e);
        }
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("javascript:window.goBack ? window.goBack() : window.history.back()", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (null == uploadMessage) {
            super.onActivityResult(requestCode, resultCode, intent);
            return;
        }

        // 以下代码参考自 https://github.com/mgks/os-fileup/blob/master/app/src/main/java/mgks/os/fileup/MainActivity.java MIT license
        if (requestCode == REQUEST_SELECT_FILE) {
            Uri[] results = null;
            ClipData clipData;
            String stringData;

            try {
                clipData = intent.getClipData();
                stringData = intent.getDataString();
            } catch (Exception e) {
                clipData = null;
                stringData = null;
            }

            if (clipData != null) {
                final int numSelectedFiles = clipData.getItemCount();
                results = new Uri[numSelectedFiles];
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    results[i] = clipData.getItemAt(i).getUri();
                }
            } else {
                try {
                    Bitmap cam_photo = (Bitmap) intent.getExtras().get("data");
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    cam_photo.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
                    stringData = MediaStore.Images.Media.insertImage(this.getContentResolver(), cam_photo, null, null);
                } catch (Exception ignored) {
                }

                if (!StringUtils.isEmpty(stringData)) {
                    results = new Uri[]{Uri.parse(stringData)};
                }
            }

            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    private boolean needUnzipAssets() {
        final String dataDir = getFilesDir().getAbsolutePath();
        final String appDir = dataDir + "/app";
        final File appDirFile = new File(appDir);
        appDirFile.mkdirs();

        boolean ret = true;
        final File appVerFile = new File(appDir, "VERSION");
        if (appVerFile.exists()) {
            try {
                final String ver = FileUtils.readFileToString(appVerFile, StandardCharsets.UTF_8);
                ret = !ver.equals(Utils.version);
            } catch (final Exception e) {
                Utils.LogError("boot", "check version failed", e);
            }
        }
        return ret;
    }

    @Override
    protected void onDestroy() {
        Log.i("boot", "destroy main activity");
        super.onDestroy();
        KeyboardUtils.unregisterSoftInputChangedListener(getWindow());
        AppUtils.unregisterAppStatusChangedListener(this);
        if (null != webView) {
            webView.removeAllViews();
            webView.destroy();
        }
        if (null != server) {
            server.stop();
        }
    }

    @Override
    public void onForeground(Activity activity) {
        startSyncData();
        if (null != webView) {
            webView.evaluateJavascript("javascript:window.reconnectWebSocket()", null);
        }
    }

    @Override
    public void onBackground(Activity activity) {
        startSyncData();
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
    }

    private void exit() {
        finishAffinity();
        finishAndRemoveTask();
    }

    private void checkWebViewVer(final WebSettings ws) {
        // Android check WebView version 75+ https://github.com/siyuan-note/siyuan/issues/7840
        final String ua = ws.getUserAgentString();
        if (ua.contains("Chrome/")) {
            final int minVer = 75;
            try {
                final String chromeVersion = ua.split("Chrome/")[1].split(" ")[0];
                if (chromeVersion.contains(".")) {
                    final String[] chromeVersionParts = chromeVersion.split("\\.");
                    webViewVer = chromeVersionParts[0];
                    if (Integer.parseInt(webViewVer) < minVer) {
                        Toast.makeText(this, "WebView version [" + chromeVersion + "] is too low, please upgrade to " + minVer + "+", Toast.LENGTH_LONG).show();
                    }
                }
            } catch (final Exception e) {
                Utils.LogError("boot", "check webview version failed", e);
                Toast.makeText(this, "Check WebView version failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private static boolean syncing;

    public static void startSyncData() {
        new Thread(MainActivity::syncData).start();
    }

    public static void syncData() {
        try {
            if (syncing) {
                Log.i("sync", "data is syncing...");
                return;
            }
            syncing = true;

            final AsyncHttpPost req = new com.koushikdutta.async.http.AsyncHttpPost("http://127.0.0.1:6806/api/sync/performSync");
            req.setBody(new JSONObjectBody(new JSONObject().put("mobileSwitch", true)));
            AsyncHttpClient.getDefaultInstance().executeJSONObject(req,
                    new com.koushikdutta.async.http.AsyncHttpClient.JSONObjectCallback() {
                        @Override
                        public void onCompleted(Exception e, com.koushikdutta.async.http.AsyncHttpResponse source, JSONObject result) {
                            if (null != e) {
                                Utils.LogError("sync", "data sync failed", e);
                            }
                        }
                    });
        } catch (final Throwable e) {
            Utils.LogError("sync", "data sync failed", e);
        } finally {
            syncing = false;
        }
    }
}
