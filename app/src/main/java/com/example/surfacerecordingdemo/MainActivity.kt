package com.example.surfacerecordingdemo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.surfacerecordingdemo.gles.FullFrameRect
import com.example.surfacerecordingdemo.gles.Texture2dProgram
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity() {
    private lateinit var surfaceView: GLSurfaceView
    private lateinit var fullscreen: FullFrameRect
    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var surface: Surface
    private lateinit var projection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var mediaRecorder: MediaRecorder

    private var textureId: Int = 0

    private val stMatrix = FloatArray(16)
    private val mediaProjectionManager by lazy { getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    private val permissions = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private val renderer = object : GLSurfaceView.Renderer {
        override fun onDrawFrame(gl: GL10) {

            surfaceTexture.updateTexImage()
            Log.d("ggwp", "onDrawFrame ${System.currentTimeMillis()}")

            surfaceTexture.getTransformMatrix(stMatrix)
            fullscreen.drawFrame(textureId, stMatrix)

        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        }

        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            Log.d("ggwp", "onSurfaceCreated")
            fullscreen = FullFrameRect(Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT))
            textureId = fullscreen.createTextureObject()

            surfaceTexture = SurfaceTexture(textureId)
            surfaceTexture.setOnFrameAvailableListener {
                Log.d("ggwp", "onFrameAvailable")
                surfaceView.requestRender()
            }
            surface = Surface(surfaceTexture)

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        surfaceView.setEGLContextClientVersion(3)
        surfaceView.setRenderer(renderer)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        surfaceView.preserveEGLContextOnPause = true

        val recordButton = findViewById<Button>(R.id.recordButton)
        recordButton.setOnClickListener { requestProjection() }

        val stopButton = findViewById<Button>(R.id.stopButton)
        stopButton.setOnClickListener { stopRecording() }
    }

    override fun onDestroy() {
        super.onDestroy()

        surfaceTexture.release()
        surface.release()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2 && data != null) {
            projection = mediaProjectionManager.getMediaProjection(resultCode, data)
            startRecording()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            requestProjection()
        }
    }

    private fun startRecording() {
        createVirtualDisplay()
        createMediaRecorder()

        mediaRecorder.start()
    }

    private fun stopRecording() {
        virtualDisplay.release()
        projection.stop()
        mediaRecorder.stop()
        mediaRecorder.release()

    }

    private fun createVirtualDisplay() {
        val displayMetrics = resources.displayMetrics
        virtualDisplay = projection.createVirtualDisplay(
            getString(R.string.app_name),
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )
    }

    private fun createMediaRecorder() {
        val displayMetrics = resources.displayMetrics

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)

            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(1000 * 1000 * 4)

            setOutputFile(createOutputFile().absolutePath)
            setVideoSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
            setInputSurface(surfaceView.holder.surface)
            prepare()
        }
    }

    private fun requestProjection() {
        if (hasPermissions()) {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 2)
        } else {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
        }
    }

    private fun hasPermissions(): Boolean {
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun createOutputFile(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "SurfaceRecordingDemo")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "output_${System.currentTimeMillis()}.mp4")
    }
}