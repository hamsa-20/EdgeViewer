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
import java.nio.ByteBuffer

/**
 * Simple Camera2 helper.
 *
 * Usage:
 *   val helper = CameraHelper(context, Size(640,480)) { nv21, w, h -> handleFrame(nv21, w, h) }
 *   helper.start()
 *   helper.stop()
 */
class CameraHelper(
    private val context: Context,
    private val desiredSize: Size = Size(640, 480),
    private val onFrame: (nv21: ByteArray, width: Int, height: Int) -> Unit
) {
    private val TAG = "CameraHelper"

    private val camThread = HandlerThread("CameraThread").apply { start() }
    private val camHandler = Handler(camThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    @SuppressLint("MissingPermission")
    fun start() {
        // pick back camera
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.first() // fallback to first

        // create ImageReader
        imageReader = ImageReader.newInstance(
            desiredSize.width,
            desiredSize.height,
            ImageFormat.YUV_420_888,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val nv21 = yuv420ToNV21(img)
                    onFrame(nv21, img.width, img.height)
                } catch (e: Exception) {
                    Log.e(TAG, "frame convert error: ${e.message}")
                } finally {
                    img.close()
                }
            }, camHandler)
        }

        // open camera
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                createSession()
            }

            override fun onDisconnected(device: CameraDevice) {
                device.close()
                cameraDevice = null
            }

            override fun onError(device: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error $error")
                device.close()
                cameraDevice = null
            }
        }, camHandler)
    }

    fun stop() {
        try {
            session?.close()
            session = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "stop error ${e.message}")
        } finally {
            camThread.quitSafely()
        }
    }

    private fun createSession() {
        val cam = cameraDevice ?: return
        val readerSurface = imageReader?.surface ?: return

        val previewRequest = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(readerSurface)
            // auto controls
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
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
    }

    /**
     * Convert YUV_420_888 Image to NV21 byte array.
     *
     * On android, `YUV_420_888` is represented as three separate planes, one for each
     * of the Y, U, and V channels. The U and V planes are subsampled by a factor of 2
     * in both horizontal and vertical directions.
     *
     * NV21 is a semi-planar format, where the Y plane is followed by an interleaved
     * VU plane.
     */
    private fun yuv420ToNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()

        // Size of the NV21 buffer
        val nv21 = ByteArray(width * height + 2 * (width * height / 4))

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)

        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        var pos = width*height
        // Interleave U and V planes
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vPos = row * vRowStride + col * vPixelStride
                val uPos = row * uRowStride + col * uPixelStride

                nv21[pos++] = vBuffer.get(vPos)
                nv21[pos++] = uBuffer.get(uPos)
            }
        }
        return nv21
    }
}