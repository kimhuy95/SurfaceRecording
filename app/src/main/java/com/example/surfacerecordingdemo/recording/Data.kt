package com.example.surfacerecordingdemo.recording

import java.nio.ByteBuffer

data class DataFrame(
    val byteBuffer: ByteBuffer,
    val size: Int,
    val endOfStream: Boolean,
    val presentTimeUs: Long
)