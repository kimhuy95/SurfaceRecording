///*
// * Copyright 2014 Google Inc. All rights reserved.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.example.surfacerecordingdemo.partial.software;
//
//import android.content.Context;
//import android.media.MediaCodec;
//import android.media.MediaCodecInfo;
//import android.media.MediaFormat;
//import android.media.MediaMuxer;
//import android.os.Handler;
//import android.os.Looper;
//import android.util.Log;
//import android.util.Size;
//import android.view.Surface;
//
//import androidx.annotation.Nullable;
//import androidx.annotation.RequiresApi;
//
//import com.example.surfacerecordingdemo.partial.AudioEncoderConfig;
//import com.screencastomatic.app.recording.recorder.partial.RecordCallback;
//import com.screencastomatic.app.recording.recorder.partial.Utils;
//import com.screencastomatic.app.recording.recorder.partial.VideoEncoderCore;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.util.ArrayList;
//import java.util.List;
//
//import static android.os.Build.VERSION_CODES.LOLLIPOP;
//import static com.screencastomatic.app.recording.BroadcastReceiverUtilKt.sendRecoverRecordingOnErrorBroadcast;
//import static com.screencastomatic.app.recording.RecordingKt.AUDIO_BITRATE;
//
///**
// * This class wraps up the core components used for surface-input video encoding.
// * <p>
// * Once created, frames are fed to the input surface.  Remember to provide the presentation
// * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
// * producer side doesn't get backed up.
// * <p>
// * This class is not thread-safe, with one exception: it is valid to use the input surface
// * on one thread, and drain the output on a different thread.
// */
//@RequiresApi(LOLLIPOP)
//public class SWVideoEncoderCore implements VideoEncoderCore {
//    private static final String TAG = "VideoEncoderCore";
//
//    private Context context;
//
//    private static final int FRAME_RATE = 24;
//    private static final int VIDEO_TIMEOUT_USEC = (1000 / FRAME_RATE) * 1000;
//    private static final int AUDIO_TIMEOUT_USEC = 10000;
//
//    public static final int MAX_INPUT_SIZE = 0;
//    private static final String VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;    // H.264 Advanced Video Coding
//    private static final String AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;
//
//    /**
//     * 5 seconds between I-frames
//     */
//    private static final int IFRAME_INTERVAL = 1;
//    /**
//     * Save path
//     */
//    private final String mPath;
//
//    private Surface mInputSurface;
//    private MediaMuxer mMuxer;
//    private MediaCodec mVideoEncoder;
//    private MediaCodec mAudioEncoder;
//    private MediaCodec.BufferInfo mVBufferInfo;
//    private MediaCodec.BufferInfo mABufferInfo;
//    private int mVTrackIndex;
//    private int mATrackIndex;
//    private boolean mMuxerStarted;
//    private boolean mStreamEnded;
//    private long mRecordStartedAt = 0;
//
//    private RecordCallback mCallback;
//    private Handler mMainHandler;
//    // is audio empty , if true, we should add a frame of audio data to the muxer
//    private boolean mEnableAudio;
//    private boolean mIsAudioEmpty;
//    private String mCoverPath;
//
//    /**
//     * Configures encoder and muxer state, and prepares the input Surface.
//     */
//    public SWVideoEncoderCore(Context context, int width, int height, int bitRate, File outputFile, boolean enableAudio, @Nullable AudioEncoderConfig audioEncoderConfig)
//            throws IOException {
//        this.context = context;
//        mMainHandler = new Handler(Looper.getMainLooper());
//        mVBufferInfo = new MediaCodec.BufferInfo();
//        mABufferInfo = new MediaCodec.BufferInfo();
//
//        mVideoEncoder = createVideoEncoder(width, height, bitRate);
//
//        if (enableAudio && audioEncoderConfig != null) {
//            MediaFormat audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, audioEncoderConfig.getSampleRate(), audioEncoderConfig.getChannel());
//            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
//            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE);
//            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE);
//            mIsAudioEmpty = true;
//
//            mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
//            mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//            mAudioEncoder.start();
//        }
//
//        mStreamEnded = false;
//
//        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
//        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
//        // obtained from the encoder after it has started processing data.
//        //
//        // We're not actually interested in multiplexing audio.  We just want to convert m
//        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
//        mPath = outputFile.toString();
//        mMuxer = new MediaMuxer(mPath,
//                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//
//        mVTrackIndex = -1;
//        mATrackIndex = -1;
//        mMuxerStarted = false;
//
//        mEnableAudio = enableAudio;
//    }
//
//    private MediaCodec createVideoEncoder(int width, int height, int bitRate) {
//        float ratio = width / (float)height;
//        Size screenSize = Utils.getFullScreenSize(context);
//        int[] candidateWidths = new int[] { 1920, 1440, 1280, 720, 640, 320 };
//        for (int candidateWidth : candidateWidths) {
//            int candidateHeight = (int)(candidateWidth / ratio);
//            boolean isValidCandidate = candidateWidth < screenSize.getWidth() && candidateHeight < screenSize.getHeight();
//
//            if (isValidCandidate) {
//                Size size = new Size(Utils.ensureEvenSize(candidateWidth), Utils.ensureEvenSize(candidateHeight));
//                try {
//                    MediaFormat videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, size.getWidth(), size.getHeight());
//                    videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//                            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//                    videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
//                    videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
//                    videoFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, FRAME_RATE);
//                    videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
//                    videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE);
//
//                    // Create a MediaCodec encoder, and configure it with our videoFormat.  Get a Surface
//                    // we can use for input and wrap it with a class that handles the EGL work.
//                    MediaCodec videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
//                    videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//                    mInputSurface = videoEncoder.createInputSurface();
//                    videoEncoder.start();
//                    return videoEncoder;
//                } catch (Exception e) {
//                    // try next candidate width
//                }
//            }
//        }
//
//        throw new IllegalStateException("Partial recorder: can not prepare media recorder");
//    }
//
//    /**
//     * Returns the encoder's input surface.
//     */
//    public Surface getInputSurface() {
//        return mInputSurface;
//    }
//
//    /**
//     * Releases encoder resources.
//     */
//    public void release() {
//        if (mVideoEncoder != null) {
//            mVideoEncoder.stop();
//            mVideoEncoder.release();
//            mVideoEncoder = null;
//        }
//        if (mAudioEncoder != null) {
//            mAudioEncoder.stop();
//            mAudioEncoder.release();
//            mAudioEncoder = null;
//        }
//
//        if (mMuxer != null) {
//            try {
//                if (mIsAudioEmpty) {
//                    // avoid empty audio track. if the audio track is empty , muxer.stop will failed
//                    byte[] bytes = new byte[2];
//                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
//                    mABufferInfo.set(0, 2, System.nanoTime() / 1000, 0);
//                    buffer.position(mABufferInfo.offset);
//                    buffer.limit(mABufferInfo.offset + mABufferInfo.size);
//                    mMuxer.writeSampleData(mATrackIndex, buffer, mABufferInfo);
//                }
//                mMuxer.stop();
//                if (mCallback != null) {
//                    mMainHandler.post(() -> {
//                        File outFile = new File(mPath);
//                        List<File> result = new ArrayList<>();
//                        result.add(outFile);
//                        mCallback.onRecordSuccess(result, mCoverPath, System.currentTimeMillis() - mRecordStartedAt);
//                    });
//                }
//            } catch (final IllegalStateException e) {
//                Log.w(TAG, "Record failed with error:", e);
//                if (mCallback != null) {
//                    mMainHandler.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            mCallback.onRecordFailed(e, System.currentTimeMillis() - mRecordStartedAt);
//                        }
//                    });
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            try {
//                mMuxer.release();
//            } catch (IllegalStateException ex) {
//                Log.w(TAG, "Record failed with error:", ex);
//            }
//
//            mMuxer = null;
//        }
//
//    }
//
//    public String getCoverPath() {
//        return mCoverPath;
//    }
//
//    public void setCoverPath(String coverPath) {
//        mCoverPath = coverPath;
//    }
//
//    public RecordCallback getRecordCallback() {
//        return mCallback;
//    }
//
//    public void setRecordCallback(RecordCallback callback) {
//        mCallback = callback;
//    }
//
//    /**
//     * Extracts all pending data from the encoder and forwards it to the muxer.
//     * <p>
//     * If endOfStream is not set, this returns when there is no more data to drain.  If it
//     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
//     * Calling this with endOfStream set should be done once, right before stopping the muxer.
//     * <p>
//     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
//     * not recording audio.
//     */
//    public void drainEncoder(boolean endOfStream) {
//        if (endOfStream) {
//            mVideoEncoder.signalEndOfInputStream();
//            mStreamEnded = true;
//        }
//
//        try {
//            drainVideo(endOfStream);
//            drainAudio(endOfStream);
//        } catch (Exception e) {
//        }
//    }
//
//
//    private long videoPresentationTimeUsLast = 0L;
//
//    private void drainVideo(boolean endOfStream) {
//        try {
//            while (true) {
//                int encoderStatus = mVideoEncoder.dequeueOutputBuffer(mVBufferInfo, VIDEO_TIMEOUT_USEC);
//                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                    // no output available yet
//                    if (!endOfStream) {
//                        break;      // out of while
//                    } else {
//                        if (mStreamEnded) {
//                            break;
//                        }
//                    }
//                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                    // should happen before receiving buffers, and should only happen once
//                    if (mMuxerStarted) {
//                        throw new RuntimeException("format changed twice");
//                    }
//                    MediaFormat newFormat = mVideoEncoder.getOutputFormat();
//
//                    // now that we have the Magic Goodies, start the muxer
//                    mVTrackIndex = mMuxer.addTrack(newFormat);
//                    tryStartMuxer();
//                } else if (encoderStatus < 0) {
//                    // let's ignore it
//                } else {
//                    if (mMuxerStarted) {
//                        // same as mVideoEncoder.getOutputBuffer(encoderStatus)
//                        ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(encoderStatus);
//
//                        if (encodedData == null) {
//                            throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
//                                    " was null");
//                        }
//
//                        if ((mVBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                            // The codec config data was pulled out and fed to the muxer when we got
//                            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
//                            mVBufferInfo.size = 0;
//                        }
//
//                        if (mVBufferInfo.size != 0) {
//                            if (!mMuxerStarted) {
//                                throw new RuntimeException("muxer hasn't started");
//                            }
//
//                            // adjust the ByteBuffer values to match BufferInfo (not needed?)
//                            encodedData.position(mVBufferInfo.offset);
//                            encodedData.limit(mVBufferInfo.offset + mVBufferInfo.size);
//
//                            if (videoPresentationTimeUsLast != 0 && videoPresentationTimeUsLast > mVBufferInfo.presentationTimeUs) {
//                                mVBufferInfo.presentationTimeUs = videoPresentationTimeUsLast + 1;
//                            }
//                            videoPresentationTimeUsLast = mVBufferInfo.presentationTimeUs;
//
//                            mMuxer.writeSampleData(mVTrackIndex, encodedData, mVBufferInfo);
//                        }
//
//                        mVideoEncoder.releaseOutputBuffer(encoderStatus, false);
//
//                        if ((mVBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                            break;      // out of while
//                        }
//                    } else {
//                        // let's ignore it
//                        mVideoEncoder.releaseOutputBuffer(encoderStatus, false);
//                    }
//                }
//            }
//        } catch (Exception e) {
//            sendRecoverRecordingOnErrorBroadcast(context);
//        }
//    }
//
//    private long audioPresentationTimeUsLast = 0L;
//
//    public void drainAudio(boolean endOfStream) {
//        try {
//            if (mAudioEncoder != null) {
//                while (true) {
//                    // Start to get data from OutputBuffer and write to Muxer
//                    int index = mAudioEncoder.dequeueOutputBuffer(mABufferInfo, AUDIO_TIMEOUT_USEC);
//                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                        // no output available yet
//                        if (!endOfStream) {
//                            break;      // out of while
//                        } else {
//                            if (mStreamEnded) {
//                                break;
//                            }
//                        }
//                    }
//                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                        if (mATrackIndex != -1) {
//                            throw new RuntimeException("format changed twice");
//                        }
//                        mATrackIndex = mMuxer.addTrack(mAudioEncoder.getOutputFormat());
//                        tryStartMuxer();
//                    } else if (index >= 0) {
//                        if (mMuxerStarted) {
//                            if ((mABufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                                // ignore codec config
//                                mABufferInfo.size = 0;
//                            }
//
//                            if (mABufferInfo.size != 0) {
//                                ByteBuffer out = mAudioEncoder.getOutputBuffer(index);
//                                out.position(mABufferInfo.offset);
//                                out.limit(mABufferInfo.offset + mABufferInfo.size);
//
//                                if (audioPresentationTimeUsLast != 0 && audioPresentationTimeUsLast > mABufferInfo.presentationTimeUs) {
//                                    mABufferInfo.presentationTimeUs = audioPresentationTimeUsLast + 1;
//                                }
//                                audioPresentationTimeUsLast = mABufferInfo.presentationTimeUs;
//
//                                mMuxer.writeSampleData(mATrackIndex, out, mABufferInfo);
//                                mIsAudioEmpty = false;
//                            }
//
//                            mAudioEncoder.releaseOutputBuffer(index, false);
//
//                            if ((mABufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                                mStreamEnded = true; // Audio stream ended
//                                break;      // out of while
//                            }
//                        } else {
//                            // let's ignore it
//                            mAudioEncoder.releaseOutputBuffer(index, false); // Don't forget to release it
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            sendRecoverRecordingOnErrorBroadcast(context);
//        }
//    }
//
//    /**
//     * Enqueue the audio frame buffers to the encoder
//     *
//     * @param buffer      the data
//     * @param size        size of the data
//     * @param endOfStream is this frame the end
//     */
//    public void enqueueAudioFrame(ByteBuffer buffer, int size, long presentTimeUs, boolean endOfStream, Context context) {
//        if (mAudioEncoder != null) {
//
//            boolean done = false;
//            try {
//                while (!done) {
//                    // Start to put data to InputBuffer
//                    int index = mAudioEncoder.dequeueInputBuffer(AUDIO_TIMEOUT_USEC);
//                    if (index >= 0) { // In case we didn't get any input buffer, it may be blocked by all output buffers being
//                        // full, thus try to drain them below if we didn't get any
//                        ByteBuffer in = mAudioEncoder.getInputBuffer(index);
//                        in.clear();
//                        if (size < 0) {
//                            size = 0;
//                        }
//                        if (buffer == null) {
//                            buffer = ByteBuffer.allocate(0);
//                            size = 0;
//                        }
//                        in.position(0);
//                        in.limit(size);
//                        buffer.position(0);
//                        buffer.limit(size);
//
//                        in.put(buffer); // Here we should ensure that `size` is smaller than the capacity of the `in` buffer
//                        int flag = endOfStream ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
//                        mAudioEncoder.queueInputBuffer(index, 0, size, presentTimeUs, flag);
//                        done = true; // Done passing the input to the codec, but still check for available output below
//                    } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                        // if input buffers are full try to drain them
//                    }
//                }
//            } catch (Exception e) {
//            }
//        }
//    }
//
//    /**
//     * Enqueue the audio frame buffers to the encoder
//     *
//     * @param buffer      the data
//     * @param size        size of the data
//     * @param endOfStream is this frame the end
//     */
//    public void enqueueAudioFrame(ByteBuffer buffer, int size, boolean endOfStream, Context context) {
//        if (mAudioEncoder != null) {
//            enqueueAudioFrame(buffer, size, System.nanoTime() / 1000, endOfStream, context);
//        }
//    }
//
//    private void tryStartMuxer() {
//        if (mVTrackIndex != -1  // Video track is added
//                && (!mEnableAudio || mATrackIndex != -1) // and audio track is added
//                && !mMuxerStarted) { // and muxer not started
//            // then start the muxer
//            mMuxer.start();
//            mMuxerStarted = true;
//            mRecordStartedAt = System.currentTimeMillis();
//        }
//    }
//}