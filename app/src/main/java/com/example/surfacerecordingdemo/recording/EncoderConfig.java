package com.example.surfacerecordingdemo.recording;

import android.opengl.EGLContext;

import java.io.File;

public class EncoderConfig {
    public final File mOutputFile;
    public final int mWidth;
    public final int mHeight;
    public final float mTopCropped;
    public final float mBottomCropped;
    public final float mLeftCropped;
    public final float mRightCropped;
    public final int mBitRate;
    public final EGLContext mEglContext;
    public final Boolean mEnableWatermark;
    public boolean mEnableAudio;
    public final int mDelayMs;

    public EncoderConfig(File outputFile, int width, int height,
                         float topCropped, float bottomCropped, float leftCropped, float rightCropped,
                         int bitRate,
                         EGLContext sharedEglContext,
                         Boolean enableAudio,
                         Boolean enableWatermark,
                         int delayMs) {
        mOutputFile = outputFile;
        mWidth = width;
        mHeight = height;
        mTopCropped = topCropped;
        mBottomCropped = bottomCropped;
        mLeftCropped = leftCropped;
        mRightCropped = rightCropped;
        mBitRate = bitRate;
        mEglContext = sharedEglContext;
        mEnableAudio = enableAudio;
        mEnableWatermark = enableWatermark;
        mDelayMs = delayMs;
    }

    @Override
    public String toString() {
        return "EncoderConfig: " + mWidth + "x" + mHeight
                + ", Crop with: " + mTopCropped + " and " + mBottomCropped
                + "@" + mBitRate +
                " to '" + mOutputFile.toString() + "' ctxt=" + mEglContext;
    }
}
