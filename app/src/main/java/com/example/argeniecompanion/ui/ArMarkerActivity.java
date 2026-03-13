package com.example.argeniecompanion.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SizeF;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.ar.FrameBuffer;
import com.example.argeniecompanion.ar.camera.ArCamera2Manager;
import com.example.argeniecompanion.ar.pose.AnnotationRenderItem;
import com.example.argeniecompanion.ar.pose.PoseMemoryStore;
import com.example.argeniecompanion.ar.render.ArAnnotationRenderer;
import com.example.argeniecompanion.ar.sensor.RotationTracker;
import com.example.argeniecompanion.ar.vision.MarkerDetector;
import com.example.argeniecompanion.ar.vision.MarkerDetectionResult;
import com.example.argeniecompanion.ar.vision.PoseEstimator;

import org.opencv.android.OpenCVLoader;

import java.util.List;

/**
 * Host activity for the AR marker-based annotation system.
 *
 * Phase completion:
 *   ✓ Phase 1 — Camera2 preview in GLSurfaceView
 *   ✓ Phase 2 — ArUco detection + solvePnP pose estimation
 *   ✓ Phase 3 — Rotation tracking (TYPE_GAME_ROTATION_VECTOR)
 *   ✓ Phase 4 — Pose memory store + annotation lifecycle (fade-out)
 *   ✓ Phase 5 — Canvas annotation overlay (label box + dot + line)
 */
