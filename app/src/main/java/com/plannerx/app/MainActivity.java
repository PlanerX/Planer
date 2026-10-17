package com.plannerx.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(11,16,32));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(11,16,32));

        // Creating the channel here also makes Android 13+ ask for notification permission.
        ReminderReceiver.ensureChannel(this);

        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.rgb(11,16,32));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new PlannerBridge(), "PlannerBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    // Only the app's own pages stay inside the WebView; other links open in the browser.
    private boolean handleUrl(String url) {
        if (url.startsWith("file:///android_asset/")) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Throwable ignored) {
        }
        return true;
    }

    /** Functions the web page (index.html) can call as PlannerBridge.xxx() */
    public class PlannerBridge {
        @JavascriptInterface
        public void syncReminders(String json) {
            ReminderReceiver.schedule(getApplicationContext(), json);
        }

        @JavascriptInterface
        public void testNotification() {
            ReminderReceiver.show(getApplicationContext(), "Planner X",
                    "Test reminder: notifications are working.", 9001);
        }

        @JavascriptInterface
        public void openNotificationSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(i);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
