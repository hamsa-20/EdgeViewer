package com.hamsa.edgeviewer

object NativeBridge {
    init {
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: UnsatisfiedLinkError) {
            try { System.loadLibrary("opencv_java3") } catch (_: UnsatisfiedLinkError) {
                try { System.loadLibrary("opencv_java") } catch (_: UnsatisfiedLinkError) {
                    try { System.loadLibrary("opencv") } catch (_: UnsatisfiedLinkError) {}
                }
            }
        }
        System.loadLibrary("edgeviewer")
    }

    external fun processFrameNV21(
        nv21: ByteArray,
        width: Int,
        height: Int,
        rotation: Int
    ): ByteArray?

}