public class ArMarkerActivity extends AppCompatActivity
        implements ArAnnotationRenderer.SurfaceTextureListener {

    private static final String TAG = "ArMarkerActivity";

    /** Overlay refresh interval during fade animation (≈30 fps). */
    private static final long OVERLAY_REFRESH_MS = 33;

    // ---- Views ----
    private GLSurfaceView         glSurfaceView;
    private AnnotationOverlayView annotationOverlay;
    private TextView              detectionStatusTv;

    // ---- AR components ----
    private final FrameBuffer   frameBuffer    = new FrameBuffer();
    private ArAnnotationRenderer renderer;
    private ArCamera2Manager    camera2Manager;
    private RotationTracker     rotationTracker;
    private MarkerDetector      markerDetector;
    private PoseMemoryStore     poseMemoryStore;

    // Cached so we can reopen the camera on resume without re-entering onSurfaceTextureReady
    private SurfaceTexture cachedSurfaceTexture;
    private double         focalLengthPx = 960.0;
    private boolean        isResumed     = false;

    // ---- Fade-animation handler ----
    private final Handler  mainHandler       = new Handler(Looper.getMainLooper());
    private final Runnable overlayRefreshTask = this::refreshOverlay;

    // ---- Permission launcher ----
    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    initGlSurface();
                } else {
                    Toast.makeText(this,
                            "Camera permission is required for AR marker detection",
                            Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    // ---- Lifecycle ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_marker);

        ImageView backBtn = findViewById(R.id.back_btn);
        backBtn.setOnClickListener(v -> finish());
        backBtn.requestFocus();

        detectionStatusTv   = findViewById(R.id.detection_status_tv);
        annotationOverlay   = findViewById(R.id.annotation_overlay);

        if (!OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV failed to initialize", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        rotationTracker = new RotationTracker(this);
        poseMemoryStore = new PoseMemoryStore();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            initGlSurface();
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        if (glSurfaceView != null) glSurfaceView.onResume();
        rotationTracker.start();

        // Reopen camera on resume if we already have a SurfaceTexture
        if (cachedSurfaceTexture != null && camera2Manager == null) {
            openCameraWithSurface(cachedSurfaceTexture);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
        mainHandler.removeCallbacks(overlayRefreshTask);
        if (glSurfaceView != null) glSurfaceView.onPause();
        rotationTracker.stop();
        closeCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (glSurfaceView != null) {
            glSurfaceView.queueEvent(() -> {
                if (renderer != null) renderer.release();
            });
        }
    }

    // ---- GL setup ----

    private void initGlSurface() {
        glSurfaceView = findViewById(R.id.gl_surface_view);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setPreserveEGLContextOnPause(true);

        renderer = new ArAnnotationRenderer(glSurfaceView);
        renderer.setSurfaceTextureListener(this);

        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    // ---- ArAnnotationRenderer.SurfaceTextureListener ----

    @Override
    public void onSurfaceTextureReady(SurfaceTexture surfaceTexture) {
        // Called on the GL thread — switch to main thread for Camera2
        runOnUiThread(() -> openCameraWithSurface(surfaceTexture));
    }

    // ---- Camera + detector startup ----

    private void openCameraWithSurface(SurfaceTexture surfaceTexture) {
        cachedSurfaceTexture = surfaceTexture;

        focalLengthPx = estimateFocalLengthPx();

        poseMemoryStore.setCameraIntrinsics(
                focalLengthPx,
                ArCamera2Manager.PREVIEW_WIDTH  / 2.0,
                ArCamera2Manager.PREVIEW_HEIGHT / 2.0);

        camera2Manager = new ArCamera2Manager(this, frameBuffer);
        camera2Manager.open(surfaceTexture);

        PoseEstimator poseEstimator = new PoseEstimator(
                focalLengthPx,
                ArCamera2Manager.PREVIEW_WIDTH  / 2.0,
                ArCamera2Manager.PREVIEW_HEIGHT / 2.0);

        markerDetector = new MarkerDetector(frameBuffer, poseEstimator, this::onMarkersDetected);
        markerDetector.start();
    }

    private void closeCamera() {
        if (markerDetector  != null) { markerDetector.stop();  markerDetector  = null; }
        if (camera2Manager  != null) { camera2Manager.close(); camera2Manager  = null; }
    }

    // ---- Detection callback (called from vision thread) ----

    private void onMarkersDetected(List<MarkerDetectionResult> results) {
        // Switch to main thread — PoseMemoryStore and View updates must be on main thread
        runOnUiThread(() -> {
            poseMemoryStore.updateDetections(results, rotationTracker.getSnapshot());
            refreshOverlay();
            scheduleOverlayRefresh(); // keep refreshing for fade animation

            // Update status label
            int n = results.size();
            String msg = n + " marker" + (n == 1 ? "" : "s") + " detected";
            detectionStatusTv.setText(msg);
            detectionStatusTv.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_green_light));
            Log.d(TAG, msg + " — IDs: " + markerIdsString(results));
        });
    }

    // ---- Overlay refresh ----

    /** Rebuild the overlay render list from current pose memory and redraw. */
    private void refreshOverlay() {
        if (!isResumed || glSurfaceView == null) return;

        int w = glSurfaceView.getWidth();
        int h = glSurfaceView.getHeight();
        if (w == 0 || h == 0) return;

        List<AnnotationRenderItem> items =
                poseMemoryStore.buildRenderList(rotationTracker.getSnapshot(), w, h);
        annotationOverlay.setRenderItems(items);

        // Reset status when all annotations have faded
        if (!poseMemoryStore.hasActiveAnnotations()) {
            detectionStatusTv.setText("Searching…");
            detectionStatusTv.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_orange_light));
        }
    }

    /**
     * Keep the overlay refreshing at ~30 fps while annotations are present
     * so fade-out animates smoothly even between detection callbacks.
     */
    private void scheduleOverlayRefresh() {
        mainHandler.removeCallbacks(overlayRefreshTask);
        if (poseMemoryStore.hasActiveAnnotations() && isResumed) {
            mainHandler.postDelayed(overlayRefreshTask, OVERLAY_REFRESH_MS);
        }
    }

    // ---- Camera intrinsics ----

    private double estimateFocalLengthPx() {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;

                float[] focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                SizeF   sensorSize   = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);

                if (focalLengths != null && focalLengths.length > 0 && sensorSize != null) {
                    double fPx = (focalLengths[0] / sensorSize.getWidth())
                               * ArCamera2Manager.PREVIEW_WIDTH;
                    Log.d(TAG, String.format("Focal length: %.1f mm → %.1f px",
                            focalLengths[0], fPx));
                    return fPx;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read camera characteristics", e);
        }
        // Fallback: 80° horizontal FoV approximation
        double fallback = (ArCamera2Manager.PREVIEW_WIDTH / 2.0) / Math.tan(Math.toRadians(40));
        Log.d(TAG, "Using fallback focal length: " + (int) fallback + " px");
        return fallback;
    }

    // ---- Utilities ----

    private static String markerIdsString(List<MarkerDetectionResult> results) {
        StringBuilder sb = new StringBuilder();
        for (MarkerDetectionResult r : results) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.markerId);
        }
        return sb.toString();
    }
}
