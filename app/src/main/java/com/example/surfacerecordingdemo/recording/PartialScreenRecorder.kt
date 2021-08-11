package com.example.surfacerecordingdemo.recording

import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.opengl.EGL14
import android.os.Environment
import android.util.Size
import android.view.Surface
import com.example.surfacerecordingdemo.R
import com.example.surfacerecordingdemo.recording.hardware.HWTextureMovieEncoder
import com.screencastomatic.app.recording.recorder.ScreenRecorder
import java.io.File

class PartialScreenRecorder(private val context: Context, private val projection: MediaProjection) :
    ScreenRecorder {
    private val projectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var mediaRecorder: TextureMovieEncoder? = null
    private var callback: ScreenRecorder.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var files = mutableListOf<File>()

    override var isRecording = false

    override fun prepare(intent: Intent) {
    }

    override fun start(callback: ScreenRecorder.Callback) {
        this.callback = callback
        startRecorder()
    }

    override fun stop() {
        callback?.onRecordSuccess(files)
    }

    override fun resume() {
        startRecorder()
    }

    override fun pause(callback: (files: List<File>) -> Unit) {
        mediaRecorder?.setRecordCallback(object : RecordCallback {
            override fun onRecordSuccess(
                files: List<File>,
                coverPath: String?,
                duration: Long
            ) {
                callback(files)
            }

            override fun onRecordFailed(e: Throwable?, duration: Long) {
                // try to notify callback in case record failed
                callback(mutableListOf())
            }

            override fun onRecordStarted() {}
        })
        stopRecorder()
    }

    override fun cleanUp(keepRecording: Boolean) {
        if (!keepRecording) {
            files.forEach { it.delete() }
        }
        files = mutableListOf()

        mediaRecorder = null
        callback = null
        virtualDisplay = null
    }

    private fun startRecorder() {
        val file = createOutputFile()
        files.add(file)
        mediaRecorder = createVideoRecorder()
        mediaRecorder?.setEncoderCallback(object : EncoderCallback {
            override fun onStartRecord() {
                this@PartialScreenRecorder.callback?.onRecordStarted()
            }

            override fun onInputSurfacePrepared(surface: Surface) {
                virtualDisplay?.surface = surface
            }

            override fun onEncoderPrepared() {
                configureMediaProjection()
                isRecording = true
            }

            override fun onError(e: Throwable) {
                callback?.onRecordFailed(e, 0)
            }
        })
        attachRecorder(file)
    }

    private fun stopRecorder() {
        stopProjection()
        isRecording = false
    }

    private fun createVideoRecorder(): TextureMovieEncoder {
        return HWTextureMovieEncoder(
            context,
            AudioEncoderConfig(44100, AudioFormat.CHANNEL_IN_MONO, 1)
        )
    }

    private fun configureMediaProjection() {
        val fullScreenSize = Size(Utils.getScreenWidth(context), Utils.getRealHeight(context))

        virtualDisplay = projection.createVirtualDisplay(
            context.getString(R.string.app_name),
            fullScreenSize.width,
            fullScreenSize.height,
            context.resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null,
            null,
            null
        )
    }

    @Synchronized
    fun attachRecorder(file: File) {
        val eglContext = EGL14.eglGetCurrentContext()

        val cropRect = getCropRect()

        mediaRecorder?.apply {
            startRecording(
                EncoderConfig(
                    file,
                    Utils.getScreenWidth(context), Utils.getRealHeight(context),
                    cropRect.top, cropRect.bottom, cropRect.left, cropRect.right,
                    1000 * 1000 * 4, eglContext,
                    false,
                    true,
                    0
                )
            )
        }
    }

    /**
     * Step 4，detach encoder from virtual screen and stop recoding.
     *
     * @return true if success
     */
    @Synchronized
    fun detachRecorder() {
        mediaRecorder?.stopRecording()
        virtualDisplay?.surface = null
    }

    private fun stopProjection() {
        detachRecorder()
        virtualDisplay?.release()
        projection.stop()

        virtualDisplay = null
        mediaRecorder = null
    }

    private fun getCropRect(): RectF {
        return RectF(0f, 0f, 0f, 0f)
    }

    private fun createOutputFile(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "SurfaceRecordingDemo")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "output_${System.currentTimeMillis()}.mp4")
    }
}