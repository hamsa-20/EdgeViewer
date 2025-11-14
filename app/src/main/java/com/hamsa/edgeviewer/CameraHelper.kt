package com.hamsa.edgeviewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import java.nio.ByteBuffer

class CameraHelper(
    private val context: Context,
    private val desiredSize: Size = Size(640, 480),
    private val onFrame: (nv21: ByteArray, width: Int, height: Int, rotation: Int) -> Unit
) {
    private val TAG = "CameraHelper"
    private val camThread = HandlerThread("CameraThread").apply { start() }
    private val camHandler = Handler(camThread.looper)
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraId: String? = null
    private var sensorOrientation: Int = 0
    private var displayRotation: Int = Surface.ROTATION_0
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            displayRotation = when {
                orientation in 45..134 -> Surface.ROTATION_270
                orientation in 135..224 -> Surface.ROTATION_180
                orientation in 225..314 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
        }
    }
    fun shutdown() {
        camThread.quitSafely()
    }

    @SuppressLint("MissingPermission")
    fun start() {
        Log.d(TAG, "Starting camera...")
        orientationListener.enable()
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()
            if (cameraId == null) return

            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            imageReader = ImageReader.newInstance(
                desiredSize.width, desiredSize.height, ImageFormat.YUV_420_888, 2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val nv21 = yuv420ToNV21(img)
                        val totalRotation = calculateTotalRotation()
                        onFrame(nv21, img.width, img.height, totalRotation)
                    } finally {
                        img.close()
                    }
                }, camHandler)
            }

            cameraManager.openCamera(cameraId!!, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    createSession()
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    cameraDevice = null
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    cameraDevice = null
                }
            }, camHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Camera start error: ${e.message}")
        }
    }

    fun stop() {
        orientationListener.disable()

        try {
            session?.close()
            session = null
        } catch (_: Exception) {}

        try {
            cameraDevice?.close()
            cameraDevice = null
        } catch (_: Exception) {}

        imageReader?.close()
        imageReader = null
    }


    private fun createSession() {
        val cam = cameraDevice ?: return
        val readerSurface = imageReader?.surface ?: return
        try {
            val previewRequest = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(readerSurface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            cam.createCaptureSession(listOf(readerSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    try {
                        s.setRepeatingRequest(previewRequest.build(), null, camHandler)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "setRepeatingRequest failed: ${e.message}")
                    }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    Log.e(TAG, "createCaptureSession failed")
                }
            }, camHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Create session error: ${e.message}")
        }
    }

    private fun calculateTotalRotation(): Int {
        val displayRotationDegrees = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return (sensorOrientation - displayRotationDegrees + 360) % 360
    }

    private fun yuv420ToNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val nv21 = ByteArray(ySize * 3 / 2)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        extractYPlane(yPlane, nv21, width, height)
        extractUVPlanes(uPlane, vPlane, nv21, width, height)
        return nv21
    }

    private fun extractYPlane(yPlane: Image.Plane, nv21: ByteArray, width: Int, height: Int) {
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        buffer.rewind()
        if (rowStride == width && pixelStride == 1) {
            buffer.get(nv21, 0, width * height)
        } else {
            var offset = 0
            for (row in 0 until height) {
                for (col in 0 until width) {
                    val pos = (row * rowStride) + (col * pixelStride)
                    if (pos < buffer.limit()) {
                        buffer.position(pos)
                        nv21[offset++] = buffer.get()
                    }
                }
            }
        }
    }

    private fun extractUVPlanes(uPlane: Image.Plane, vPlane: Image.Plane, nv21: ByteArray, width: Int, height: Int) {
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val ySize = width * height
        uBuffer.rewind()
        vBuffer.rewind()
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val uvIndex = ySize + (row * width) + (col * 2)
                val uPos = (row * uPlane.rowStride) + (col * uPlane.pixelStride)
                val vPos = (row * vPlane.rowStride) + (col * vPlane.pixelStride)
                if (uPos < uBuffer.limit() && vPos < vBuffer.limit()) {
                    uBuffer.position(uPos)
                    vBuffer.position(vPos)
                    nv21[uvIndex] = vBuffer.get()
                    nv21[uvIndex + 1] = uBuffer.get()
                }
            }
        }
    }
}