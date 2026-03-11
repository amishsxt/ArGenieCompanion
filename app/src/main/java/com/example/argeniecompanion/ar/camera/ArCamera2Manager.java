package com.example.argeniecompanion.ar.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.example.argeniecompanion.ar.FrameBuffer;

import java.util.Arrays;

/**
 * Manages a Camera2 session that feeds two surfaces in parallel:
 *  1. A SurfaceTexture (OES) used by the GL renderer for the live camera background.
 *  2. An ImageReader (YUV_420_888) used by the vision thread for ArUco detection.
 *
 * Preview resolution: 1280×720 (high quality for GL display).
 * Vision resolution:  640×480  (lower resolution for faster ArUco processing).
 */
public class ArCamera2Manager {

    private static final String TAG = "ArCamera2Manager";

    // GL preview buffer size
    public static final int PREVIEW_WIDTH  = 1280;
    public static final int PREVIEW_HEIGHT = 720;

    // Vision processing resolution
    private static final int YUV_WIDTH  = 640;
    private static final int YUV_HEIGHT = 480;

    private final Context context;
    private final FrameBuffer frameBuffer;

    private CameraDevice       cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader        imageReader;

    private HandlerThread cameraThread;
    private Handler       cameraHandler;

    public ArCamera2Manager(Context context, FrameBuffer frameBuffer) {
        this.context     = context;
        this.frameBuffer = frameBuffer;
    }

    /**
     * Open the camera and start streaming.
     *
     * @param previewSurfaceTexture the OES SurfaceTexture created by the GL renderer.
     *                              Must be called from the main thread (Camera2 requirement).
     */
    @SuppressLint("MissingPermission")
    public void open(SurfaceTexture previewSurfaceTexture) {
        cameraThread = new HandlerThread("ArCamera2Thread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String cameraId = selectCameraId(manager);
        if (cameraId == null) {
            Log.e(TAG, "No camera found on this device");
            return;
        }

        previewSurfaceTexture.setDefaultBufferSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);

        imageReader = ImageReader.newInstance(YUV_WIDTH, YUV_HEIGHT,
                ImageFormat.YUV_420_888, /*maxImages=*/2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                frameBuffer.offer(image); // FrameBuffer takes ownership; closes old frame if any
            }
        }, cameraHandler);

        try {
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    createCaptureSession(previewSurfaceTexture);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    Log.w(TAG, "Camera disconnected");
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e(TAG, "Camera error: " + error);
                    camera.close();
                    cameraDevice = null;
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to open camera", e);
        }
    }

    private void createCaptureSession(SurfaceTexture previewSurfaceTexture) {
        Surface previewSurface = new Surface(previewSurfaceTexture);
        Surface yuvSurface     = imageReader.getSurface();

        try {
            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, yuvSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            startRepeating(previewSurface, yuvSurface);
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Capture session configuration failed");
                        }
                    },
                    cameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create capture session", e);
        }
    }

    private void startRepeating(Surface previewSurface, Surface yuvSurface) {
        try {
            CaptureRequest.Builder builder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            builder.addTarget(yuvSurface);
            builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            captureSession.setRepeatingRequest(builder.build(), null, cameraHandler);
            Log.d(TAG, "Camera streaming started");
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to start repeating request", e);
        }
    }

    /** Prefer back-facing camera; fall back to first available. */
    private String selectCameraId(CameraManager manager) {
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id;
                }
            }
            String[] ids = manager.getCameraIdList();
            return ids.length > 0 ? ids[0] : null;
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to enumerate cameras", e);
            return null;
        }
    }

    /** Close the camera and release all resources. Safe to call multiple times. */
    public void close() {
        if (captureSession != null) {
            try { captureSession.stopRepeating(); } catch (Exception ignored) {}
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try { cameraThread.join(500); } catch (InterruptedException ignored) {}
            cameraThread = null;
        }
    }
}
