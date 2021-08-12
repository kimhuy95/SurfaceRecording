package com.example.surfacerecordingdemo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.surfacerecordingdemo.recording.PartialScreenRecorder
import com.screencastomatic.app.recording.recorder.ScreenRecorder
import java.io.File


class MainActivity : AppCompatActivity() {
    private lateinit var projection: MediaProjection
    private lateinit var screenRecorder: PartialScreenRecorder

    private val mediaProjectionManager by lazy { getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
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
        screenRecorder = PartialScreenRecorder(this, projection)
        screenRecorder.start(object : ScreenRecorder.Callback {
            override fun onRecordFailed(e: Throwable?, duration: Long) {
            }

            override fun onRecordStarted() {
            }

            override fun onRecordSuccess(files: List<File>) {
            }

            override fun onRecordingInfo(amplitude: Int) {
            }
        })
    }

    private fun stopRecording() {
        screenRecorder.pause { }
        Toast.makeText(this, "Output video in SurfaceRecordingDemo folder", Toast.LENGTH_LONG)
            .show()
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
}