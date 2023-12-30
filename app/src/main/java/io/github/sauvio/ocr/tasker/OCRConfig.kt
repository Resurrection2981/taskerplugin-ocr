package io.github.sauvio.ocr.tasker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.mlkit.vision.text.Text
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import io.github.sauvio.ocr.R
import io.github.sauvio.ocr.SettingsActivity
import io.github.sauvio.ocr.databinding.ActivityConfigOcrBinding
import io.github.sauvio.ocr.tasker.Analyzer.OnAnalyzeListener
import io.github.sauvio.ocr.utils.BitmapUtils
import io.github.sauvio.ocr.utils.Constants
import io.github.sauvio.ocr.utils.ImageFileProvider
import io.github.sauvio.ocr.utils.SpUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File


class BackgroundWork: CoroutineScope by MainScope() {
    private var imageDrawer: ImageDrawer? = null
    private var mOnAnalyzeListener: OnAnalyzeListener<AnalyzeResult<Text?>>? = null
    private var mOnDrawListener: ImageDrawer.OnDrawListener? = null
    private val shouldGroupRecognizedTextInBlocks: Boolean = false
    private val showLanguageTag: Boolean = true
    private val showConfidence: Boolean = true

    fun readText(context: Context, imagePathName: String): Deferred<OCROutput> {
        imageDrawer = ImageDrawer(context)
        imageDrawer?.clear()
        // start OCR
        // Use the withContext function to switch context. The withContext function
        // can switch to the specified scheduler inside the coroutine.
        return readTextFromOCRAPI(context, imagePathName)

    }
    private fun readTextFromOCRAPI(context: Context, pathName: String): Deferred<OCROutput> {
        val deferred = CompletableDeferred<OCROutput>()
        var ocrResult = OCROutput("","", "")
        val imageFile = File(pathName)
        val imageUri = FileProvider.getUriForFile(context, ImageFileProvider.authority(context), imageFile)
        val bitmap = BitmapUtils.getBitmapFromContentUri(context, imageUri!!)
        val processedBitmap = BitmapUtils.preProcessBitmap(context, bitmap)
        mOnAnalyzeListener = object : OnAnalyzeListener<AnalyzeResult<Text?>> {
            override fun onSuccess(result: AnalyzeResult<Text?>) {

                val text = result.text
                if ( text != null) {
                    /**
                    val textOnImage: StringBuilder = StringBuilder()
                    for (textBlock in text.textBlocks){
                        textOnImage.append(textBlock.text).append(",")
                    }
                    if (textOnImage.length > 1) {
                        textOnImage.delete(textOnImage.length - 1, textOnImage.length)
                    }
                    */

                    val coordinatesData = assembleCoordinatesData(result)
                    ocrResult = OCROutput(text.text, coordinatesData, convertTextToJson(text))
                    drawTextAndSaveImage(context, imageUri, text)
                    deferred.complete(ocrResult)
                }
            }

            override fun onFailure() {
                deferred.complete(ocrResult)
            }
        }

        launch {
            withContext(IO) {
                TextRecognitionAnalyzer(context, ChineseRecognizer()).analyze(
                    processedBitmap,
                    0,
                    mOnAnalyzeListener!!
                )
            }
        }
        return deferred
    }

    private fun drawTextAndSaveImage(context: Context, imageUri: Uri, text: Text){
        // Reload and process the selected image
        val bitmap = BitmapUtils.getBitmapFromContentUri(context, imageUri)
        // Set up the draw listener to handle post-draw actions
        mOnDrawListener = object : ImageDrawer.OnDrawListener {
            override fun onDrawCompleted(bitmap: Bitmap) {
                // Save to local storage after drawing is complete, if needed
                if (SpUtil.getInstance().getBoolean(Constants.KEY_PERSIST_DATA, true)) {
                    BitmapUtils.saveBitmapToStorage(context, bitmap)
                }
                bitmap.recycle()
            }

            override fun postBitmap(): Bitmap {
                return bitmap
            }
        }

        // Process the recognition result
        imageDrawer?.add(
            InMemoryImageDrawer(
                imageDrawer!!,
                context,
                text,
                shouldGroupRecognizedTextInBlocks,
                showLanguageTag,
                showConfidence
            )
        )
        imageDrawer?.setImageSourceInfo(bitmap.width, bitmap.height, false)
        imageDrawer?.setOnDrawListener(mOnDrawListener as ImageDrawer.OnDrawListener)
    }

    /**
     * Assemble the key-value information for the coordinates of each textBlock.
     * @return JSON string. eg:[{"1":[151,33,154,72]},{"0":[151,33,171,67]},{"g":[176,44,196,72]}]
     */
    private fun assembleCoordinatesData(result: AnalyzeResult<Text?>): String {
        val text = result.text ?: return ""
        val jsonTextBlocks = JSONArray()

        for(textBlock in text.textBlocks) {
            val jsonTextBlock = JSONObject()
            jsonTextBlock.put(textBlock.text, JSONArray(rectToIntArray(textBlock.boundingBox)))
            jsonTextBlocks.put(jsonTextBlock)
        }
        return jsonTextBlocks.toString()
    }



