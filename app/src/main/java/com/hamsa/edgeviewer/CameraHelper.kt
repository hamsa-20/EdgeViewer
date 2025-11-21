package com.hamsa.edgeviewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import java.nio.BufferUnderflowException
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
    private var sensorOrientation = 0
    private var displayRotation = Surface.ROTATION_0

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    // Public TextureView used by Compose
    val previewTextureView = TextureView(context)

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
                chars.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId == null) {
                Log.e(TAG, "No camera id available")
                return
            }

            val chars = cameraManager.getCameraCharacteristics(cameraId!!)
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            imageReader = ImageReader.newInstance(
                desiredSize.width,
                desiredSize.height,
                ImageFormat.YUV_420_888,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        // convert to NV21 safely
                        val nv21 = try {
                            yuv420ToNV21Safe(img)
                        } catch (e: Exception) {
                            Log.e(TAG, "yuv conversion error: ${e.message}")
                            null
                        }

                        val totalRotation = calculateTotalRotation()
                        nv21?.let { onFrame(it, img.width, img.height, totalRotation) }

                    } finally {
                        img.close()
                    }
                }, camHandler)
            }

            if (previewTextureView.isAvailable) openCamera()
            else setTextureListener()

        } catch (e: Exception) {
            Log.e(TAG, "start() error: ${e.message}", e)
        }
    }

    private fun setTextureListener() {
        previewTextureView.surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    previewTextureView.surfaceTextureListener = null
                    openCamera()
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
    }

    private fun openCamera() {
        try {
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
                    Log.e(TAG, "CameraDevice error: $error")
                }
            }, camHandler)
        } catch (e: Exception) {
            Log.e(TAG, "openCamera error: ${e.message}", e)
        }
    }

    fun stop() {
        orientationListener.disable()
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
    }

    private fun createSession() {
        val cam = cameraDevice ?: return
        val readerSurface = imageReader?.surface ?: return

        val texture = previewTextureView.surfaceTexture ?: run {
            Log.e(TAG, "surfaceTexture null in createSession()")
            return
        }

        try {
            texture.setDefaultBufferSize(desiredSize.width, desiredSize.height)
        } catch (e: Exception) {
            Log.w(TAG, "setDefaultBufferSize failed: ${e.message}")
        }

        val previewSurface = Surface(texture)
        val surfaces = mutableListOf<Surface>().apply {
            add(previewSurface)
            add(readerSurface)
        }

        try {
            val builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                addTarget(readerSurface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }

            cam.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(sess: CameraCaptureSession) {
                    session = sess
                    try {
                        sess.setRepeatingRequest(builder.build(), null, camHandler)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "setRepeatingRequest failed: ${e.message}")
                    }
                }

                override fun onConfigureFailed(sess: CameraCaptureSession) {
                    Log.e(TAG, "createCaptureSession failed")
                }
            }, camHandler)

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Create session error: ${e.message}", e)
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

    /**
     * Safe conversion of Image (YUV_420_888) -> NV21 byte array.
     * Handles arbitrary rowStride and pixelStride and avoids reading out-of-bounds.
     */
    private fun yuv420ToNV21Safe(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        try {
            // Copy Y
            var pos = 0
            if (yPixelStride == 1 && yRowStride == width) {
                // fast path
                yBuffer.get(nv21, 0, ySize)
                pos = ySize
            } else {
                // row by row
                val row = ByteArray(yRowStride)
                for (r in 0 until height) {
                    yBuffer.position(r * yRowStride)
                    val toRead = minOf(yRowStride, yBuffer.remaining())
                    yBuffer.get(row, 0, toRead)
                    // copy only width pixels with pixelStride
                    if (yPixelStride == 1) {
                        System.arraycopy(row, 0, nv21, pos, width)
                        pos += width
                    } else {
                        // pixelStride > 1
                        var colPos = 0
                        var i = 0
                        while (colPos < width && i < toRead) {
                            nv21[pos++] = row[i]
                            i += yPixelStride
                            colPos++
                        }
                    }
                }
            }

            // Copy interleaved VU (NV21: V then U)
            val uvHeight = height / 2
            val tmpU = ByteArray(uRowStride)
            val tmpV = ByteArray(vRowStride)
            for (r in 0 until uvHeight) {
                // read row from v and u buffers
                vBuffer.position(r * vRowStride)
                val vToRead = minOf(vRowStride, vBuffer.remaining())
                vBuffer.get(tmpV, 0, vToRead)

                uBuffer.position(r * uRowStride)
                val uToRead = minOf(uRowStride, uBuffer.remaining())
                uBuffer.get(tmpU, 0, uToRead)

                var cu = 0
                var cv = 0
                for (c in 0 until width / 2) {
                    // ensure we don't index out of bounds
                    val vIndex = cv
                    val uIndex = cu
                    val vByte = if (vIndex < vToRead) tmpV[vIndex] else 0
                    val uByte = if (uIndex < uToRead) tmpU[uIndex] else 0
                    // V then U (NV21)
                    nv21[pos++] = vByte
                    nv21[pos++] = uByte
                    cu += uPixelStride
                    cv += vPixelStride
                }
            }
        } catch (e: BufferUnderflowException) {
            Log.e(TAG, "BufferUnderflow in yuv conversion: ${e.message}")
            // return empty to signal failure
            return ByteArray(0)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in yuv conversion: ${e.message}")
            return ByteArray(0)
        }

        return nv21
    }
}
