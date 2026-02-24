package com.example.argeniecompanion.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.graphics.pdf.PdfRenderer;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.logger.AppLogger;
import com.example.argeniecompanion.model.ChatMessage;

import me.relex.photodraweeview.PhotoDraweeView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Full-screen document viewer fragment.
 *
 * <ul>
 *   <li>Images  → Fresco {@link PhotoDraweeView} (pan + zoom)</li>
 *   <li>PDFs    → {@link PdfViewportView} rendered via {@link PdfRenderer};
 *                 D-pad UP/DOWN scrolls within a page, edges trigger page navigation</li>
 *   <li>Others  → file info card with an "Open with…" intent</li>
 * </ul>
 *
 * <p>Wire D-pad into this fragment from
 * {@code MainActivity.dispatchKeyEvent()} by calling {@link #handleNavKey(int)}.</p>
 */
public class DocumentViewerFragment extends Fragment {

    private static final String TAG = DocumentViewerFragment.class.getSimpleName();

    /** Display pixels scrolled per D-pad press in PDF mode. */
    private static final int PDF_SCROLL_PX = 220;

    private final ChatMessage message;

    // ── Views ──────────────────────────────────────────────────────────────────
    private View            loadingOverlay;
    private PhotoDraweeView imageView;
    private PdfViewportView pdfView;
    private View            otherLayout;
    private ImageView       backBtn;
    private TextView        titleTv;
    private TextView        pageInfoTv;

    // ── PDF state ──────────────────────────────────────────────────────────────
    private PdfRenderer pdfRenderer;
    private int         currentPage = 0;
    private int         pageCount   = 0;
    private File        pdfCacheFile;
    /** Prevents concurrent page renders. */
    private volatile boolean rendering = false;

    // ── Constructor ────────────────────────────────────────────────────────────

    public DocumentViewerFragment(@NonNull ChatMessage message) {
        this.message = message;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_document_viewer, container, false);

        loadingOverlay = view.findViewById(R.id.doc_viewer_loading);
        imageView      = view.findViewById(R.id.doc_viewer_image);
        pdfView        = view.findViewById(R.id.doc_viewer_pdf);
        otherLayout    = view.findViewById(R.id.doc_viewer_other);
        backBtn        = view.findViewById(R.id.doc_viewer_back_btn);
        titleTv        = view.findViewById(R.id.doc_viewer_title);
        pageInfoTv     = view.findViewById(R.id.doc_viewer_page_info);

        backBtn.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        // Title from URL last path segment
        String rawUrl  = message.getMessage();
        String segment = Uri.parse(rawUrl).getLastPathSegment();
        titleTv.setText((segment != null && !segment.isEmpty())
                ? segment
                : message.fileTypeLabel() + " file");

        String mime = message.getMimeType();
        if (mime.startsWith("image/")) {
            showImage(rawUrl);
        } else if ("application/pdf".equals(mime)) {
            startPdfDownload(rawUrl);
        } else {
            showOther();
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        closePdfRenderer();
        if (pdfCacheFile != null && pdfCacheFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pdfCacheFile.delete();
        }
    }

    // ── Image ──────────────────────────────────────────────────────────────────

    private void showImage(String url) {
        imageView.setVisibility(View.VISIBLE);
        imageView.setPhotoUri(Uri.parse(url));
    }

    // ── PDF ────────────────────────────────────────────────────────────────────

    private void startPdfDownload(String url) {
        loadingOverlay.setVisibility(View.VISIBLE);

        pdfView.setScrollCallback(new PdfViewportView.ScrollCallback() {
            @Override public void onTopEdgeReached()    { navigatePage(-1); }
            @Override public void onBottomEdgeReached() { navigatePage(+1); }
        });

        new Thread(() -> {
            try {
                File cacheDir = requireContext().getCacheDir();
                pdfCacheFile = File.createTempFile("viewer_", ".pdf", cacheDir);

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(30_000);
                conn.connect();

                try (InputStream in  = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(pdfCacheFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }

                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                        pdfCacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(pfd);
                pageCount   = pdfRenderer.getPageCount();
                currentPage = 0;

                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    loadingOverlay.setVisibility(View.GONE);
                    pdfView.setVisibility(View.VISIBLE);
                    pageInfoTv.setVisibility(View.VISIBLE);
                    // Render after layout so getWidth() is valid
                    pdfView.post(() -> renderPageAsync(0, true));
                });

            } catch (Exception e) {
                AppLogger.e(TAG, "PDF download error", e);
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    loadingOverlay.setVisibility(View.GONE);
                    showOther();
                });
            }
        }).start();
    }

    private void navigatePage(int delta) {
        int next = currentPage + delta;
        if (next < 0 || next >= pageCount) return;
        currentPage = next;
        renderPageAsync(currentPage, delta > 0);
    }

    private void renderPageAsync(int pageIndex, boolean fromTop) {
        if (rendering) return;
        rendering = true;

        new Thread(() -> {
            Bitmap bitmap = renderPageBitmap(pageIndex);
            requireActivity().runOnUiThread(() -> {
                rendering = false;
                if (!isAdded() || bitmap == null) return;
                if (fromTop) pdfView.showPageFromTop(bitmap);
                else         pdfView.showPageFromBottom(bitmap);
                pageInfoTv.setText((currentPage + 1) + " / " + pageCount);
            });
        }).start();
    }

    @Nullable
    private Bitmap renderPageBitmap(int pageIndex) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pageCount) return null;
        try (PdfRenderer.Page page = pdfRenderer.openPage(pageIndex)) {
            int w = pdfView.getWidth();
            if (w == 0) w = 1280;
            int h = (int) ((float) page.getHeight() / page.getWidth() * w);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            new Canvas(bmp).drawColor(Color.WHITE);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bmp;
        } catch (Exception e) {
            AppLogger.e(TAG, "renderPageBitmap error", e);
            return null;
        }
    }

    private void closePdfRenderer() {
        if (pdfRenderer != null) {
            try { pdfRenderer.close(); } catch (Exception ignored) {}
            pdfRenderer = null;
        }
    }

    // ── Other / fallback ───────────────────────────────────────────────────────

    private void showOther() {
        otherLayout.setVisibility(View.VISIBLE);

        String rawUrl  = message.getMessage();
        String segment = Uri.parse(rawUrl).getLastPathSegment();
        String displayName = (segment != null && !segment.isEmpty())
                ? segment
                : message.fileTypeLabel() + " file";

        ((TextView) otherLayout.findViewById(R.id.doc_viewer_file_name)).setText(displayName);
        ((TextView) otherLayout.findViewById(R.id.doc_viewer_mime_tv)).setText(message.getMimeType());

        // Icon based on type
        ImageView icon = otherLayout.findViewById(R.id.doc_viewer_file_icon);
        String mime = message.getMimeType();
        if (mime.startsWith("video/")) {
            icon.setImageResource(R.drawable.video_library_24px);
        } else if (mime.startsWith("image/")) {
            icon.setImageResource(R.drawable.photo_library_24px);
        } else {
            icon.setImageResource(R.drawable.docs_24px);
        }

        Button openBtn = otherLayout.findViewById(R.id.doc_viewer_open_btn);
        openBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(rawUrl), mime);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(),
                        "No app found to open this file", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── D-pad handling (called from MainActivity.dispatchKeyEvent) ─────────────

    /**
     * Returns {@code true} if the key was consumed.
     * In PDF mode: UP/DOWN scroll the viewport (with page navigation at edges).
     */
    public boolean handleNavKey(int keyCode) {
        if (pdfView.getVisibility() != View.VISIBLE) return false;

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            pdfView.smoothScroll(+PDF_SCROLL_PX);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            pdfView.smoothScroll(-PDF_SCROLL_PX);
            return true;
        }
        return false;
    }
}