    /**
     * Converts a Text object, representing recognized text in an image, into a JSON string.
     *
     * @see com.google.mlkit.vision.text.Text
     * @param text The Text object to be converted to JSON.
     * @return A JSON representation of the provided Text object. If the Text object is null or an exception
     *         occurs during the conversion, an empty string ("") is returned.
     */
    fun convertTextToJson(text: Text): String {
        return try {
            val jsonText = JSONObject()
            jsonText.put("text", text.text)
            val jsonTextBlocks = JSONArray()

            for (textBlock in text.textBlocks) {
                val jsonTextBlock = JSONObject()
                jsonTextBlock.put("text", textBlock.text)
                jsonTextBlock.put("boundingBox", JSONArray(rectToIntArray(textBlock.boundingBox)))
                jsonTextBlock.put("cornerPoints", JSONArray(cornerPointsToIntArray(textBlock.cornerPoints)))
                jsonTextBlock.put("recognizedLanguage", textBlock.recognizedLanguage)

                val jsonLines = JSONArray()
                for (line in textBlock.lines) {
                    val jsonLine = JSONObject()
                    jsonLine.put("recognizedLanguage", line.recognizedLanguage)
                    jsonLine.put("text", line.text)
                    jsonLine.put("boundingBox", JSONArray(rectToIntArray(line.boundingBox).contentToString()))
                    jsonLine.put("cornerPoints", JSONArray(cornerPointsToIntArray(line.cornerPoints).contentToString()))
                    jsonLine.put("confidence", line.confidence)
                    jsonLine.put("angle", line.angle)

                    val jsonElements = JSONArray()
                    for (element in line.elements) {
                        val jsonElement = JSONObject()
                        jsonElement.put("recognizedLanguage", element.recognizedLanguage)
                        jsonElement.put("text", element.text)
                        jsonElement.put("boundingBox", JSONArray(rectToIntArray(element.boundingBox).contentToString()))
                        jsonElement.put("cornerPoints", JSONArray(cornerPointsToIntArray(line.cornerPoints).contentToString()))
                        jsonElement.put("confidence", element.confidence)
                        jsonElement.put("angle", element.angle)

                        val jsonSymbols = JSONArray()
                        for (symbol in element.symbols) {
                            val jsonSymbol = JSONObject()
                            jsonSymbol.put("recognizedLanguage", symbol.recognizedLanguage)
                            jsonSymbol.put("text", symbol.text)
                            jsonSymbol.put("boundingBox", JSONArray(rectToIntArray(symbol.boundingBox).contentToString()))
                            jsonSymbol.put("cornerPoints", JSONArray(cornerPointsToIntArray(symbol.cornerPoints).contentToString()))
                            jsonSymbol.put("confidence", symbol.confidence)
                            jsonSymbol.put("angle", symbol.angle)
                            jsonSymbols.put(jsonSymbol)
                        }

                        jsonElement.put("symbols", jsonSymbols)
                        jsonElements.put(jsonElement)
                    }

                    jsonLine.put("elements", jsonElements)
                    jsonLines.put(jsonLine)
                }

                jsonTextBlock.put("lines", jsonLines)
                jsonTextBlocks.put(jsonTextBlock)
            }

            jsonText.put("textBlocks", jsonTextBlocks)
            jsonText.toString()
        } catch (e: JSONException) {
            e.printStackTrace()
            ""
        }
    }

    private fun rectToIntArray(rect: Rect?): IntArray {
        return rect?.let {
            intArrayOf(it.left, it.top, it.right, it.bottom)
        } ?: intArrayOf()
    }

    private fun cornerPointsToIntArray(cornerPoints: Array<Point>?): IntArray {
        return cornerPoints?.flatMap { listOf(it.x, it.y) }?.toIntArray() ?: intArrayOf()
    }
}

class BackgroundHelper(config: TaskerPluginConfig<OCRInput>): TaskerPluginConfigHelper<OCRInput, OCROutput, OCRRunner>(config){
    override val inputClass get() = OCRInput::class.java
    override val outputClass get() = OCROutput::class.java
    override val runnerClass = OCRRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<OCRInput>, blurbBuilder: StringBuilder) {
        if(!isInputValid(input).success)
            blurbBuilder.append("\nConfigure some variables to perform actions ")
    }

    override fun isInputValid(input: TaskerInput<OCRInput>) = input.regular.invalid

}

class ActivityConfigOCR :  ActivityConfigTasker<OCRInput, OCROutput, OCRRunner, BackgroundHelper, ActivityConfigOcrBinding>() {
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private val taskHelper by lazy { BackgroundHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding?.editImagePathName?.setOnClickListener { showVariableDialog() }
        val settingsEntranceTextView: TextView = findViewById(R.id.settings_entrance)
        settingsEntranceTextView.setOnClickListener {
            openSettingsActivity()
        }
        activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                finishForTasker()
            }
        }
    }

    private fun showVariableDialog() {
        val relevantVariables = taskerHelper.relevantVariables.toList()
        if (relevantVariables.isEmpty()) return "No variables to select.\n\nCreate some local variables in Tasker to show here.".toToast(this)
        selectOne("Select a Tasker variable", relevantVariables) { binding?.editImagePathName?.setText(it) }
    }
    private fun finishForTasker() {
        taskHelper.finishForTasker()
    }

    override fun getNewHelper(config: TaskerPluginConfig<OCRInput>) = BackgroundHelper(config)

    override fun inflateBinding(layoutInflater: LayoutInflater) = ActivityConfigOcrBinding.inflate(layoutInflater)

    override val inputForTasker get() = TaskerInput(OCRInput(binding?.editImagePathName?.text?.toString(),binding?.editResultVariableName?.text?.toString()))

    override fun assignFromInput(input: TaskerInput<OCRInput>) {
        input.regular.run {
            binding?.editImagePathName?.setText(imagePathName)
            binding?.editResultVariableName?.setText(resultVariableName)
        }
    }

    private fun openSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }


}