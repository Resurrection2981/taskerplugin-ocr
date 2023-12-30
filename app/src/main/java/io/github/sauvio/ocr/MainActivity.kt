package io.github.sauvio.ocr

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.mlkit.vision.text.Text
import io.github.sauvio.ocr.tasker.AnalyzeResult
import io.github.sauvio.ocr.tasker.Analyzer
import io.github.sauvio.ocr.tasker.ChineseRecognizer
import io.github.sauvio.ocr.tasker.CustomImageView
import io.github.sauvio.ocr.tasker.GraphicOverlay
import io.github.sauvio.ocr.tasker.TextGraphic
import io.github.sauvio.ocr.tasker.TextRecognitionAnalyzer
import io.github.sauvio.ocr.utils.BitmapUtils
import io.github.sauvio.ocr.utils.Constants
import io.github.sauvio.ocr.utils.SpUtil
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private var mOnAnalyzeListener: Analyzer.OnAnalyzeListener<AnalyzeResult<Text?>>? = null
    private var mOnDrawListener: GraphicOverlay.OnDrawListener? = null
    private var preview: CustomImageView? = null
    private var graphicOverlay: GraphicOverlay? = null
    private val shouldGroupRecognizedTextInBlocks: Boolean = false
    private val showLanguageTag: Boolean = true
    private val showConfidence: Boolean = true
    private var imageUri: Uri? = null
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                imageUri = result.data?.data
                tryReloadAndRecognizeInImage()
            }
        }
    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {}
                else -> super.onManagerConnected(status)
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        val params = window.attributes
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = params
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        hideSystemUI(controller)

        preview = findViewById(R.id.preview)
        graphicOverlay = findViewById(R.id.graphic_overlay)

        val pickImageButton: Button = findViewById(R.id.pick_image_button)
        pickImageButton.setOnClickListener {
            openGalleryForImage()
        }
    }

    override fun onResume() {
        super.onResume()
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            Log.d(TAG, "Internal OpenCV library not found. Report to the developer");
        }
    }

    private fun hideSystemUI(controller: WindowInsetsControllerCompat) {
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.hide(WindowInsetsCompat.Type.statusBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun openGalleryForImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun tryReloadAndRecognizeInImage() {
        // Check if there is a previously selected image
        if (imageUri != null) {
            // Clear previous overlays
            preview?.setImageURI(null)
            graphicOverlay?.clear()

            // Reload and process the selected image
            val bitmap = BitmapUtils.getBitmapFromContentUri(applicationContext, imageUri!!)
            val processedBitmap = BitmapUtils.preProcessBitmap(applicationContext, bitmap)
            preview?.setImageBitmap(processedBitmap)
            preview?.rotation = 0f

            // Start the analysis process
            mOnAnalyzeListener = object : Analyzer.OnAnalyzeListener<AnalyzeResult<Text?>> {
                override fun onSuccess(result: AnalyzeResult<Text?>) {
                    // Set up the draw listener to handle post-draw actions
                    mOnDrawListener = object : GraphicOverlay.OnDrawListener {
                        override fun onDrawCompleted(bitmap: Bitmap) {
                            // Save to local storage after drawing is complete, if needed
                            if (SpUtil.getInstance().getBoolean(Constants.KEY_PERSIST_DATA, true)) {
                                BitmapUtils.saveBitmapToStorage(applicationContext, bitmap)
                            }
                            bitmap.recycle()
                        }

                        override fun postBitmap(): Bitmap {
                            return bitmap
                        }
                    }

                    // Process and display the recognition result
                    val text = result.text
                    if (text != null) {
                        graphicOverlay?.add(
                            TextGraphic(
                                graphicOverlay!!,
                                text,
                                shouldGroupRecognizedTextInBlocks,
                                showLanguageTag,
                                showConfidence
                            )
                        )
                        graphicOverlay?.setImageSourceInfo(bitmap.width, bitmap.height, false)
                        graphicOverlay?.setOnDrawListener(mOnDrawListener as GraphicOverlay.OnDrawListener)
                    }
                }

                override fun onFailure() {
                    // Handle analysis failure if needed
                }
            }

            // Start the text recognition analysis
            TextRecognitionAnalyzer(applicationContext, ChineseRecognizer()).analyze(
                processedBitmap, 0, mOnAnalyzeListener!!
            )
        } else {
            // Handle the case where no image has been selected
            // You may want to display a message or take appropriate action
        }
    }

}