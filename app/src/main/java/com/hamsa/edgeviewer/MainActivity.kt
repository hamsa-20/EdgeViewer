package com.hamsa.edgeviewer
import androidx.compose.ui.graphics.nativeCanvas

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var cameraHelper: CameraHelper

    // UI state
    private var isCameraActive by mutableStateOf(false)
    private var frameInfo by mutableStateOf("No frames received")
    private var processingTime by mutableStateOf(0L)
    private var frameCount by mutableStateOf(0)
    private var edgeBitmap by mutableStateOf<Bitmap?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            frameInfo = "Camera permission denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            EdgeViewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EdgeViewerUI(
                        isCameraActive = isCameraActive,
                        frameInfo = frameInfo,
                        processingTime = processingTime,
                        frameCount = frameCount,
                        edgeBitmap = edgeBitmap,
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
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                setupCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setupCamera() {
        // cameraHelper is a small stub below — replace with your actual Camera helper
        cameraHelper = CameraHelper(this, Size(640, 480)) { nv21, width, height, rotation ->
            val startTime = System.currentTimeMillis()
            frameCount++

            // Process the frame with rotation information (calls native stub below)
            processFrame(nv21, width, height, rotation)

            processingTime = System.currentTimeMillis() - startTime
            frameInfo = "Frame: ${width}x${height} | Rotation: ${rotation}° | FPS: ${calculateFPS()}"
        }
    }

    private fun startCamera() {
        if (!::cameraHelper.isInitialized) {
            setupCamera()
        }
        cameraHelper.start()
        isCameraActive = true
        frameInfo = "Camera starting..."
    }

    private fun stopCamera() {
        if (::cameraHelper.isInitialized) {
            cameraHelper.stop()
        }
        isCameraActive = false
        frameInfo = "Camera stopped"
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun processFrame(nv21: ByteArray, width: Int, height: Int, rotation: Int) {
        // Pass to your native code with rotation information (stub below).
        // Replace EdgeViewerNative.processFrame(...) with your JNI call which writes the result
        EdgeViewerNative.processFrame(nv21, width, height, rotation)

        // For now, update UI with a placeholder generated bitmap.
        updateEdgeDisplay(width, height)
    }

    private fun updateEdgeDisplay(width: Int, height: Int) {
        // placeholder gradient bitmap — replace with real processed image provided by native code
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width step 2) {
            for (y in 0 until height step 2) {
                val intensity = ((x + y) % 255).toFloat() / 255f
                val gray = (intensity * 255).toInt()
                val color = android.graphics.Color.rgb(gray, gray, gray)
                bitmap.setPixel(x, y, color)
            }
        }
        edgeBitmap = bitmap
    }

    private var lastFpsTime = System.currentTimeMillis()
    private var lastFrameCount = 0
    private fun calculateFPS(): Int {
        // moving / sampled FPS to be stable
        val now = System.currentTimeMillis()
        val delta = now - lastFpsTime
        if (delta >= 1000) {
            val frames = frameCount - lastFrameCount
            lastFrameCount = frameCount
            lastFpsTime = now
            return frames.coerceAtMost(60)
        }
        return 0
    }

    override fun onResume() {
        super.onResume()
        if (isCameraActive && ::cameraHelper.isInitialized) {
            cameraHelper.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::cameraHelper.isInitialized) {
            cameraHelper.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraHelper.isInitialized) {
            cameraHelper.stop()
        }
        cameraHelper.shutdown()
    }

}

@Composable
fun EdgeViewerUI(
    isCameraActive: Boolean,
    frameInfo: String,
    processingTime: Long,
    frameCount: Int,
    edgeBitmap: Bitmap?,
    onStartCamera: () -> Unit,
    onStopCamera: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    var permissionStatus by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // keep permission status updated periodically
    LaunchedEffect(Unit) {
        while (true) {
            permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Edge Viewer",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Permission card
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Camera Permission", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (permissionStatus) "Granted ✅" else "Denied ❌",
                    color = if (permissionStatus) Color(0xFF2E7D32) else Color.Red
                )
                if (!permissionStatus) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRequestPermission) { Text("Request Permission") }
                }
            }
        }

        // Controls
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Camera Controls", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (permissionStatus) {
                    if (isCameraActive) {
                        Button(onClick = onStopCamera, modifier = Modifier.fillMaxWidth()) { Text("Stop Camera") }
                    } else {
                        Button(onClick = onStartCamera, modifier = Modifier.fillMaxWidth()) { Text("Start Camera") }
                    }
                }
                if (isCameraActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        // Info
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Frame Information", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Status: $frameInfo")
                Text("Processing Time: ${processingTime}ms")
                Text("Total Frames: $frameCount")
            }
        }

        // Preview
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edge Detection Preview", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Color.Black)) {
                    if (edgeBitmap != null) {
                        EdgeDetectionCanvas(bitmap = edgeBitmap!!)
                    } else {
                        Text("No edge data available", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    }
                }
                edgeBitmap?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Resolution: ${it.width}x${it.height}", fontSize = 12.sp)
                }
            }
        }

        // Instructions
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Instructions", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Grant camera permission")
                Text("2. Click 'Start Camera' to begin")
                Text("3. View edge detection results in real-time")
                Text("4. Use 'Stop Camera' to pause processing")
            }
        }
    }
}

@Composable
fun EdgeDetectionCanvas(bitmap: Bitmap) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val imageBitmap = bitmap.asImageBitmap()
        // draw the bitmap to fill the available size (scaled)
        drawImage(
            image = imageBitmap,
            dstSize = IntSize(size.width.toInt(), size.height.toInt())
        )

        // draw border
        drawRect(color = Color.White, style = Stroke(width = 2f))

        // draw label (use nativeCanvas for text)
        drawContext.canvas.nativeCanvas.drawText(
            "Edge Detection Output",
            10f,
            30f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 28f
            }
        )
    }
}

@Composable
fun EdgeViewerTheme(content: @Composable () -> Unit) {
    // Minimal Material3 wrapper — you can replace with your project theme
    MaterialTheme(
        content = content
    )
}

/*
  ===== STUBS =====
  The following are tiny stubs so the file compiles. Replace these with your real camera helper
  and native JNI bridge that you already had for the original project.
*/



object EdgeViewerNative {
    // stub: implement JNI to call C++ processing and return result to UI (or write to shared buffer)
    fun processFrame(nv21: ByteArray, width: Int, height: Int, rotation: Int) {
        // no-op in stub
    }
}
