package com.hamsa.edgeviewer

object NativeBridge {
    init {
        try {
            // Load OpenCV first - use the correct library name
            // For OpenCV Android 4.x, the correct name is usually "opencv_java4" or "opencv_java3"
            System.loadLibrary("opencv_java4")
        } catch (e: UnsatisfiedLinkError) {
            // Try alternative names if the first one fails
            try {
                System.loadLibrary("opencv_java3")
            } catch (e2: UnsatisfiedLinkError) {
                try {
                    System.loadLibrary("opencv_java")
                } catch (e3: UnsatisfiedLinkError) {
                    // Last resort - try without version
                    System.loadLibrary("opencv")
                }
            }
        }

        // Load our native library
        System.loadLibrary("edgeviewer")
    }

    external fun processFrameNV21(nv21: ByteArray, width: Int, height: Int): ByteArray?
    external fun passthroughNV21(nv21: ByteArray, width: Int, height: Int): ByteArray?
}