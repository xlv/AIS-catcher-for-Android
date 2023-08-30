package com.jvdegithub.aiscatcher.ui.main;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;

import com.jvdegithub.aiscatcher.MainActivity;
import com.jvdegithub.aiscatcher.R;
import com.jvdegithub.aiscatcher.tools.LogBook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class WebViewMapFragment extends Fragment {

    private WebView webView;
    private LogBook logbook;

    public static WebViewMapFragment newInstance() {
        return new WebViewMapFragment();
    }

    private boolean isOnline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        logbook = LogBook.getInstance();

        View rootView = inflater.inflate(R.layout.fragment_map, container, false);
        webView = rootView.findViewById(R.id.webmap);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        if(!isOnline()) {
            RelativeLayout offlineBar = rootView.findViewById(R.id.offline_bar);
            offlineBar.setVisibility(View.VISIBLE);
            logbook.addLog("Device is offline.");
        }
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                if (url.startsWith("https://cdn.jsdelivr.net/npm/") || url.startsWith("https://unpkg.com/")) {
                    // Determine the prefix based on which one is matched
                    String prefix = url.startsWith("https://cdn.jsdelivr.net/npm/") ? "https://cdn.jsdelivr.net/npm/" : "https://unpkg.com/";

                    // Remove the prefix to get the remaining path
                    String remainingPath = url.substring(prefix.length());

                    try {
                        // Load the local asset using AssetManager
                        InputStream inputStream = getContext().getAssets().open(remainingPath);

                        // Determine the appropriate content type
                        String contentType;
                        if (remainingPath.endsWith(".css")) {
                            contentType = "text/css";
                        } else {
                            // Handle other content types if needed
                            contentType = "text/plain"; // Default to plain text
                        }

                        WebResourceResponse response = new WebResourceResponse(contentType, "UTF-8", inputStream);
                        logbook.addLog("Loaded locally : " + remainingPath);

                        return response;
                    } catch (IOException e) {
                        logbook.addLog("Cannot load " + remainingPath);
                    }
                }

                // Return null for other URLs to load them normally
                return null;
            }
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                webView.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                webView.setVisibility(View.VISIBLE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                logbook.addLog(String.format("W(%d): %s",
                        consoleMessage.lineNumber(), consoleMessage.message() ));

                return true;
            }
        });

        String url = "http://localhost:" + MainActivity.port + "?welcome=false&android=true";

        int currentNightMode = AppCompatDelegate.getDefaultNightMode();
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
            url += "&dark_mode=true";
        else
            url += "&dark_mode=false";
        webView.loadUrl(url);
        logbook.addLog("Opening: " + url);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        webView.stopLoading();
        logbook.addLog("View is destroyed.");    }
}