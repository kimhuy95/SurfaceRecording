package com.example.surfacerecordingdemo.recording

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Size
import com.example.surfacerecordingdemo.R
import com.example.surfacerecordingdemo.recording.gles.Drawable2d
import com.example.surfacerecordingdemo.recording.gles.GlUtil
import com.example.surfacerecordingdemo.recording.gles.Texture2dProgram

data class Texture(val id: Int, val size: Size)

class WatermarkFrameRect(
    private val context: Context,
    private val program: Texture2dProgram,
    viewport: Size
) {
    private val mRectDrawable = Drawable2d(Drawable2d.Prefab.FULL_RECTANGLE)
    private val texture = loadTexture()

    private var mvpMatrix = GlUtil.initMvpMatrix(0, viewport.width / viewport.height.toFloat())

    init {
        val gap = 0f
        val textureWidth = texture.size.width
        val textureHeight = texture.size.height
        val size = PointF(textureWidth.toFloat() / viewport.width, textureHeight.toFloat() / viewport.height)
        val position = PointF((textureWidth / 2f + gap) / viewport.width, (viewport.height - (textureHeight / 2f + gap)) / viewport.height)
        mvpMatrix = GlUtil.createFilterMvpMatrix(mvpMatrix, size, position, 0f)
    }

    fun release(doEglCleanup: Boolean) {
        if (doEglCleanup) {
            program.release()
        }
    }

    fun drawFrame(id: Int, texMatrix: FloatArray?) {
        program.draw(
            mvpMatrix,
            mRectDrawable.vertexArray,
            0,
            mRectDrawable.vertexCount,
            mRectDrawable.coordsPerVertex,
            mRectDrawable.vertexStride,
            texMatrix,
            mRectDrawable.texCoordArray,
            texture.id,
            mRectDrawable.texCoordStride
        )
    }

    private fun loadWatermarkBitmap(): Bitmap {
        val options = BitmapFactory.Options().apply { inScaled = false } // No pre-scaling
        return BitmapFactory.decodeResource(context.resources, R.drawable.ic_watermark, options)
    }

    private fun loadTexture(): Texture {
        val textureHandle = IntArray(1)

        GLES20.glGenTextures(1, textureHandle, 0)

        val options = BitmapFactory.Options()
        options.inScaled = false

        // Read in the resource
        val bitmap = loadWatermarkBitmap()
        val size = Size(bitmap.width, bitmap.height)

        // Bind to the texture in OpenGL
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])

        // Set filtering
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        // Load the bitmap into the bound texture.
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        // Recycle the bitmap, since its data has been loaded into OpenGL.
        bitmap.recycle()

        return Texture(id = textureHandle[0], size = size)
    }
}