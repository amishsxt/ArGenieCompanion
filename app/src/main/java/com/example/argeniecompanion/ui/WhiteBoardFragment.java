package com.example.argeniecompanion.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.argeniecompanion.R;

public class WhiteBoardFragment extends Fragment {

    private final String url;

    private WebView webView;
    private int zoomSteps = 0;

    public WhiteBoardFragment(@NonNull String url) {
        this.url = url;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_white_board, container, false);

        ImageView backBtn = view.findViewById(R.id.whiteboard_back_btn);
        backBtn.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        webView = view.findViewById(R.id.whiteboard_web_view);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(url);

        return view;
    }
}
