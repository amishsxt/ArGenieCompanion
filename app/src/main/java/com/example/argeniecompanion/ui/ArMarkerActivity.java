package com.example.argeniecompanion.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
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
 * Component wiring:
 *   Camera2  →  FrameBuffer  →  MarkerDetector  →  [Phase 4: AnnotationManager]
 *   Camera2  →  SurfaceTexture  →  ArAnnotationRenderer  →  GLSurfaceView
 *   SensorManager  →  RotationTracker  →  [Phase 4: AnnotationManager]
 *
 * Phase completion status:
 *   ✓ Phase 1 — Camera2 preview in GLSurfaceView
 *   ✓ Phase 2 — ArUco detection + solvePnP pose estimation
 *   ✓ Phase 3 — Rotation tracking (TYPE_GAME_ROTATION_VECTOR)
 *   ○ Phase 4 — Pose memory store + annotation lifecycle (next)
 *   ○ Phase 5 — Billboard quad annotation rendering (next)
 */
public class ArMarkerActivity extends AppCompatActivity
        implements ArAnnotationRenderer.SurfaceTextureListener {

    private static final String TAG = "ArMarkerActivity";

    // ---- Views ----
    private GLSurfaceView glSurfaceView;
    private TextView      detectionStatusTv;

    // ---- AR components ----
    private final FrameBuffer    frameBuffer    = new FrameBuffer();
    private ArAnnotationRenderer renderer;
    private ArCamera2Manager     camera2Manager;
    private RotationTracker      rotationTracker;
    private MarkerDetector       markerDetector;

    // Held so we can reopen the camera on resume without waiting for onSurfaceTextureReady again
    private SurfaceTexture cachedSurfaceTexture;

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

        detectionStatusTv = findViewById(R.id.detection_status_tv);

        // Load OpenCV native library (synchronous in 4.5.1+)
        if (!OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV failed to initialize", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        rotationTracker = new RotationTracker(this);

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
        if (glSurfaceView != null) glSurfaceView.onResume();
        rotationTracker.start();

        // If we already have a SurfaceTexture (resume after pause), reopen camera directly
        if (cachedSurfaceTexture != null && camera2Manager == null) {
            openCameraWithSurface(cachedSurfaceTexture);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) glSurfaceView.onPause();
        rotationTracker.stop();
        closeCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (glSurfaceView != null) {
            // Release GL objects on the GL thread
            glSurfaceView.queueEvent(() -> {
                if (renderer != null) renderer.release();
            });
        }
    }

    // ---- GL setup ----

    private void initGlSurface() {
        glSurfaceView = findViewById(R.id.gl_surface_view);
        glSurfaceView.setEGLContextClientVersion(2);
        // Preserve the EGL context on pause so we don't recreate the OES texture every resume
        glSurfaceView.setPreserveEGLContextOnPause(true);

        renderer = new ArAnnotationRenderer(glSurfaceView);
        renderer.setSurfaceTextureListener(this);

        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    // ---- ArAnnotationRenderer.SurfaceTextureListener ----

    /**
     * Called once from the GL thread when the OES SurfaceTexture is created.
     * We must open Camera2 from the main thread, so post to UI thread.
     */
    @Override
    public void onSurfaceTextureReady(SurfaceTexture surfaceTexture) {
        runOnUiThread(() -> openCameraWithSurface(surfaceTexture));
    }

    // ---- Camera + detector startup ----

    private void openCameraWithSurface(SurfaceTexture surfaceTexture) {
        cachedSurfaceTexture = surfaceTexture;

        camera2Manager = new ArCamera2Manager(this, frameBuffer);
        camera2Manager.open(surfaceTexture);

        // Build PoseEstimator with focal length derived from Camera2 intrinsics
        double focalLengthPx = estimateFocalLengthPx();
        PoseEstimator poseEstimator = new PoseEstimator(
                focalLengthPx,
                ArCamera2Manager.PREVIEW_WIDTH  / 2.0,   // cx
                ArCamera2Manager.PREVIEW_HEIGHT / 2.0);  // cy

        markerDetector = new MarkerDetector(frameBuffer, poseEstimator, this::onMarkersDetected);
        markerDetector.start();
    }

    private void closeCamera() {
        if (markerDetector != null) { markerDetector.stop();  markerDetector  = null; }
        if (camera2Manager != null) { camera2Manager.close(); camera2Manager  = null; }
    }

    // ---- Detection callback ----

    private void onMarkersDetected(List<MarkerDetectionResult> results) {
        // TODO (Phase 4): forward to AnnotationManager → PoseMemoryStore
        runOnUiThread(() -> {
            String msg = results.size() + " marker" + (results.size() == 1 ? "" : "s") + " detected";
            detectionStatusTv.setText(msg);
            detectionStatusTv.setTextColor(
                    ContextCompat.getColor(this, android.R.color.holo_green_light));
            Log.d(TAG, msg + " — IDs: " + markerIdsString(results));
        });
    }

    // ---- Camera intrinsics helper ----

    /**
     * Estimates focal length in pixels from Camera2 lens characteristics.
     *
     * Formula: fx = (focalLengthMm / sensorWidthMm) * imageWidthPx
     *
     * Falls back to a reasonable default (80° horizontal FoV approximation) if
     * characteristics are unavailable.
     */
    private double estimateFocalLengthPx() {
        try {
            CameraManager manager =
                    (CameraManager) getSystemService(CAMERA_SERVICE);
            // Use the first back-facing camera (same selection as ArCamera2Manager)
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;

                float[] focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                SizeF   sensorSize   = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);

                if (focalLengths != null && focalLengths.length > 0 && sensorSize != null) {
                    double focalMm     = focalLengths[0];
                    double sensorWMm   = sensorSize.getWidth();
                    double focalPx     = (focalMm / sensorWMm) * ArCamera2Manager.PREVIEW_WIDTH;
                    Log.d(TAG, String.format("Focal length: %.1f mm → %.1f px", focalMm, focalPx));
                    return focalPx;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read camera characteristics, using default focal length", e);
        }

        // Fallback: assumes ~80° horizontal FoV
        // fx = (width / 2) / tan(FoV/2)  →  1280/2 / tan(40°) ≈ 762
        double defaultFx = (ArCamera2Manager.PREVIEW_WIDTH / 2.0) / Math.tan(Math.toRadians(40));
        Log.d(TAG, "Using default focal length: " + (int) defaultFx + " px");
        return defaultFx;
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
