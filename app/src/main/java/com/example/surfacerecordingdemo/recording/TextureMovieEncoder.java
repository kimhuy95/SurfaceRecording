package com.example.surfacerecordingdemo.recording;

import android.graphics.RectF;

import java.nio.ByteBuffer;

public interface TextureMovieEncoder {
    public void updateCropRect(RectF rect);
    public void setEncoderCallback(EncoderCallback encoderCallback);
    public void setRecordCallback(RecordCallback recordCallback);
    public void startRecording(EncoderConfig config);
    public void audioFrameAvailable(ByteBuffer buffer, int size, boolean endOfStream);
    public void stopRecording();
    public boolean isRecording();
}
