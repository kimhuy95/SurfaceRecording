package com.example.surfacerecordingdemo.gles;

/**
 * This class is used to cut the top and bottom area of the screen and
 * just keep the center main part.
 *
 * In this demo, we'll cut the status bar and navigation bar of the screen
 */
public class FullFrameRect {
    private final CroppedDrawable2d mRectDrawable;
    private Texture2dProgram mProgram;

    /**
     * Prepares the object.
     *
     * @param program The program to use.  FullFrameRect takes ownership, and will release
     *     the program when no longer needed.
     */
    public FullFrameRect(Texture2dProgram program) {
        mRectDrawable = new CroppedDrawable2d(Drawable2d.Prefab.FULL_RECTANGLE);
        mProgram = program;
    }

    /**
     * Releases resources.
     * <p>
     * This must be called with the appropriate EGL context current (i.e. the one that was
     * current when the constructor was called).  If we're about to destroy the EGL context,
     * there's no value in having the caller make it current just to do this cleanup, so you
     * can pass a flag that will tell this function to skip any EGL-context-specific cleanup.
     */
    public void release(boolean doEglCleanup) {
        if (mProgram != null) {
            if (doEglCleanup) {
                mProgram.release();
            }
            mProgram = null;
        }
    }

    /**
     * Returns the program currently in use.
     */
    public Texture2dProgram getProgram() {
        return mProgram;
    }

    /**
     * Changes the program.  The previous program will be released.
     * <p>
     * The appropriate EGL context must be current.
     */
    public void changeProgram(Texture2dProgram program) {
        mProgram.release();
        mProgram = program;
    }

    /**
     * @param bottomCropped defines the bottom area to be cut. from 0f-1f.
     */
    public void setBottomCropped(float bottomCropped) {
        mRectDrawable.setBottomCropped(bottomCropped);
    }

    /**
     * @param topCropped defines the top area to be cut. from 0f-1f.
     */
    public void setTopCropped(float topCropped) {
        mRectDrawable.setTopCropped(topCropped);
    }

    /**
     * @param leftCropped defines the left area to be cut. from 0f-1f.
     */
    public void setLeftCropped(float leftCropped) {
        mRectDrawable.setLeftCropped(leftCropped);
    }

    /**
     * @param rightCropped defines the right area to be cut. from 0f-1f.
     */
    public void setRightCropped(float rightCropped) {
        mRectDrawable.setRightCropped(rightCropped);
    }

    /**
     * Creates a texture object suitable for use with drawFrame().
     */
    public int createTextureObject() {
        return mProgram.createTextureObject();
    }

    /**
     * Draws a viewport-filling rect, texturing it with the specified texture object.
     */
    public void drawFrame(int textureId, float[] texMatrix) {
        // Use the identity matrix for MVP so our 2x2 FULL_RECTANGLE covers the viewport.
        mProgram.draw(GlUtil.IDENTITY_MATRIX, mRectDrawable.getVertexArray(), 0,
                mRectDrawable.getVertexCount(), mRectDrawable.getCoordsPerVertex(),
                mRectDrawable.getVertexStride(),
                texMatrix, mRectDrawable.getTexCoordArray(), textureId,
                mRectDrawable.getTexCoordStride());
    }
}
