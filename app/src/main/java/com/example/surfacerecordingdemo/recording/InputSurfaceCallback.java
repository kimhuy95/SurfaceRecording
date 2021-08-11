package com.example.surfacerecordingdemo.recording;

import android.view.Surface;

public interface InputSurfaceCallback {
    /**
     * called when surface prepared
     *
     * @param surface a prepared surface
     */
    void onInputSurfacePrepared(Surface surface);
}