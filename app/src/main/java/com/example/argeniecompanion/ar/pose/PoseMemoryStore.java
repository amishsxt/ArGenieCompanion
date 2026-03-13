package com.example.argeniecompanion.ar.pose;

import com.example.argeniecompanion.ar.camera.ArCamera2Manager;
import com.example.argeniecompanion.ar.sensor.OrientationSnapshot;
import com.example.argeniecompanion.ar.sensor.RotationTracker;
import com.example.argeniecompanion.ar.vision.MarkerDetectionResult;
import com.example.argeniecompanion.ar.vision.PoseEstimator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages the lifecycle of all anchored annotations.
 *
 * Threading: must be accessed from the main thread only.
 *
 * Annotation lifecycle:
 *   ACTIVE  — marker is currently visible; tvec/orientation refreshed every detection
 *   FADING  — marker left the frame; annotation lingers using gyro tracking, alpha → 0
 *   REMOVED — fade complete; entry deleted
 */
public class PoseMemoryStore {

    /** How long an annotation persists after the marker leaves the frame. */
    public static final long FADE_DURATION_MS = 0;

    private final Map<Integer, AnchoredAnnotation> annotations = new HashMap<>();

    // Camera intrinsics for screen projection — set once from ArMarkerActivity
    private double focalLengthPx = 960.0;
    private double cx = ArCamera2Manager.PREVIEW_WIDTH  / 2.0;
    private double cy = ArCamera2Manager.PREVIEW_HEIGHT / 2.0;

    public void setCameraIntrinsics(double focalLengthPx, double cx, double cy) {
        this.focalLengthPx = focalLengthPx;
        this.cx = cx;
        this.cy = cy;
    }

    // ---- Write path (called when new detections arrive) ----

