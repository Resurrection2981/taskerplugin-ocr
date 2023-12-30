package io.github.sauvio.ocr.tasker

/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.firebase.components.Preconditions
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.min

class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val lock = Any()
    private val graphics: MutableList<Graphic> = ArrayList()
    private val transformationMatrix: Matrix = Matrix()
    private var onDrawListener: OnDrawListener? = null

    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var scaleFactor: Float = 1.0f
    private var postScaleWidthOffset: Float = 0f
    private var postScaleHeightOffset: Float = 0f
    private var isImageFlipped: Boolean = false
    private var needUpdateTransformation: Boolean = true

    abstract class Graphic(val overlay: GraphicOverlay) {
        abstract fun draw(canvas: Canvas)
        protected fun drawRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            canvas.drawRect(left, top, right, bottom, paint)
        }

        protected fun drawText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
            canvas.drawText(text, x, y, paint)
        }

        fun scale(imagePixel: Float): Float {
            return imagePixel * overlay.scaleFactor
        }

        fun getApplicationContext(): Context {
            return overlay.context.applicationContext
        }

        fun isImageFlipped(): Boolean {
            return overlay.isImageFlipped
        }

        fun translateX(x: Float): Float {
            return if (overlay.isImageFlipped) {
                overlay.width.toFloat() - (scale(x) - overlay.postScaleWidthOffset)
            } else {
                scale(x) - overlay.postScaleWidthOffset
            }
        }

        fun translateY(y: Float): Float {
            return scale(y) - overlay.postScaleHeightOffset
        }

        fun getTransformationMatrix(): Matrix {
            return overlay.transformationMatrix
        }

        fun postInvalidate() {
            overlay.postInvalidate()
        }

        fun updatePaintColorByZValue(
            paint: Paint,
            canvas: Canvas,
            visualizeZ: Boolean,
            rescaleZForVisualization: Boolean,
            zInImagePixel: Float,
            zMin: Float,
            zMax: Float
        ) {
            if (!visualizeZ) {
                return
            }

            val zLowerBoundInScreenPixel: Float
            val zUpperBoundInScreenPixel: Float

            if (rescaleZForVisualization) {
                zLowerBoundInScreenPixel = min(-0.001f, scale(zMin))
                zUpperBoundInScreenPixel = max(0.001f, scale(zMax))
            } else {
                val defaultRangeFactor = 1f
                zLowerBoundInScreenPixel = -defaultRangeFactor * canvas.width.toFloat()
                zUpperBoundInScreenPixel = defaultRangeFactor * canvas.width.toFloat()
            }

            val zInScreenPixel = scale(zInImagePixel)

            if (zInScreenPixel < 0) {
                val v = (zInScreenPixel / zLowerBoundInScreenPixel * 255).toInt()
                paint.setARGB(255, 255, 255 - v, 255 - v)
            } else {
                val v = (zInScreenPixel / zUpperBoundInScreenPixel * 255).toInt()
                paint.setARGB(255, 255 - v, 255 - v, 255)
            }
        }
    }

    init {
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            needUpdateTransformation = true
        }
    }

    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    fun add(graphic: Graphic) {
        synchronized(lock) {
            graphics.add(graphic)
        }
    }

    fun remove(graphic: Graphic) {
        synchronized(lock) {
            graphics.remove(graphic)
        }
        postInvalidate()
    }
    fun setImageSourceInfo(imageWidth: Int, imageHeight: Int, isFlipped: Boolean) {
        Preconditions.checkState(imageWidth > 0, "image width must be positive")
        Preconditions.checkState(imageHeight > 0, "image height must be positive")
        synchronized(lock) {
            this.imageWidth = imageWidth
            this.imageHeight = imageHeight
            this.isImageFlipped = isFlipped
            needUpdateTransformation = false
        }
        postInvalidate()
    }

    fun getImageWidth(): Int {
        return imageWidth
    }

    fun getImageHeight(): Int {
        return imageHeight
    }

    private fun updateTransformationIfNeeded() {
        if (!needUpdateTransformation || imageWidth <= 0 || imageHeight <= 0) {
            return
        }
        val viewAspectRatio = width.toFloat() / height
        val imageAspectRatio = imageWidth.toFloat() / imageHeight
        postScaleWidthOffset = 0f
        postScaleHeightOffset = 0f
        if (viewAspectRatio > imageAspectRatio) {
            scaleFactor = width.toFloat() / imageWidth
            postScaleHeightOffset =
                ((width.toFloat() / imageAspectRatio - height) / 2)
        } else {
            scaleFactor = height.toFloat() / imageHeight
            postScaleWidthOffset =
                ((height.toFloat() * imageAspectRatio - width) / 2)
        }

        transformationMatrix.reset()
        transformationMatrix.setScale(scaleFactor, scaleFactor)
        transformationMatrix.postTranslate(-postScaleWidthOffset, -postScaleHeightOffset)

        if (isImageFlipped) {
            transformationMatrix.postScale(-1f, 1f, width / 2f, height / 2f)
        }

        needUpdateTransformation = false
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val postBitmap = onDrawListener?.postBitmap()
        var mutableCanvasBitmap: Bitmap? = null
        var bitmapCanvas: Canvas? = null
        if (postBitmap != null) {
            mutableCanvasBitmap = postBitmap.copy(Bitmap.Config.ARGB_8888, true)
            bitmapCanvas = Canvas(mutableCanvasBitmap)
        }
        synchronized(lock) {
            updateTransformationIfNeeded()
            for (graphic in graphics) {
                graphic.draw(canvas)
                bitmapCanvas?.let { graphic.draw(it) }
            }
        }

        onDrawListener?.onDrawCompleted(mutableCanvasBitmap!!)
    }

    fun setOnDrawListener(listener: OnDrawListener) {
        this.onDrawListener = listener
    }

    /** Listen for drawing completion and pass the bitmap object. */
    interface OnDrawListener {
        fun onDrawCompleted(bitmap: Bitmap)
        fun postBitmap(): Bitmap
    }

}