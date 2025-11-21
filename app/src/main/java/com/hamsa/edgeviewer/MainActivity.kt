package com.hamsa.edgeviewer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Size
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {

    private lateinit var cameraHelper: CameraHelper

    private var isCameraActive by mutableStateOf(false)
    private var frameInfo by mutableStateOf("No frames received")
    private var processingTime by mutableStateOf(0L)
    private var frameCount by mutableStateOf(0)
    private var edgeBitmap by mutableStateOf<Bitmap?>(null)
    private var previewTextureView by mutableStateOf<TextureView?>(null)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) setupCamera()
            else Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            EdgeViewerTheme {
                Surface(Modifier.fillMaxSize()) {
                    EdgeViewerUI(
                        isCameraActive = isCameraActive,
                        frameInfo = frameInfo,
                        processingTime = processingTime,
                        frameCount = frameCount,
                        edgeBitmap = edgeBitmap,
                        previewView = previewTextureView,
                        onStartCamera = { startCamera() },
                        onStopCamera = { stopCamera() },
                        onRequestPermission = { requestCameraPermission() }
                    )
                }
            }
        }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) setupCamera()
        else requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun setupCamera() {

        cameraHelper = CameraHelper(this, Size(640, 480)) { nv21, width, height, rotation ->

            val start = System.currentTimeMillis()
            frameCount++

            if (nv21.isEmpty()) return@CameraHelper

            try {
                // native processing only takes 3 params
                val out = NativeBridge.processFrameNV21(nv21, width, height, rotation)


                if (out != null && out.size >= width * height * 4) {

                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(out))

                    // rotate to match camera preview
                    val rotatedBmp = rotateBitmap(bmp, rotation)

                    runOnUiThread {
                        edgeBitmap = rotatedBmp
                    }
                }

            } catch (e: Throwable) {
                e.printStackTrace()
            }
            processingTime = System.currentTimeMillis() - start
            frameInfo = "Frame: ${width}x${height} | Rot ${rotation}° | FPS ${calculateFPS()}"
        }

        previewTextureView = cameraHelper.previewTextureView
    }

    private fun startCamera() {
        if (!::cameraHelper.isInitialized) setupCamera()
        cameraHelper.start()
        isCameraActive = true
    }

    private fun stopCamera() {
        if (::cameraHelper.isInitialized) cameraHelper.stop()
        isCameraActive = false
    }

    private fun requestCameraPermission() =
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)

    private var lastFpsTime = System.currentTimeMillis()
    private var lastFrameCount = 0

    private fun calculateFPS(): Int {
        val now = System.currentTimeMillis()
        if (now - lastFpsTime >= 1000) {
            val fps = frameCount - lastFrameCount
            lastFrameCount = frameCount
            lastFpsTime = now
            return fps
        }
        return 0
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraHelper.isInitialized) {
            cameraHelper.stop()
            cameraHelper.shutdown()
        }
    }

    private fun rotateBitmap(src: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return src
        val m = android.graphics.Matrix()
        m.postRotate(rotation.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}

@Composable
fun EdgeViewerUI(
    isCameraActive: Boolean,
    frameInfo: String,
    processingTime: Long,
    frameCount: Int,
    edgeBitmap: Bitmap?,
    previewView: TextureView?,
    onStartCamera: () -> Unit,
    onStopCamera: () -> Unit,
    onRequestPermission: () -> Unit
) {

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {

        Text("Edge Viewer", style = MaterialTheme.typography.headlineLarge)

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {

                Text("Camera Preview")
                Spacer(Modifier.height(10.dp))

                if (previewView != null) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {

                Text("Edge Detection Output")
                Spacer(Modifier.height(10.dp))

                Box(
                    Modifier.fillMaxWidth().height(300.dp).background(Color.Black)
                ) {
                    if (edgeBitmap != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawImage(
                                image = edgeBitmap.asImageBitmap(),
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )
                            drawRect(Color.White, style = Stroke(2f))
                        }
                    } else {
                        Text("No edge data", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {

                Text("Camera Controls")
                Spacer(Modifier.height(10.dp))

                if (isCameraActive)
                    Button(onClick = onStopCamera) { Text("Stop Camera") }
                else
                    Button(onClick = onStartCamera) { Text("Start Camera") }

                Spacer(Modifier.height(10.dp))

                Text("Status: $frameInfo")
                Text("Processing: ${processingTime}ms")
                Text("Frames: $frameCount")
            }
        }
    }
}

@Composable
fun EdgeViewerTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
