package com.example.argeniecompanion.ar.vision;

import android.media.Image;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.example.argeniecompanion.ar.FrameBuffer;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs ArUco marker detection on a dedicated HandlerThread, decoupled from
 * both the camera thread and the GL rendering thread.
 *
 * Dictionary: DICT_6X6_250 (matches markers generated with OpenCV defaults).
 *
 * Threading:
 *   - Continuously polls FrameBuffer; if no frame is available it reschedules
 *     itself after 30 ms rather than blocking.
 *   - Detection results are delivered via DetectionCallback; callers must
 *     dispatch to the appropriate thread themselves.
 */
public class MarkerDetector {

    private static final String TAG = "MarkerDetector";

    public interface DetectionCallback {
        void onMarkersDetected(List<MarkerDetectionResult> results);
    }

    private final FrameBuffer        frameBuffer;
    private final PoseEstimator      poseEstimator;
    private final DetectionCallback  callback;

    private HandlerThread visionThread;
    private Handler       visionHandler;

    // OpenCV objects (created on start(), reused across frames)
    private ArucoDetector     detector;
    private final Mat         grayMat  = new Mat();
    private final Mat         idsMat   = new Mat();
    private final List<Mat>   corners  = new ArrayList<>();
    private final List<Mat>   rejected = new ArrayList<>();

    // Reusable byte buffer for YUV Y-plane copy
    private byte[] rowBuf;

    public MarkerDetector(FrameBuffer frameBuffer,
                          PoseEstimator poseEstimator,
                          DetectionCallback callback) {
        this.frameBuffer   = frameBuffer;
        this.poseEstimator = poseEstimator;
        this.callback      = callback;
    }

    public void start() {
        Dictionary dict   = Objdetect.getPredefinedDictionary(Objdetect.DICT_6X6_250);
        DetectorParameters params = new DetectorParameters();
        detector = new ArucoDetector(dict, params);

        visionThread = new HandlerThread("ArucoVisionThread",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        visionThread.start();
        visionHandler = new Handler(visionThread.getLooper());
        visionHandler.post(this::processFrame);
    }

    public void stop() {
        if (visionThread != null) {
            visionThread.quitSafely();
            try { visionThread.join(500); } catch (InterruptedException ignored) {}
            visionThread = null;
        }
        grayMat.release();
        idsMat.release();
        for (Mat c : corners)  c.release();
        for (Mat r : rejected) r.release();
        corners.clear();
        rejected.clear();
    }

    // ---- Core loop ----

    private void processFrame() {
        Image image = frameBuffer.take();
        if (image == null) {
            // No frame yet — back off and retry
            visionHandler.postDelayed(this::processFrame, 30);
            return;
        }

        try {
            extractYPlane(image, grayMat);
        } finally {
            image.close();
        }

        corners.clear();
        idsMat.release();

        try {
            detector.detectMarkers(grayMat, corners, idsMat, rejected);
        } catch (Exception e) {
            Log.e(TAG, "detectMarkers failed", e);
            visionHandler.post(this::processFrame);
            return;
        }

        int numMarkers = idsMat.rows();
        if (numMarkers > 0 && callback != null) {
            List<MarkerDetectionResult> results = new ArrayList<>(numMarkers);
            for (int i = 0; i < numMarkers; i++) {
                int markerId = (int) idsMat.get(i, 0)[0];

                // ArUco corners are 1×4 CV_32FC2. new MatOfPoint2f(Mat) does NOT produce
                // a layout that solvePnP's checkVector(2, CV_32F) accepts — it returns -1,
                // causing the assertion failure. Extract raw floats and build explicitly.
                Mat c = corners.get(i);
                float[] pts = new float[8]; // 4 points × (x,y)
                c.get(0, 0, pts);
                MatOfPoint2f cornerMat = new MatOfPoint2f(
                        new org.opencv.core.Point(pts[0], pts[1]),
                        new org.opencv.core.Point(pts[2], pts[3]),
                        new org.opencv.core.Point(pts[4], pts[5]),
                        new org.opencv.core.Point(pts[6], pts[7]));

                MarkerDetectionResult r = null;
                try {
                    r = poseEstimator.estimatePose(markerId, cornerMat);
                } catch (Exception e) {
                    Log.e(TAG, "solvePnP failed for marker " + markerId, e);
                } finally {
                    cornerMat.release();
                }
                if (r != null) results.add(r);
            }
            if (!results.isEmpty()) {
                callback.onMarkersDetected(results);
            }
        }

        // Schedule next frame immediately
        visionHandler.post(this::processFrame);
    }

    /**
     * Extracts the Y (luminance) plane from a YUV_420_888 Image into a grayscale Mat.
     * Uses only the luma channel — sufficient for ArUco corner detection and faster
     * than a full colour conversion.
     */
    private void extractYPlane(Image image, Mat dst) {
        Image.Plane yPlane   = image.getPlanes()[0];
        ByteBuffer  buffer   = yPlane.getBuffer();
        int         width    = image.getWidth();
        int         height   = image.getHeight();
        int         rowStride = yPlane.getRowStride();

        if (dst.rows() != height || dst.cols() != width) {
            dst.create(height, width, CvType.CV_8UC1);
        }

        if (rowStride == width) {
            // Contiguous memory — single bulk copy
            int remaining = buffer.remaining();
            if (rowBuf == null || rowBuf.length < remaining) rowBuf = new byte[remaining];
            buffer.get(rowBuf, 0, remaining);
            dst.put(0, 0, rowBuf);
        } else {
            // Stride padding present — copy row by row
            if (rowBuf == null || rowBuf.length < width) rowBuf = new byte[width];
            for (int row = 0; row < height; row++) {
                buffer.position(row * rowStride);
                buffer.get(rowBuf, 0, width);
                dst.put(row, 0, rowBuf);
            }
        }
    }
}
