package com.example.argeniecompanion.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.logger.AppLogger;

import me.relex.photodraweeview.PhotoDraweeView;

/**
 * Full-screen document viewer fragment.
 *
 * <ul>
 *   <li>Images  → Fresco {@link PhotoDraweeView} with pinch-to-zoom
 *   <li>PDFs    → WebView using Google Docs viewer for remote URLs
 *   <li>Other   → File info screen with "Open in App" via system Intent
 * </ul>
 *
 * Create via {@link #newInstance(String, String, String)}.
 */
public class DocumentViewerFragment extends Fragment {

    private static final String TAG = DocumentViewerFragment.class.getSimpleName();

    public static final String ARG_URL       = "doc_url";
    public static final String ARG_MIME_TYPE = "doc_mime_type";
    public static final String ARG_FILE_NAME = "doc_file_name";

    private String url;
    private String mimeType;
    private String fileName;

    private WebView         webView;
    private PhotoDraweeView imageView;
    private LinearLayout    fileInfoView;
    private FrameLayout     loadingView;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static DocumentViewerFragment newInstance(String url, String mimeType, String fileName) {
        DocumentViewerFragment fragment = new DocumentViewerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putString(ARG_MIME_TYPE, mimeType);
        args.putString(ARG_FILE_NAME, fileName);
        fragment.setArguments(args);
        return fragment;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            url      = getArguments().getString(ARG_URL, "");
            mimeType = getArguments().getString(ARG_MIME_TYPE, "");
            fileName = getArguments().getString(ARG_FILE_NAME, "Document");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_document_viewer, container, false);

        ImageView closeBtn      = root.findViewById(R.id.viewer_close_btn);
        TextView  fileNameTv    = root.findViewById(R.id.viewer_file_name_tv);
        TextView  fileTypeBadge = root.findViewById(R.id.viewer_file_type_badge);
        webView      = root.findViewById(R.id.viewer_webview);
        imageView    = root.findViewById(R.id.viewer_image);
        fileInfoView = root.findViewById(R.id.viewer_file_info);
        loadingView  = root.findViewById(R.id.viewer_loading);

        fileNameTv.setText(fileName);

        closeBtn.setOnClickListener(v -> close());

        // Intercept hardware back key
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        root.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                close();
                return true;
            }
            return false;
        });

        if (mimeType.startsWith("image/")) {
            fileTypeBadge.setText("IMG");
            fileTypeBadge.setVisibility(View.VISIBLE);
            showImageViewer();
        } else if ("application/pdf".equals(mimeType)) {
            fileTypeBadge.setText("PDF");
            fileTypeBadge.setVisibility(View.VISIBLE);
            showPdfViewer();
        } else {
            fileTypeBadge.setText(labelForMime(mimeType));
            fileTypeBadge.setVisibility(View.VISIBLE);
            showFileInfoViewer(root);
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
    }

    // -------------------------------------------------------------------------
    // Viewer modes
    // -------------------------------------------------------------------------

    /** Displays the image using Fresco's PhotoDraweeView (supports pinch-to-zoom). */
    private void showImageViewer() {
        if (url == null || url.isEmpty()) {
            AppLogger.w(TAG, "showImageViewer: empty URL");
            showFileInfoFallback();
            return;
        }

        imageView.setVisibility(View.VISIBLE);

        try {
            // PhotoDraweeView.setPhotoUri handles Fresco controller setup and zoom internally.
            imageView.setPhotoUri(Uri.parse(url));
        } catch (Exception e) {
            AppLogger.e(TAG, "showImageViewer error", e);
            showFileInfoFallback();
        }
    }

    /** Loads a remote PDF via Google Docs viewer inside a WebView. */
    private void showPdfViewer() {
        if (url == null || url.isEmpty()) {
            AppLogger.w(TAG, "showPdfViewer: empty URL");
            showFileInfoFallback();
            return;
        }

        loadingView.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                if (loadingView != null) loadingView.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                AppLogger.e(TAG, "PDF WebView error: " + description);
                if (loadingView != null) loadingView.setVisibility(View.GONE);
            }
        });

        String viewerUrl = "https://docs.google.com/viewer?url=" + Uri.encode(url);
        webView.loadUrl(viewerUrl);
    }

    /** Shows file metadata and an "Open in App" button for unsupported types. */
    private void showFileInfoViewer(View root) {
        fileInfoView.setVisibility(View.VISIBLE);

        ImageView fileIcon = root.findViewById(R.id.viewer_file_icon);
        TextView  nameTv   = root.findViewById(R.id.viewer_info_name_tv);
        TextView  mimeTv   = root.findViewById(R.id.viewer_info_mime_tv);
        TextView  openBtn  = root.findViewById(R.id.viewer_open_external_btn);

        if (mimeType.startsWith("image/")) {
            fileIcon.setImageResource(R.drawable.photo_library_24px);
        } else if (mimeType.startsWith("video/")) {
            fileIcon.setImageResource(R.drawable.video_library_24px);
        } else {
            fileIcon.setImageResource(R.drawable.docs_24px);
        }

        nameTv.setText(fileName);
        mimeTv.setText(mimeType);

        openBtn.setOnClickListener(v -> openExternally());
    }

    private void showFileInfoFallback() {
        if (!isAdded()) return;
        imageView.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        loadingView.setVisibility(View.GONE);
        fileInfoView.setVisibility(View.VISIBLE);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void openExternally() {
        if (url == null || url.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            AppLogger.e(TAG, "openExternally failed", e);
        }
    }

    private void close() {
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    private static String labelForMime(String mime) {
        if (mime == null)                                   return "FILE";
        if (mime.startsWith("image/"))                      return "IMG";
        if ("application/pdf".equals(mime))                 return "PDF";
        if (mime.contains("word") || mime.contains("document")) return "DOC";
        return "FILE";
    }
}
