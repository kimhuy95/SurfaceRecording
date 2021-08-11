package com.example.surfacerecordingdemo.recording

import android.media.MediaCodecInfo
import android.media.MediaCodecList

enum class EncoderType {
    Software,
    Hardware
}

data class EncoderInfo(val type: EncoderType, val name: String?)

object CodecUtils {
    val encoderInfo: EncoderInfo
        get() {
            val codecName = selectCodec("video/avc")?.name
            val type = if (codecName == "OMX.google.h264.encoder") {
                EncoderType.Software
            } else {
                EncoderType.Hardware
            }
            return EncoderInfo(type, codecName)
        }

    fun selectCodec(mimeType: String): MediaCodecInfo? {
        val numCodecs: Int = MediaCodecList.getCodecCount()
        for (i in 0 until numCodecs) {
            val codecInfo: MediaCodecInfo = MediaCodecList.getCodecInfoAt(i)
            if (!codecInfo.isEncoder) {
                continue
            }
            val types: Array<String> = codecInfo.supportedTypes
            for (j in types.indices) {
                if (types[j].equals(mimeType, ignoreCase = true)) {
                    return codecInfo
                }
            }
        }
        return null
    }
}