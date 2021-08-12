/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.surfacerecordingdemo.recording.hardware;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.surfacerecordingdemo.recording.AudioEncoderConfig;
import com.example.surfacerecordingdemo.recording.Benchmark;
import com.example.surfacerecordingdemo.recording.EncoderCallback;
import com.example.surfacerecordingdemo.recording.EncoderConfig;
import com.example.surfacerecordingdemo.recording.MainFrameRect;
import com.example.surfacerecordingdemo.recording.RecordCallback;
import com.example.surfacerecordingdemo.recording.TextureMovieEncoder;
import com.example.surfacerecordingdemo.recording.WatermarkFrameRect;
import com.example.surfacerecordingdemo.recording.gles.EglCore;
import com.example.surfacerecordingdemo.recording.gles.Texture2dProgram;
import com.example.surfacerecordingdemo.recording.gles.WindowSurface;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Timer;

import javax.microedition.khronos.opengles.GL10;

/**
 * Encode a movie from frames rendered from an external texture image.
 * <p>
 * The object wraps an encoder running on a dedicated thread.  The various control messages
 * may be sent from arbitrary threads (typically the app UI thread).  The encoder thread
 * manages both sides of the encoder (feeding and draining); the only external input is
 * the GL texture.
 * <p>
 * The design is complicated slightly by the need to create an EGL context that shares state
 * with a view that gets restarted if (say) the device orientation changes.  When the view
 * in question is a GLSurfaceView, we don't have full control over the EGL context creation
 * on that side, so we have to bend a bit backwards here.
 * <p>
 * To use:
 * <ul>
 * <li>create TextureMovieEncoder object
 * <li>create an EncoderConfig
 * <li>call TextureMovieEncoder#startRecording() with the config
 * <li>call TextureMovieEncoder#setTextureId() with the texture object that receives frames
 * <li>for each frame, after latching it with SurfaceTexture#updateTexImage(),
 * call TextureMovieEncoder#frameAvailable().
 * </ul>
 * <p>
 * TODO: tweak the API (esp. textureId) so it's less awkward for simple use cases.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class HWTextureMovieEncoder implements TextureMovieEncoder, Runnable, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "TextureMovieEncoder";
    private static final boolean VERBOSE = true;

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;
    private static final int MSG_SET_TEXTURE_ID = 3;
    private static final int MSG_UPDATE_SHARED_CONTEXT = 4;
    private static final int MSG_AUDIO_FRAME_AVAILABLE = 5;
    private static final int MSG_QUIT = 6;
    // ----- accessed exclusively by encoder thread -----
    private WindowSurface mInputWindowSurface;
    private EglCore mEglCore;
    private MainFrameRect mFullScreen;
    private WatermarkFrameRect watermarkFrameRect;
    private int mTextureId;
    private HWVideoEncoderCore mVideoEncoder;

    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;

    private final Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;
    private HandlerThread mVideoFrameSender;
    private Handler mVideoFrameHandler;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private float mTopCropped;
    private float mBottomCropped;
    private float mLeftCropped;
    private float mRightCropped;
    private RecordCallback mRecordCallback;
    private EncoderCallback mCallback;
    private int mVideoWidth;
    private int mVideoHeight;
    private boolean mEnableWatermark;
    private Context context;
    private AudioEncoderConfig audioEncoderConfig;
    private Timer throttlingTimer;
    private Boolean isShownDangerToast = false;
    private Benchmark benchmark;

    public HWTextureMovieEncoder(Context context, @Nullable AudioEncoderConfig audioEncoderConfig) {
        this.context = context;
        this.audioEncoderConfig = audioEncoderConfig;
        benchmark = Benchmark.create(context);
    }

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     * <p>
     * Creates a new thread, which will create an encoder using the provided configuration.
     * <p>
     * Returns after the recorder thread has started and is ready to accept Messages.  The
     * encoder may not yet be fully configured.
     */
    public void startRecording(EncoderConfig config) {
        synchronized (mReadyFence) {
            if (mRunning) {
                return;
            }
            mRunning = true;
            new Thread(this, "TextureMovieEncoder").start();
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }

        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECORDING, config));
    }

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     * <p>
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     * <p>
     * TODO: have the encoder thread invoke a callback on the UI thread just before it shuts down
     * so we can provide reasonable status UI (and let the caller know that movie encoding
     * has completed).
     */
    @Override
    public void stopRecording() {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }


        synchronized (this) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
            mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
        }
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
        benchmark.stop();
    }

    /**
     * Returns true if recording has been started.
     */
    @Override
    public boolean isRecording() {
        synchronized (mReadyFence) {
            return mRunning;
        }
    }

    @Override
    public void updateCropRect(RectF rect) {
        if (mFullScreen != null) {
            mFullScreen.setLeftCropped(rect.left);
            mFullScreen.setTopCropped(rect.top);
            mFullScreen.setRightCropped(rect.right);
            mFullScreen.setBottomCropped(rect.bottom);
        }
    }

    /**
     * @see #frameAvailable(SurfaceTexture, long)
     */
    public void frameAvailable(SurfaceTexture st) {
        frameAvailable(st, st.getTimestamp());
    }

    /**
     * Tells the video recorder that a new frame is available.  (Call from non-encoder thread.)
     * <p>
     * This function sends a message and returns immediately.  This isn't sufficient -- we
     * don't want the caller to latch a new frame until we're done with this one -- but we
     * can get away with it so long as the input frame rate is reasonable and the encoder
     * thread doesn't stall.
     * <p>
     * or have a separate "block if still busy" method that the caller can execute immediately
     * before it calls updateTexImage().  The latter is preferred because we don't want to
     * stall the caller while this thread does work.
     *
     * @param timestamp present timestamp in nanosecond
     */
    public void frameAvailable(SurfaceTexture st, long timestamp) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        float[] transform = new float[16];
        st.getTransformMatrix(transform);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE, (int) (timestamp >> 32), (int) timestamp, transform));
    }

    @Override
    public void audioFrameAvailable(ByteBuffer buffer, int size, boolean endOfStream) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        if (mVideoEncoder != null) {
            mVideoEncoder.enqueueAudioFrame(buffer, size, endOfStream, context);
        }
    }

    /**
     * Tells the video recorder what texture name to use.  This is the external texture that
     * we're receiving camera previews in.  (Call from non-encoder thread.)
     * <p>
     * TODO: do something less clumsy
     */
    public void setTextureId(int id) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, id, 0, null));
    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p>
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new EncoderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }

    @Override
    public void setRecordCallback(RecordCallback recordCallback) {
        mRecordCallback = recordCallback;
        mVideoEncoder.setRecordCallback(mRecordCallback);
    }

    @Override
    public void setEncoderCallback(EncoderCallback encoderCallback) {
        mCallback = encoderCallback;
    }

    /**
     * Starts recording.
     */
    private void handleStartRecording(EncoderConfig config) {
        prepareEncoder(config);
    }

    /**
     * Handles notification of an available frame.
     * <p>
     * The texture is rendered onto the encoder's input surface, along with a moving
     * box (just because we can).
     * <p>
     *
     * @param timestampNanos The frame's timestamp, from SurfaceTexture.
     */
    private void handleFrameAvailable(long timestampNanos, float[] transform) {
        if (shouldStart && mVideoEncoder != null) {
            GLES20.glFlush();
            GLES20.glFinish();

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

            mFullScreen.drawFrame(mTextureId, transform);
            if (mEnableWatermark) {
                watermarkFrameRect.drawFrame(mTextureId, transform);
            }

            mInputWindowSurface.setPresentationTime(timestampNanos);
            mInputWindowSurface.swapBuffers();

            GLES20.glFlush();
            GLES20.glFinish();

            benchmark.tick();
        }
    }

    /**
     * Handles a request to stop encoding.
     */
    private void handleStopRecording() {
        mVideoEncoder.drainEncoder(true);
        releaseEncoder();
    }

    /**
     * Sets the texture name that SurfaceTexture will use when frames are received.
     */
    private void handleSetTexture(int id) {
        mTextureId = id;
    }

    /**
     * Tears down the EGL surface and context we've been using to feed the MediaCodec input
     * surface, and replaces it with a new one that shares with the new context.
     * <p>
     * This is useful if the old context we were sharing with went away (maybe a GLSurfaceView
     * that got torn down) and we need to hook up with the new one.
     */
    private void handleUpdateSharedContext(EGLContext newSharedContext) {
        // Release the EGLSurface and EGLContext.
        mInputWindowSurface.releaseEglSurface();
        mFullScreen.release(false);
        if (watermarkFrameRect != null) {
            watermarkFrameRect.release(false);
            watermarkFrameRect = null;
        }
        mEglCore.release();

        // Create a new EGLContext and recreate the window surface.
        mEglCore = new EglCore(newSharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface.recreate(mEglCore);
        mInputWindowSurface.makeCurrent();

        // Create new programs and such for the new context.
        mFullScreen = new MainFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mFullScreen.setTopCropped(mTopCropped);
        mFullScreen.setBottomCropped(mBottomCropped);
        mFullScreen.setLeftCropped(mLeftCropped);
        mFullScreen.setRightCropped(mRightCropped);

        configureWatermark();
    }

    private boolean shouldStart = false;

    private void prepareEncoder(EncoderConfig config) {
        mTopCropped = config.mTopCropped;
        mBottomCropped = config.mBottomCropped;
        mLeftCropped = config.mLeftCropped;
        mRightCropped = config.mRightCropped;

        mVideoHeight = (int) (config.mHeight * (1f - mTopCropped - mBottomCropped));
        if (mVideoHeight % 2 != 0) {
            mVideoHeight += 1; // Pixels must be even
        }

        mVideoWidth = (int) (config.mWidth * (1f - mLeftCropped - mRightCropped));
        if (mVideoWidth % 2 != 0) {
            mVideoWidth += 1;
        }

        try {
            mVideoEncoder = new HWVideoEncoderCore(context, mVideoWidth, mVideoHeight, config.mBitRate, config.mOutputFile, config.mEnableAudio, audioEncoderConfig);
            mVideoEncoder.setRecordCallback(mRecordCallback);

            if (mCallback != null) {
                mCallback.onEncoderPrepared();
            }

            mEglCore = new EglCore(config.mEglContext, EglCore.FLAG_RECORDABLE);
            mInputWindowSurface = new WindowSurface(mEglCore, mVideoEncoder.getInputSurface(), true);
            mInputWindowSurface.makeCurrent();

            mFullScreen = new MainFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
            mFullScreen.setTopCropped(config.mTopCropped);
            mFullScreen.setBottomCropped(config.mBottomCropped);
            mFullScreen.setLeftCropped(mLeftCropped);
            mFullScreen.setRightCropped(mRightCropped);

            mEnableWatermark = config.mEnableWatermark;

            configureWatermark();

            mTextureId = mFullScreen.createTextureObject();

            mVideoFrameSender = new HandlerThread("SurfaceFrameSender");
            mVideoFrameSender.start();
            mVideoFrameHandler = new Handler(mVideoFrameSender.getLooper());
            mSurfaceTexture = new SurfaceTexture(mTextureId);
            mSurfaceTexture.setOnFrameAvailableListener(this, mVideoFrameHandler); // 为了不阻塞TextureMovieEncoder ，需要额外的线程
            mSurfaceTexture.setDefaultBufferSize(config.mWidth, config.mHeight);
            mSurface = new Surface(mSurfaceTexture);

            if (mCallback != null) {
                mCallback.onInputSurfacePrepared(mSurface);
            }

            mHandler.postDelayed(() -> {
                shouldStart = true;
                if (mCallback != null) {
                    mCallback.onStartRecord();
                }
                mSurfaceTexture.updateTexImage();
                benchmark.start();
            }, config.mDelayMs);

            mVideoEncoder.cb = new HWVideoEncoderCore.Callback() {
                @Override
                public void onVideoFrameProceed() {
                    mSurfaceTexture.updateTexImage();
                }

                @Override
                public void onAudioFrameProceed() {
                }
            };
        } catch (Exception ex) {
            if (mCallback != null) {
                mCallback.onError(ex);
            }
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        frameAvailable(surfaceTexture);
    }

    private void configureWatermark() {
        if (mEnableWatermark) {
            watermarkFrameRect = new WatermarkFrameRect(context, new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D), new Size(mVideoWidth, mVideoHeight));
        }
    }

    private void releaseEncoder() {
        if (mVideoEncoder != null) {
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mInputWindowSurface != null) {
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);
            mFullScreen = null;
        }

        if (watermarkFrameRect != null) {
            watermarkFrameRect.release(false);
            watermarkFrameRect = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
        if (mVideoFrameHandler != null) {
            mVideoFrameHandler = null;
        }
        if (mVideoFrameSender != null) {
            mVideoFrameSender.quit();
            mVideoFrameSender = null;
        }
    }


    /**
     * Encoder configuration.
     * <p>
     * Object is immutable, which means we can safely pass it between threads without
     * explicit synchronization (and don't need to worry about it getting tweaked out from
     * under us).
     * <p>
     * TODO: make frame rate and iframe interval configurable?  Maybe use builder pattern
     * with reasonable defaults for those and bit rate.
     */

    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {
        private WeakReference<HWTextureMovieEncoder> mWeakEncoder;

        public EncoderHandler(HWTextureMovieEncoder encoder) {
            mWeakEncoder = new WeakReference<HWTextureMovieEncoder>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            HWTextureMovieEncoder encoder = mWeakEncoder.get();
            if (encoder == null) {
                return;
            }

            switch (what) {
                case MSG_START_RECORDING:
                    encoder.handleStartRecording((EncoderConfig) obj);
                    break;
                case MSG_STOP_RECORDING:
                    encoder.handleStopRecording();
                    break;
                case MSG_FRAME_AVAILABLE:
                    long presentationTimestamp = (((long) inputMessage.arg1) << 32) |
                            (((long) inputMessage.arg2) & 0xffffffffL);
                    encoder.handleFrameAvailable(presentationTimestamp, (float[]) inputMessage.obj);
                    break;
                case MSG_SET_TEXTURE_ID:
                    encoder.handleSetTexture(inputMessage.arg1);
                    break;
                case MSG_UPDATE_SHARED_CONTEXT:
                    encoder.handleUpdateSharedContext((EGLContext) inputMessage.obj);
                    break;
                case MSG_QUIT:
                    Looper.myLooper().quit();
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }
}
