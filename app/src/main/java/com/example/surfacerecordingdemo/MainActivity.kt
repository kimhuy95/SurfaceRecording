package com.example.surfacerecordingdemo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File


class MainActivity : AppCompatActivity() {
    private lateinit var surfaceView: SurfaceView
    private lateinit var projection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay

    private val mediaProjectionManager by lazy { getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    private val overlayManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val permissions =
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!Settings.canDrawOverlays(this)) {
            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(myIntent)
        }

        val recordButton = findViewById<Button>(R.id.recordButton)
        recordButton.setOnClickListener { requestProjection() }

        val stopButton = findViewById<Button>(R.id.stopButton)
        stopButton.setOnClickListener { stopRecording() }
    }

    override fun onDestroy() {
        super.onDestroy()
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
        addSurfaceView()
    }

    private fun addSurfaceView() {
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                val a = 12

            }

            override fun surfaceCreated(holder: SurfaceHolder) {
                val a = 12
                createVirtualDisplay()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                val a = 12
            }
        })

        val params = WindowManager.LayoutParams(
            256,
            256,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100
        overlayManager.addView(surfaceView, params)
    }

    private fun stopRecording() {
        virtualDisplay.release()
        projection.stop()
        overlayManager.removeViewImmediate(surfaceView)
    }

    private fun createVirtualDisplay() {
        val displayMetrics = resources.displayMetrics
        virtualDisplay = projection.createVirtualDisplay(
            getString(R.string.app_name),
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surfaceView.holder.surface,
            null,
            null
        )
    }

    private fun requestProjection() {
        if (hasPermissions()) {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 2)
        } else {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
        }
    }

    private fun hasPermissions(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun createOutputFile(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "SurfaceRecordingDemo")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "output_${System.currentTimeMillis()}.mp4")
    }
}