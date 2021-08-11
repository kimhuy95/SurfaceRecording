package com.example.surfacerecordingdemo.recording

import android.view.Surface

interface EncoderCallback {
    fun onStartRecord()

    fun onInputSurfacePrepared(surface: Surface)

    fun onEncoderPrepared()

    fun onError(e: Throwable)
}