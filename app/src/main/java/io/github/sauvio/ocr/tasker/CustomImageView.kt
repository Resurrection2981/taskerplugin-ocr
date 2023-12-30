package io.github.sauvio.ocr.tasker

import android.content.Context
import android.graphics.Matrix
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class CustomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (Build.VERSION.SDK_INT < 18) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val result = super.setFrame(l, t, r, b)
        applyMatrix()
        return result
    }

    private fun applyMatrix() {
        val matrix = Matrix()
        matrix.setScale(1f, 1f)
        matrix.postTranslate(0f, 0f)
        imageMatrix = matrix
    }
}