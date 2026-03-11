package com.example.argeniecompanion.ar.vision;

import android.util.Log;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;

/**
 * Estimates the 6-DOF pose of a detected ArUco marker using solvePnP.
 *
 * The camera matrix is built from focal length + principal point derived from
 * Camera2 sensor parameters (see ArMarkerActivity for how these are computed).
 *
 * Physical marker size defaults to MARKER_SIZE_METERS — must match the actual
 * printed size of your markers for accurate tvec (depth) values.
 */
public class PoseEstimator {

    private static final String TAG = "PoseEstimator";

    /** Physical side length of the ArUco marker in metres. Adjust to match printed size. */
    public static final float MARKER_SIZE_METERS = 0.10f;  // 10 cm

    private final Mat       cameraMatrix;
    private final MatOfDouble distCoeffs;

    /**
     * @param focalLengthPx  focal length in pixels (assume fx == fy for square pixels)
     * @param cx             principal point X (typically imageWidth / 2)
     * @param cy             principal point Y (typically imageHeight / 2)
     */
    public PoseEstimator(double focalLengthPx, double cx, double cy) {
        cameraMatrix = Mat.eye(3, 3, CvType.CV_64FC1);
        cameraMatrix.put(0, 0, focalLengthPx);
        cameraMatrix.put(1, 1, focalLengthPx);
        cameraMatrix.put(0, 2, cx);
        cameraMatrix.put(1, 2, cy);

        distCoeffs = new MatOfDouble(); // zero distortion as starting assumption
    }

    /**
     * Estimate pose for one detected marker.
     *
     * @param markerId  ArUco marker ID
     * @param corners   2D image corners in order: top-left, top-right, bottom-right, bottom-left
     * @return MarkerDetectionResult, or null if solvePnP fails
     */
    public MarkerDetectionResult estimatePose(int markerId, MatOfPoint2f corners) {
        float h = MARKER_SIZE_METERS / 2f;

        // 3D marker corners in marker-local coordinate system (z=0 plane)
        MatOfPoint3f objectPoints = new MatOfPoint3f(
                new Point3(-h,  h, 0),
                new Point3( h,  h, 0),
                new Point3( h, -h, 0),
                new Point3(-h, -h, 0)
        );

        Mat rvec = new Mat();
        Mat tvec = new Mat();

        boolean ok = Calib3d.solvePnP(
                objectPoints, corners, cameraMatrix, distCoeffs, rvec, tvec);

        if (!ok) {
            objectPoints.release();
            rvec.release();
            tvec.release();
            return null;
        }

        double[] tvecArr = new double[3];
        double[] rvecArr = new double[3];
        tvec.get(0, 0, tvecArr);
        rvec.get(0, 0, rvecArr);

        objectPoints.release();
        rvec.release();
        tvec.release();

        return new MarkerDetectionResult(markerId, tvecArr, rvecArr, System.nanoTime());
    }

    public void release() {
        cameraMatrix.release();
        distCoeffs.release();
    }
}
