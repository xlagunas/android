/**
 * Copyright (c) 2016 Mattermost, Inc. All Rights Reserved.
 * See License.txt for license information.
 */
package net.opentrends.mattermost;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.KeyEvent;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import net.opentrends.model.User;
import net.opentrends.service.IResultListener;
import net.opentrends.service.MattermostService;
import net.opentrends.service.Promise;


import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;


public class MainActivity extends WebViewActivity {

    WebView webView;
    Uri appUri;

    String senderID;
    GoogleCloudMessaging gcm;
    ProgressDialog dialog;
    long timeAway;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        appUri = Uri.parse(service.getBaseUrl());

        webView = (WebView) findViewById(R.id.web_view);

        initProgressBar(R.id.webViewProgress);
        initWebView(webView);
    }

    protected void loadRootView() {
        String url = service.getBaseUrl();
        if (!url.endsWith("/"))
            url += "/";
        url += "channels/town-square";
        webView.loadUrl(url);

        dialog = new ProgressDialog(this);
        dialog.setMessage(this.getText(R.string.loading));
        dialog.setCancelable(false);
        dialog.show();
    }

    @Override
    protected void onPause() {
        Log.i("MainActivity", "paused");
        webView.onPause();
        webView.pauseTimers();
        timeAway = System.currentTimeMillis();
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.i("MainActivity", "resumed");
        webView.onResume();
        webView.resumeTimers();

        if ((System.currentTimeMillis() - timeAway) > 1000 * 60 * 5) {
            loadRootView();
        }

        super.onResume();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    protected void setWebViewClient(WebView view) {
        view.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                dialog.hide();
                Log.i("onPageFinished", "onPageFinished while loading");
                Log.i("onPageFinished", url);

                if (url.equals("about:blank")) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                    alert.setTitle(R.string.error_retry);

                    alert.setPositiveButton(R.string.error_logout, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            MainActivity.this.onLogout();
                        }
                    });

                    alert.setNegativeButton(R.string.error_refresh, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            MainActivity.this.loadRootView();
                        }
                    });

                    alert.show();
                }

                // Check to see if we need to attach the device Id
                if (url.toLowerCase().endsWith("/channels/town-square")) {
                    if (isLoggedIn() && !MattermostService.service.isAttached()) {
                        Log.i("MainActivity", "Attempting to attach device id");
                        MattermostService.service.init(MattermostService.service.getBaseUrl());
                        MattermostService.service.attachDevice()
                                .then(new IResultListener<User>() {
                                    @Override
                                    public void onResult(Promise<User> promise) {
                                        if (promise.getError() != null) {
                                            Log.e("AttachDeviceId", promise.getError());
                                        } else {
                                            Log.i("AttachDeviceId", "Attached device_id to session");
                                            MattermostService.service.SetAttached();
                                        }
                                    }
                                });
                    }
                }
            }

//            @Override
//            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
//                Log.e("onReceivedError", "onReceivedError while loading");
//                Log.e("onReceivedError", error.getDescription().toString() + " " + error.getErrorCode());
//            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                Log.e("onReceivedHttpError", "onReceivedHttpError while loading");
                StringBuilder total = new StringBuilder();

                if (errorResponse.getData() != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(errorResponse.getData()));
                    String line;
                    try {
                        while ((line = r.readLine()) != null) {
                            total.append(line);
                        }
                    } catch (IOException e) {
                        total.append("failed to read data");
                    }
                } else {
                    total.append("no data");
                }

                Log.e("onReceivedHttpError", total.toString());
            }

            @Override
            public void onReceivedError (WebView view, int errorCode, String description, String failingUrl) {
                Log.e("onReceivedErrord", "onReceivedError while loading (d)");
                Log.e("onReceivedErrord", errorCode + " " + description + " " + failingUrl);
                webView.loadUrl("about:blank");
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);

                if (!isLoggedIn()) {
                    return false;
                }

                if (!uri.getHost().equalsIgnoreCase(appUri.getHost())) {
                    openUrl(uri);
                    return true;
                }

                if (uri.getPath().startsWith("/static/help")) {
                    openUrl(uri);
                    return true;
                }

                if (uri.getPath().startsWith("/api/v1/files/get/")) {
                    openUrl(uri);
                    return true;
                }

                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                // Check to see if the user was trying to logout
                if (url.toLowerCase().endsWith("/logout")) {
                    MattermostApplication.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            onLogout();
                        }
                    });
                }

                String baseUrl = "";
                int i = service.getBaseUrl().lastIndexOf("/");
                if (i != -1) {
                    baseUrl = service.getBaseUrl().substring(0, i);

                }

                // If you're at the root then logout and so the select team view
                if (url.toLowerCase().endsWith(baseUrl + "/") || url.toLowerCase().endsWith(baseUrl)) {
                    MattermostApplication.handler.post(new Runnable() {
                        @Override
                        public void run() {
                            onLogout();
                        }
                    });
                }

                return super.shouldInterceptRequest(view, url);
            }
        });
    }

    private void openUrl(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    private boolean isLoggedIn() {
        String baseUrl = service.getBaseUrl();
        if (baseUrl == null) {
            return false;
        }

        String cookies = CookieManager.getInstance().getCookie(baseUrl);
        if (cookies == null)
            return false;
        if (cookies.trim().isEmpty())
            return false;
        if (!cookies.contains("MMTOKEN"))
            return false;
        return true;
    }

    @Override
    protected void onLogout() {
        Log.i("MainActivity", "onLogout called");
        super.onLogout();

        MattermostService.service.logout();

        Intent intent = new Intent(this, SelectTeamActivity.class);
        startActivityForResult(intent, SelectTeamActivity.START_CODE);
        finish();
    }
}
