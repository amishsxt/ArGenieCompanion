package com.example.argeniecompanion.ar.render;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * A full-screen NDC quad that renders the camera OES texture as the scene background.
 *
 * Vertex layout: [posX, posY, texS, texT] — 4 floats per vertex, triangle strip.
 * The texture transform matrix (uTexMatrix) from SurfaceTexture.getTransformMatrix()
 * is applied in the vertex shader to correct for any camera sensor rotation / flip.
 */
class CameraBackgroundQuad {

    // NDC positions + raw (pre-transform) texture coordinates
    private static final float[] VERTICES = {
        // posX  posY   texS  texT
        -1f,  -1f,   0f,   0f,   // bottom-left
         1f,  -1f,   1f,   0f,   // bottom-right
        -1f,   1f,   0f,   1f,   // top-left
         1f,   1f,   1f,   1f,   // top-right
    };

    private static final int FLOATS_PER_VERTEX = 4;
    private static final int STRIDE = FLOATS_PER_VERTEX * Float.BYTES;

    private final int vbo;
    private final int aPositionLoc;
    private final int aTexCoordLoc;
    private final int uTexMatrixLoc;

    CameraBackgroundQuad(int program) {
        aPositionLoc  = GLES20.glGetAttribLocation(program, "aPosition");
        aTexCoordLoc  = GLES20.glGetAttribLocation(program, "aTexCoord");
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix");

        FloatBuffer buf = ByteBuffer
                .allocateDirect(VERTICES.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buf.put(VERTICES).flip();

        int[] vbos = new int[1];
        GLES20.glGenBuffers(1, vbos, 0);
        vbo = vbos[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                buf.capacity() * Float.BYTES, buf, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    void draw(float[] texMatrix) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);

        GLES20.glEnableVertexAttribArray(aPositionLoc);
        GLES20.glVertexAttribPointer(aPositionLoc, 2,
                GLES20.GL_FLOAT, false, STRIDE, 0);

        GLES20.glEnableVertexAttribArray(aTexCoordLoc);
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2,
                GLES20.GL_FLOAT, false, STRIDE, 2 * Float.BYTES);

        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(aPositionLoc);
        GLES20.glDisableVertexAttribArray(aTexCoordLoc);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    void release() {
        GLES20.glDeleteBuffers(1, new int[]{vbo}, 0);
    }
}
