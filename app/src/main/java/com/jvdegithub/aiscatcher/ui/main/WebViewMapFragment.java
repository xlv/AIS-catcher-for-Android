package com.jvdegithub.aiscatcher.ui.main;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.jvdegithub.aiscatcher.MainActivity;
import com.jvdegithub.aiscatcher.R;
import com.jvdegithub.aiscatcher.tools.LogBook;

public class WebViewMapFragment extends Fragment {

    private WebView webView;
    private LogBook logbook;

    public static WebViewMapFragment newInstance() {
        return new WebViewMapFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        logbook = LogBook.getInstance();

        View rootView = inflater.inflate(R.layout.fragment_map, container, false);
        webView = rootView.findViewById(R.id.webmap);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient());

        /*
        Activity activity = getActivity();
        if (activity != null) {
            webView.addJavascriptInterface(new WebAppInterface(activity), "Android");
        }
        */

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                logbook.addLog(String.format("WV: %s (l:%d)",
                        consoleMessage.message(), consoleMessage.lineNumber() ));

                return true;
            }
        });


        webView.loadUrl("http://localhost:" + MainActivity.port + "?welcome=false&android=true");

        return rootView;
    }
}