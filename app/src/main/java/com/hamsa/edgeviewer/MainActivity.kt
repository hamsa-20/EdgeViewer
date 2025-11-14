package com.hamsa.edgeviewer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Size
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hamsa.edgeviewer.gl.GLRenderer
import android.opengl.GLSurfaceView
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: GLRenderer
    private var cameraHelper: CameraHelper? = null

    private val CAMERA_PERMISSION_CODE = 101

    // --- UI ---
    private lateinit var fpsLabel: TextView
    private lateinit var toggleButton: ToggleButton

    // --- State ---
    private val isEdgeDetectionEnabled = AtomicBoolean(true)
    private var frameCount = 0
    private var lastFpsTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- UI --- 
        renderer = GLRenderer(this)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        val root = FrameLayout(this)
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(glView)

        // --- Toggle Button ---
        toggleButton = ToggleButton(this).apply {
            textOn = "Edge Detection"
            textOff = "Raw Camera"
            isChecked = isEdgeDetectionEnabled.get()
            setOnCheckedChangeListener { _, isChecked ->
                isEdgeDetectionEnabled.set(isChecked)
            }
        }
        val toggleParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply{
            setMargins(20, 20, 20, 20)
        }
        root.addView(toggleButton, toggleParams)

        // --- FPS Label ---
        fpsLabel = TextView(this).apply {
            setTextColor(Color.GREEN)
            textSize = 20f
        }
        val fpsParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(20, 150, 20, 20)
        }
        root.addView(fpsLabel, fpsParams)

        setContentView(root)

        checkPermissionAndStart()
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE
            )
        } else {
            startFlow()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startFlow()
            } else {
                finish()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun startFlow() {
        cameraHelper = CameraHelper(this, Size(640, 480)) { nv21, w, h ->
            try {
                val processed: ByteArray? = if(isEdgeDetectionEnabled.get()) {
                    NativeBridge.processFrameNV21(nv21, w, h)
                }else{
                    NativeBridge.passthroughNV21(nv21, w, h)
                }

                if (processed != null) {
                    glView.queueEvent {
                        renderer.updateFrameFromQueue(processed, w, h)
                    }
                    glView.requestRender()
                }
                updateFPS()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        cameraHelper?.start()
    }

    private fun updateFPS() {
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsTime > 1000) {
            val fps = frameCount / ((now - lastFpsTime) / 1000.0)
            runOnUiThread {
                fpsLabel.text = String.format("%.1f FPS", fps)
            }
            frameCount = 0
            lastFpsTime = now
        }
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        lastFpsTime = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
        cameraHelper?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper?.stop()
    }
}
