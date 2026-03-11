package com.example.argeniecompanion.ar;

import android.media.Image;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe single-slot frame holder used to hand off YUV_420_888 Images
 * from the Camera2 thread to the vision thread.
 *
 * Offering a new frame automatically closes any previously held frame,
 * so the camera thread never blocks waiting for the vision thread to consume.
 */
public class FrameBuffer {

    private final AtomicReference<Image> slot = new AtomicReference<>(null);

    /** Store a new frame, closing the previous one if it was never consumed. */
    public void offer(Image image) {
        Image old = slot.getAndSet(image);
        if (old != null) {
            try { old.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Take the current frame and remove it from the buffer.
     * Caller is responsible for closing the returned Image.
     * Returns null if no frame is currently available.
     */
    public Image take() {
        return slot.getAndSet(null);
    }
}
