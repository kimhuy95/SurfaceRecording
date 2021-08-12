package com.example.surfacerecordingdemo.recording

import android.content.Context
import android.os.Environment
import com.example.surfacerecordingdemo.BuildConfig
import com.example.surfacerecordingdemo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

data class Checkpoint(val recordingTimeSeconds: Long, val deltaTimeMillis: Long)

interface Benchmark {
    fun start()
    fun tick()
    fun stop()

    companion object {
        @JvmStatic
        fun create(context: Context): Benchmark {
            return if (BuildConfig.DEBUG) {
                DefaultBenchmark(context)
            } else {
                DummyBenchmark()
            }
        }
    }
}

class DefaultBenchmark(private val context: Context) : Benchmark {
    private var startMs = 0L
    private var lastMs = 0L
    private val checkpoints = mutableListOf<Checkpoint>()

    override fun start() {
        startMs = System.currentTimeMillis()
    }

    override fun tick() {
        if (lastMs == 0L) {
            lastMs = System.currentTimeMillis()
            return
        }

        val currentMs = System.currentTimeMillis()
        val recordingTimeSeconds = (currentMs - startMs) / 1000
        val deltaTimeMillis = currentMs - lastMs

        if (checkpoints.isEmpty() || recordingTimeSeconds != checkpoints.last().recordingTimeSeconds) {
            val checkpoint = Checkpoint(recordingTimeSeconds, deltaTimeMillis)
            checkpoints.add(checkpoint)
        }

        lastMs = currentMs
    }

    override fun stop() {
        GlobalScope.launch(Dispatchers.IO) {
            val outputFile = createOutputFile()
            BufferedWriter(FileWriter(outputFile, true)).use { writer ->
                writer.append("Recording time (s),deltaTime (ms)${System.lineSeparator()}")
                for (checkpoint in checkpoints) {
                    writer.append("${checkpoint.recordingTimeSeconds},${checkpoint.deltaTimeMillis}")
                    writer.append(System.lineSeparator())
                }
            }
        }
    }

    private fun createOutputFile(): File {
        val dir = File(
            Environment.getExternalStorageDirectory(),
            "${context.getString(R.string.app_name)}/Benchmarks"
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
        val filename = "Benchmark_${dateFormat.format(Date(System.currentTimeMillis()))}.csv"
        return File(dir, filename)
    }
}

class DummyBenchmark : Benchmark {
    override fun start() {
    }

    override fun stop() {
    }

    override fun tick() {
    }
}