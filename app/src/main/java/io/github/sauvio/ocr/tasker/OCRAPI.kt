package io.github.sauvio.ocr.tasker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import io.github.sauvio.ocr.tasker.Analyzer.OnAnalyzeListener
import java.io.IOException


interface RecognizerFactory {

    fun createClient(): TextRecognizerOptionsInterface

}

interface Analyzer<T> {
    /**
     * Analyzes an image to produce a result.
     *
     * @param bitmap The image bitmap to analyze
     */
    fun analyze(bitmap: Bitmap?, rotationDegress: Int?, listener: OnAnalyzeListener<AnalyzeResult<T?>>)
    fun analyze(uri: Uri?, listener: OnAnalyzeListener<AnalyzeResult<T?>>)
    interface OnAnalyzeListener<T> {
        fun onSuccess(result: T)
        fun onFailure()
    }
}

data class AnalyzeResult<T>(
    var bitmap: Bitmap? = null,
    var text: T? = null
)

class ChineseRecognizer : RecognizerFactory {

    override fun createClient(): TextRecognizerOptionsInterface {
        return ChineseTextRecognizerOptions.Builder().build()
    }

}

class DefaultRecognizer(private val options: TextRecognizerOptionsInterface) : RecognizerFactory {

    override fun createClient(): TextRecognizerOptionsInterface {
        return options
    }

}

class TextRecognitionAnalyzer constructor(
    private val context: Context,
    options: RecognizerFactory
) : Analyzer<Text?> {

    private var mTextRecognizer: TextRecognizer? = null

    init {
        mTextRecognizer = TextRecognition.getClient(options.createClient())
    }

    override fun analyze(bitmap: Bitmap?, rotationDegrees: Int?, listener: OnAnalyzeListener<AnalyzeResult<Text?>>) {
        analyzeBitmap(bitmap, rotationDegrees, listener)
    }

    override fun analyze(uri: Uri?, listener: OnAnalyzeListener<AnalyzeResult<Text?>>) {
        try {
//            analyzeBitmap(BitmapUtils.getBitmapFromContentUri(context, uri!!), listener)
            if(uri == null){
                return
            }
            val inputImage = InputImage.fromFilePath(context, uri)
            mTextRecognizer!!.process(inputImage)
                .addOnSuccessListener{ result ->
                    listener.onSuccess(AnalyzeResult(null, result))
                }
                .addOnFailureListener{ listener.onFailure() }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun analyzeBitmap(
        bitmap: Bitmap?,
        rotationDegrees: Int?,
        listener: OnAnalyzeListener<AnalyzeResult<Text?>>
    ) {
        try {
            if (bitmap == null)
                return
            val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees!!)
            mTextRecognizer!!.process(inputImage)
                .addOnSuccessListener { result ->
                    listener.onSuccess(AnalyzeResult(bitmap, result))
                }
                .addOnFailureListener { listener.onFailure() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}