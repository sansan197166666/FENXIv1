package com.carriez.flutter_hbb

import java.nio.ByteBuffer

object DataTransferManager {
    private var imageBuffer: ByteBuffer? = null

    fun setImageBuffer(buffer: ByteBuffer) {
        imageBuffer = buffer
    }

    fun getImageBuffer(): ByteBuffer? {
        return imageBuffer
    }
}
