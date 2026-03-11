package com.example.argeniecompanion.ar.render;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GLSurfaceView.Renderer that draws the live camera feed as the full-screen background.
 *
 * Threading:
 *   - onSurfaceCreated / onDrawFrame run on the GL thread.
 *   - SurfaceTexture is created here and handed to the activity via SurfaceTextureListener
 *     so Camera2 can be opened with it from the main thread.
 *   - frameAvailable flag is written by the SurfaceTexture callback (camera thread)
 *     and read + cleared on the GL thread inside a synchronized block.
 */
public class ArAnnotationRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "ArAnnotationRenderer";

    // ---- Shaders ----

    private static final String VERT_SHADER =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying   vec2 vTexCoord;\n" +
            "uniform   mat4 uTexMatrix;\n" +
            "void main() {\n" +
            "    gl_Position = aPosition;\n" +
            // Apply SurfaceTexture transform (rotation/flip correction from sensor orientation)
            "    vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;\n" +
            "}\n";

    private static final String FRAG_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform samplerExternalOES uTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
            "}\n";

    // ---- Callback ----

    public interface SurfaceTextureListener {
        /** Called on the GL thread when the OES SurfaceTexture is ready for Camera2. */
        void onSurfaceTextureReady(SurfaceTexture surfaceTexture);
    }

    // ---- State ----

    private final GLSurfaceView        glSurfaceView;
    private       SurfaceTextureListener listener;

    private int            cameraTextureId = -1;
    private SurfaceTexture surfaceTexture;
    private final float[]  texMatrix = new float[16];

    private boolean frameAvailable = false; // guarded by `this`

    private int                  programHandle = -1;
    private CameraBackgroundQuad backgroundQuad;

    public ArAnnotationRenderer(GLSurfaceView glSurfaceView) {
        this.glSurfaceView = glSurfaceView;
    }

    public void setSurfaceTextureListener(SurfaceTextureListener listener) {
        this.listener = listener;
    }

    // ---- GLSurfaceView.Renderer ----

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        programHandle = buildProgram(VERT_SHADER, FRAG_SHADER);
        if (programHandle == 0) {
            Log.e(TAG, "Shader program failed to build");
            return;
        }

        // Create the OES texture that SurfaceTexture will write camera frames into
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        cameraTextureId = textures[0];

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        // SurfaceTexture ties the camera stream to the OES texture
        surfaceTexture = new SurfaceTexture(cameraTextureId);
        surfaceTexture.setOnFrameAvailableListener(st -> {
            synchronized (ArAnnotationRenderer.this) {
                frameAvailable = true;
            }
            glSurfaceView.requestRender(); // wake GL thread (RENDERMODE_WHEN_DIRTY)
        });

        backgroundQuad = new CameraBackgroundQuad(programHandle);

        // Notify host so it can start Camera2 with this SurfaceTexture
        if (listener != null) {
            listener.onSurfaceTextureReady(surfaceTexture);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        synchronized (this) {
            if (frameAvailable) {
                surfaceTexture.updateTexImage();
                surfaceTexture.getTransformMatrix(texMatrix);
                frameAvailable = false;
            }
        }

        GLES20.glUseProgram(programHandle);

        // Bind the camera OES texture to unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programHandle, "uTexture"), 0);

        backgroundQuad.draw(texMatrix);
    }

    /** Release GL objects. Must be called on the GL thread (e.g. from onPause via queueEvent). */
    public void release() {
        if (backgroundQuad != null)  { backgroundQuad.release(); backgroundQuad = null; }
        if (programHandle != -1)     { GLES20.glDeleteProgram(programHandle); programHandle = -1; }
        if (cameraTextureId != -1)   {
            GLES20.glDeleteTextures(1, new int[]{cameraTextureId}, 0);
            cameraTextureId = -1;
        }
        if (surfaceTexture != null)  { surfaceTexture.release(); surfaceTexture = null; }
    }

    /** Returns the SurfaceTexture after surface is created, null before that. */
    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    // ---- Shader helpers ----

    private static int buildProgram(String vertSrc, String fragSrc) {
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc);
        if (vs == 0 || fs == 0) return 0;

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Program link error: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        return program;
    }

    private static int compileShader(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Shader compile error: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