    /**
     * Update the store with the latest detected markers.
     *
     * @param detected list of markers seen in this frame (may be empty)
     * @param current  device orientation at the moment of detection
     */
    public void updateDetections(List<MarkerDetectionResult> detected,
                                 OrientationSnapshot current) {
        Set<Integer> seenIds = new HashSet<>();

        double markerHalf = PoseEstimator.MARKER_SIZE_METERS / 2.0;

        for (MarkerDetectionResult r : detected) {
            seenIds.add(r.markerId);
            // Project the marker's top-centre (0, +markerHalf, 0) into camera space
            // so the annotation anchors at the top edge rather than the centre.
            double[] topTvec = markerTopInCamera(r.rvec, r.tvec, markerHalf);
            AnchoredAnnotation ann = annotations.get(r.markerId);
            if (ann == null) {
                ann = new AnchoredAnnotation(r.markerId, topTvec, current);
                annotations.put(r.markerId, ann);
            } else {
                System.arraycopy(topTvec, 0, ann.tvec, 0, 3);
                ann.storedOrientation = current;
                ann.isFading   = false;
                ann.fadeStartMs = 0;
            }
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, AnchoredAnnotation>> it = annotations.entrySet().iterator();
        while (it.hasNext()) {
            AnchoredAnnotation ann = it.next().getValue();
            if (seenIds.contains(ann.markerId)) continue;

            if (!ann.isFading) {
                // Marker just left the frame — start fade
                ann.isFading    = true;
                ann.fadeStartMs = now;
            } else if (now - ann.fadeStartMs >= FADE_DURATION_MS) {
                // Fully faded — remove
                it.remove();
            }
        }
    }

    // ---- Read path (called every display frame to build draw list) ----

    /**
     * Project all active annotations into screen-space for the current device orientation.
     *
     * Math:
     *   1. deltaR = getDeltaRotationMatrix(storedOrientation, current)
     *      — rotation the camera has undergone since we last saw the marker
     *   2. T_current = deltaR * T_stored
     *      — stored camera-frame tvec rotated into the current camera frame
     *   3. u = fx * (Tx/Tz) + cx,  v = fy * (Ty/Tz) + cy   (pinhole projection)
     *   4. Scale from camera-image pixels to view pixels
     *
     * @param current    latest device orientation from RotationTracker
     * @param viewWidth  width of the overlay/GL view in pixels
     * @param viewHeight height of the overlay/GL view in pixels
     */
    public List<AnnotationRenderItem> buildRenderList(OrientationSnapshot current,
                                                      int viewWidth, int viewHeight) {
        List<AnnotationRenderItem> items = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (AnchoredAnnotation ann : annotations.values()) {
            float alpha = ann.isFading
                    ? 1f - (float)(now - ann.fadeStartMs) / FADE_DURATION_MS
                    : 1f;
            alpha = Math.max(0f, Math.min(1f, alpha));
            if (alpha <= 0f) continue;

            // When actively visible, project tvec directly — no gyro needed.
            // Gyro delta is only applied while fading (marker has left the scene)
            // to keep the annotation pointing in the right direction as the device moves.
            double[] t = ann.tvec;
            double dx, dy, dz;
            if (ann.isFading) {
                float[] R = RotationTracker.getDeltaRotationMatrix(ann.storedOrientation, current);
                dx = R[0]*t[0] + R[1]*t[1] + R[2]*t[2];
                dy = R[3]*t[0] + R[4]*t[1] + R[5]*t[2];
                dz = R[6]*t[0] + R[7]*t[1] + R[8]*t[2];
            } else {
                dx = t[0];
                dy = t[1];
                dz = t[2];
            }

            if (dz < 0.05) continue; // behind or at camera origin

            // Step 3: pinhole projection to camera-image pixels
            double u = focalLengthPx * (dx / dz) + cx;
            double v = focalLengthPx * (dy / dz) + cy;

            // Step 4: scale to overlay view pixels
            float scaleX = (float) viewWidth  / ArCamera2Manager.PREVIEW_WIDTH;
            float scaleY = (float) viewHeight / ArCamera2Manager.PREVIEW_HEIGHT;

            float screenX = (float)(u * scaleX);
            float screenY = (float)(v * scaleY);

            items.add(new AnnotationRenderItem(ann.markerId, ann.label, screenX, screenY, alpha));
        }
        return items;
    }

    // ---- Helpers ----

    /**
     * Returns the camera-frame 3D position of the marker's top-centre edge.
     *
     * In the ArUco object-point convention used by PoseEstimator, the top of the
     * marker corresponds to +yOffset along the marker's local Y axis.  We apply
     * only the Y column of the Rodrigues rotation matrix so we avoid building the
     * full 3×3 matrix.
     *
     *   P_camera = R * [0, yOffset, 0]^T + tvec
     *            = [R01*yOffset + tx,  R11*yOffset + ty,  R21*yOffset + tz]
     *
     * @param rvec    Rodrigues rotation vector from solvePnP  (marker → camera)
     * @param tvec    marker centre in camera frame (metres)
     * @param yOffset half side-length of the marker (metres)
     */
    private static double[] markerTopInCamera(double[] rvec, double[] tvec, double yOffset) {
        double rx = rvec[0], ry = rvec[1], rz = rvec[2];
        double theta = Math.sqrt(rx * rx + ry * ry + rz * rz);

        double r01, r11, r21; // column-1 of the rotation matrix
        if (theta < 1e-10) {
            r01 = 0.0; r11 = 1.0; r21 = 0.0; // identity
        } else {
            double kx = rx / theta, ky = ry / theta, kz = rz / theta;
            double c = Math.cos(theta), s = Math.sin(theta), t = 1.0 - c;
            r01 = t * kx * ky - s * kz;
            r11 = t * ky * ky + c;
            r21 = t * kz * ky + s * kx;
        }

        return new double[]{
            r01 * yOffset + tvec[0],
            r11 * yOffset + tvec[1],
            r21 * yOffset + tvec[2]
        };
    }

    public boolean hasActiveAnnotations() {
        return !annotations.isEmpty();
    }

    public int size() {
        return annotations.size();
    }
}
