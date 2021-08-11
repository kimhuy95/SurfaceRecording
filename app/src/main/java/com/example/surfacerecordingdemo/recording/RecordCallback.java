package com.example.surfacerecordingdemo.recording;

import java.io.File;
import java.util.List;

/**
 * Record callback
 */
public interface RecordCallback {
    void onRecordStarted();

    void onRecordSuccess(List<File> files, String coverPath, long duration);

    void onRecordFailed(Throwable e, long duration);
}
