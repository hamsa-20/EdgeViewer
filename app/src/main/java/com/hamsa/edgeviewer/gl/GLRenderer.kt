package com.hamsa.edgeviewer.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLRenderer: receives processed RGBA byte[] via updateFrameFromQueue (called on GL thread)
 * and draws it using GLTextureRenderer.
 *
 * We use a small queue to avoid skipping frames or concurrency issues.
 */
class GLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val TAG = "GLRenderer"
    private var textureRenderer: GLTextureRenderer? = null

    // Single-element queue to transfer frame data safely onto GL thread
    private val frameQueue = ArrayBlockingQueue<Frame>(2)

    data class Frame(val data: ByteArray, val width: Int, val height: Int)

    /**
     * Called from GL thread using glView.queueEvent { ... }.
     * Put new frame into queue (caller must call requestRender afterwards).
     */
    fun updateFrameFromQueue(data: ByteArray, width: Int, height: Int) {
        // keep only the latest frame to avoid memory growth
        val frame = Frame(data, width, height)
        while (!frameQueue.offer(frame)) {
            // remove oldest to make room
            frameQueue.poll()
        }
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        textureRenderer = GLTextureRenderer()
        textureRenderer?.init()
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // pop latest frame (if any)
        val frame = frameQueue.poll()
        if (frame != null) {
            try {
                textureRenderer?.drawTexture(frame.data, frame.width, frame.height)
            } catch (e: Exception) {
                Log.e(TAG, "drawTexture failed: ${e.message}")
            }
        }
    }
}
