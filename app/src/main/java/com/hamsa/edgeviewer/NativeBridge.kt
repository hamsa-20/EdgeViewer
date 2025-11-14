package com.hamsa.edgeviewer

object NativeBridge {
    init {
        System.loadLibrary("edgeviewer")
    }

    external fun processFrameNV21(nv21: ByteArray, width: Int, height: Int): ByteArray?

    external fun passthroughNV21(nv21: ByteArray, width: Int, height: Int): ByteArray?
}
