package io.github.sauvio.ocr.tasker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.firebase.components.Preconditions
import kotlin.math.max
import kotlin.math.min

class ImageDrawer(context: Context): HardwareInfomationAware {
    private val lock = Any()
    private var mContext: Context? = context
    private val drawers: MutableList<Drawer> = ArrayList()
    private val transformationMatrix: Matrix = Matrix()
    private var onDrawListener: OnDrawListener? = null

    private var deviceWidth: Int = 0
    private var deviceHeight: Int = 0
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var scaleFactor: Float = 1.0f
    private var postScaleWidthOffset: Float = 0f
    private var postScaleHeightOffset: Float = 0f
    private var isImageFlipped: Boolean = false
    private var needUpdateTransformation: Boolean = true


    abstract class Drawer(val drawer: ImageDrawer) {
        abstract fun draw(canvas: Canvas)
        protected fun drawRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            canvas.drawRect(left, top, right, bottom, paint)
        }

        protected fun drawText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
            canvas.drawText(text, x, y, paint)
        }

        fun scale(imagePixel: Float): Float {
            return imagePixel * drawer.scaleFactor
        }

        fun getApplicationContext(): Context {
            return drawer.mContext!!.applicationContext
        }

        fun isImageFlipped(): Boolean {
            return drawer.isImageFlipped
        }

        fun translateX(x: Float): Float {
            return if (drawer.isImageFlipped) {
                drawer.deviceWidth.toFloat() - (scale(x) - drawer.postScaleWidthOffset)
            } else {
                scale(x) - drawer.postScaleWidthOffset
            }
        }

        fun translateY(y: Float): Float {
            return scale(y) - drawer.postScaleHeightOffset
        }

        fun getTransformationMatrix(): Matrix {
            return drawer.transformationMatrix
        }

        fun postInvalidate() {
            drawer.postInvalidate()
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
    fun clear() {
        synchronized(lock) {
            drawers.clear()
        }
        postInvalidate()
    }

    fun add(drawer: Drawer) {
        synchronized(lock) {
            drawers.add(drawer)
        }
    }

    fun remove(drawer: Drawer) {
        synchronized(lock) {
            drawers.remove(drawer)
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

    override fun awareResolution(pair: Pair<Int, Int>) {
        deviceWidth = pair.first
        deviceHeight = pair.second
    }

    private fun updateTransformationIfNeeded() {
        if (!needUpdateTransformation || imageWidth <= 0 || imageHeight <= 0) {
            return
        }
        val viewAspectRatio = deviceWidth.toFloat() / deviceHeight
        val imageAspectRatio = imageWidth.toFloat() / imageHeight
        postScaleWidthOffset = 0f
        postScaleHeightOffset = 0f
        if (viewAspectRatio > imageAspectRatio) {
            scaleFactor = deviceWidth.toFloat() / imageWidth
            postScaleHeightOffset =
                ((deviceWidth.toFloat() / imageAspectRatio - deviceHeight) / 2)
        } else {
            scaleFactor = deviceHeight.toFloat() / imageHeight
            postScaleWidthOffset =
                ((deviceHeight.toFloat() * imageAspectRatio - deviceWidth) / 2)
        }

        transformationMatrix.reset()
        transformationMatrix.setScale(scaleFactor, scaleFactor)
        transformationMatrix.postTranslate(-postScaleWidthOffset, -postScaleHeightOffset)

        if (isImageFlipped) {
            transformationMatrix.postScale(-1f, 1f, deviceWidth / 2f, deviceHeight / 2f)
        }

        needUpdateTransformation = false
    }

    fun onDraw() {
        val postBitmap = onDrawListener?.postBitmap()
        var mutableCanvasBitmap: Bitmap? = null
        var bitmapCanvas: Canvas? = null
        if (postBitmap != null) {
            mutableCanvasBitmap = postBitmap.copy(Bitmap.Config.ARGB_8888, true)
            bitmapCanvas = Canvas(mutableCanvasBitmap)
        }
        synchronized(lock) {
            updateTransformationIfNeeded()
            for (drawer in drawers) {
                bitmapCanvas?.let { drawer.draw(it) }
            }
        }

        onDrawListener?.onDrawCompleted(mutableCanvasBitmap!!)
    }

    fun postInvalidate(){
        onDraw()
    }

    fun setOnDrawListener(listener: OnDrawListener) {
        this.onDrawListener = listener
        postInvalidate()
    }

    /** Listen for drawing completion and pass the bitmap object. */
    interface OnDrawListener {
        fun onDrawCompleted(bitmap: Bitmap)
        fun postBitmap(): Bitmap
    }

}

interface HardwareInfomationAware {
    fun awareResolution(pair:Pair<Int, Int>)
}
