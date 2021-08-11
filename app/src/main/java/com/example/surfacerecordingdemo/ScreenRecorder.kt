package com.screencastomatic.app.recording.recorder

import android.content.Intent
import android.media.projection.MediaProjection
import java.io.File

interface ScreenRecorder {
    var isRecording: Boolean

    fun prepare(intent: Intent)
    fun start(callback: Callback)
    fun stop()
    fun pause(callback: (files: List<File>) -> Unit)
    fun resume()
    fun cleanUp(keepRecording: Boolean)

    interface Callback {
        fun onRecordStarted()

        fun onRecordSuccess(files: List<File>)

        fun onRecordFailed(e: Throwable?, duration: Long)

        fun onRecordingInfo(amplitude: Int)
    }
}
