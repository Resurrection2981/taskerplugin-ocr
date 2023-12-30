package io.github.sauvio.ocr.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.googlecode.leptonica.android.AdaptiveMap
import com.googlecode.leptonica.android.Binarize
import com.googlecode.leptonica.android.Convert
import com.googlecode.leptonica.android.Enhance
import com.googlecode.leptonica.android.ReadFile
import com.googlecode.leptonica.android.Rotate
import com.googlecode.leptonica.android.Skew
import com.googlecode.leptonica.android.WriteFile
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay


class BitmapUtils {
    companion object {
        val EMPTY_RECT: Rect = Rect()
        var mMaxTextureSize: Int? = 0

        @Throws(FileNotFoundException::class)
        fun writeBitmapToUri(
            context: Context,
            bitmap: Bitmap,
            uri: Uri?,
            compressFormat: CompressFormat?,
            compressQuality: Int
        ) {
            var outputStream: OutputStream? = null
            try {
                outputStream = context.contentResolver.openOutputStream(uri!!)
                bitmap.compress(compressFormat!!, compressQuality, outputStream!!)
            } finally {
                closeSafe(outputStream)
            }
        }

        fun getBitmapFromContentUri(context: Context, uri: Uri): Bitmap {
            val decodedBitmap = decodeSampledBitmap(context, uri)
//            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            val orientation = getExifOrientationTag(context, uri)

            var rotationDegrees = 0
            var flipX = false
            var flipY = false

            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                    flipX = true
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    rotationDegrees = 90
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    rotationDegrees = 90
                    flipX = true
                }
                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    rotationDegrees = 180
                }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    flipY = true
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    rotationDegrees = -90
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    rotationDegrees = -90
                    flipX = true
                }
                ExifInterface.ORIENTATION_UNDEFINED,
                ExifInterface.ORIENTATION_NORMAL -> {
                    // No transformations necessary in this case.
                }
            }

            return rotateBitmap(decodedBitmap.bitmap!!, rotationDegrees, flipX, flipY)
//            return rotateBitmap(bitmap!!, rotationDegrees, flipX, flipY)

        }

        fun getExifOrientationTag(context: Context, imageUri: Uri): Int {
            // https://android-developers.googleblog.com/2016/12/introducing-the-exifinterface-support-library.html
            if (imageUri.scheme != ContentResolver.SCHEME_CONTENT && imageUri.scheme != ContentResolver.SCHEME_FILE) {
                return 0
            }

            var exif: ExifInterface? = null
            try {
                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    exif = ExifInterface(inputStream)
                }
            } catch (e: IOException) {
                CrashUtils(context,"").logException(e)
                throw RuntimeException("failed to open file to read rotation meta data: $imageUri", e)
            }

            return exif!!.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }

        /** Rotates a bitmap if it is converted from a bytebuffer. */
        fun rotateBitmap(
            bitmap: Bitmap,
            rotationDegrees: Int,
            flipX: Boolean,
            flipY: Boolean
        ): Bitmap {
            val matrix = Matrix()

            // Rotate the image back to straight.
            matrix.postRotate(rotationDegrees.toFloat())

            // Mirror the image along the X or Y axis.
//            matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)

            val rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            // Recycle the old bitmap if it has changed.
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            return rotatedBitmap
        }


        fun saveBitmapToStorage(context: Context, bitmap: Bitmap) {
            var fileOutputStream: FileOutputStream? = null
            val dir: File
            val file: File
            try {
                dir = ImageFileProvider.filesDir(context)!!
                val fileName = UUID.randomUUID().toString() + ".jpeg"
                file = File(dir, fileName)
                fileOutputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 30, fileOutputStream)
                fileOutputStream.close()
            } catch (e: FileNotFoundException) {
                CrashUtils(context,"").logException(e)
            } catch (e: IOException) {
                CrashUtils(context,"").logException(e)
            } finally {
                fileOutputStream?.close()
            }
        }


        fun preProcessBitmap(context: Context, bitmap: Bitmap): Bitmap {
            SpUtil.getInstance().init(context)
            var processedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            var pix = ReadFile.readBitmap(processedBitmap)

            if(SpUtil.getInstance().getBoolean(Constants.KEY_GRAYSCALE, true)) {
                // grayscale
                pix = Convert.convertTo8(pix)
            }

            if (SpUtil.getInstance().getBoolean(Constants.KEY_CONTRAST, true)) {
                // pix=AdaptiveMap.backgroundNormMorph(pix);
                if(pix.depth != 8) {
                    // Requires an 8 bpp PIX image.
                    pix = Convert.convertTo8(pix)
                }
                pix = AdaptiveMap.pixContrastNorm(pix)
            }

            if (SpUtil.getInstance().getBoolean(Constants.KEY_UN_SHARP_MASKING, true)) {
                pix = Enhance.unsharpMasking(pix)
            }

            if (SpUtil.getInstance().getBoolean(Constants.KEY_OTSU_THRESHOLD, true)) {
                if(pix.depth != 8) {
                    // Requires an 8 bpp PIX image.
                    pix = Convert.convertTo8(pix)
                }
                pix = Binarize.otsuAdaptiveThreshold(pix)
            }

            if (SpUtil.getInstance().getBoolean(Constants.KEY_FIND_SKEW_AND_DESKEW, true)) {
                val skewAngle = Skew.findSkew(pix)
                pix = Rotate.rotate(pix, skewAngle)
            }

            processedBitmap = WriteFile.writeBitmap(pix)
            if (SpUtil.getInstance().getBoolean(Constants.KEY_ADAPTIVE_THRESHOLD, true)) {
                val maxValue = SpUtil.getInstance().getString(Constants.KEY_ADAPTIVE_THRESHOLD_MAX_VALUE,
                    Constants.VALUE_ADAPTIVE_THRESHOLD_MAX_VALUE_DEFAULT)?.toDouble()
                var adaptiveMethod = SpUtil.getInstance().getString(Constants.KEY_ADAPTIVE_THRESHOLD_METHOD,
                    Constants.VALUE_ADAPTIVE_THRESHOLD_METHOD_DEFAULT)?.toInt()
                var thresholdType = SpUtil.getInstance().getString(Constants.KEY_ADAPTIVE_THRESHOLD_TYPE,
                    Constants.VALUE_ADAPTIVE_THRESHOLD_TYPE_DEFAULT)?.toInt()
                var blockSize = SpUtil.getInstance().getString(Constants.KEY_ADAPTIVE_THRESHOLD_BLOCK_SIZE,
                    Constants.VALUE_ADAPTIVE_THRESHOLD_BLOCK_SIZE_DEFAULT)?.toInt()
                var mean = SpUtil.getInstance().getString(Constants.KEY_ADAPTIVE_THRESHOLD_MEAN,
                    Constants.VALUE_ADAPTIVE_THRESHOLD_MEAN_DEFAULT)?.toDouble()
                processedBitmap = applyAdaptiveThreshold(processedBitmap, maxValue!!,
                    adaptiveMethod!!, thresholdType!!, blockSize!!, mean!!)
            }

            return processedBitmap
        }

        /**
         * Uses OpenCV's Imgproc.adaptiveThreshold method to perform adaptive thresholding
         * on the input image.
         */
        private fun applyAdaptiveThreshold(bitmap: Bitmap,
                                           maxValue: Double,
                                           adaptiveMethod: Int,
                                           thresholdType: Int,
                                           blockSize: Int,
                                           mean: Double): Bitmap {
            val bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            // Convert bitmap to Mat
            val inputMat = Mat()
            Utils.bitmapToMat(bitmap, inputMat)
            // Convert to grayscale if necessary
            if (inputMat.channels() > 1) {
                Imgproc.cvtColor(inputMat, inputMat, Imgproc.COLOR_BGR2GRAY)
            }
            // Apply adaptive thresholding
            val outputMat = Mat()
            Imgproc.adaptiveThreshold(
                inputMat,
                outputMat,
                maxValue,
                adaptiveMethod,
                thresholdType,
                blockSize,
                mean
            )

            // Convert Mat to bitmap
            Utils.matToBitmap(outputMat, bitmap)
            // Convert bitmap to Mat
            // val resultMat = Mat()
            // Utils.bitmapToMat(bitmap, resultMat)
            return bitmap
        }

        /**
         * Calculate the largest inSampleSize value that is a power of 2 and keeps both height and width
         * larger than the requested height and width.
         */
        private fun calculateInSampleSizeByRequestedSize(
            width: Int, height: Int, reqWidth: Int, reqHeight: Int
        ): Int {
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                while ((height / 2 / inSampleSize) > reqHeight && (width / 2 / inSampleSize) > reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        /**
         * Calculate the largest inSampleSize value that is a power of 2 and keeps both height and width
         * smaller than max texture size allowed for the device.
         */
        private fun calculateInSampleSizeByMaxTextureSize(width: Int, height: Int): Int {
            var inSampleSize = 1
            if (mMaxTextureSize == 0) {
                mMaxTextureSize = getMaxTextureSize()
            }
            if (mMaxTextureSize!! > 0) {
                while ((height / inSampleSize) > mMaxTextureSize!! || (width / inSampleSize) > mMaxTextureSize!!) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }


        /**
         * Get the max size of bitmap allowed to be rendered on the device.
         * [http://stackoverflow.com/questions/7428996/hw-accelerated-activity-how-to-get-opengl-texture-size-limit].
         */
        private fun getMaxTextureSize(): Int {
            // Safe minimum default size
            val IMAGE_MAX_BITMAP_DIMENSION = 2048

            return try {
                // Get EGL Display
                val egl: EGL10 = EGLContext.getEGL() as EGL10
                val display: EGLDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)

                // Initialize
                val version = IntArray(2)
                egl.eglInitialize(display, version)

                // Query total number of configurations
                val totalConfigurations = IntArray(1)
                egl.eglGetConfigs(display, null, 0, totalConfigurations)

                // Query actual list configurations
                val configurationsList = arrayOfNulls<EGLConfig>(totalConfigurations[0])
                egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations)

                val textureSize = IntArray(1)
                var maximumTextureSize = 0

                // Iterate through all the configurations to locate the maximum texture size
                for (i in 0 until totalConfigurations[0]) {
                    // Only need to check for width since opengl textures are always squared
                    egl.eglGetConfigAttrib(
                        display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize
                    )

                    // Keep track of the maximum texture size
                    maximumTextureSize = maxOf(maximumTextureSize, textureSize[0])
                }

                // Release
                egl.eglTerminate(display)

                // Return the largest texture size found, or default
                maxOf(maximumTextureSize, IMAGE_MAX_BITMAP_DIMENSION)
            } catch (e: Exception) {
                IMAGE_MAX_BITMAP_DIMENSION
            }
        }

        /**
         * Decode image from uri using "inJustDecodeBounds" to get the image dimensions.
         */
        @Throws(FileNotFoundException::class)
        private fun decodeImageForOption(resolver: ContentResolver, uri: Uri): BitmapFactory.Options {
            resolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(stream, EMPTY_RECT, options)
                options.inJustDecodeBounds = false
                return options
            }
            throw FileNotFoundException("Failed to open input stream for URI: $uri")
        }

        /**
         * Decode image from uri using given "inSampleSize", but if failed due to out-of-memory then raise
         * the inSampleSize until success.
         */
        @Throws(FileNotFoundException::class)
        private fun decodeImage(resolver: ContentResolver, uri: Uri, options: BitmapFactory.Options): Bitmap? {
            var stream: InputStream? = null
            do {
                try {
                    stream = resolver.openInputStream(uri)
                    return BitmapFactory.decodeStream(stream, EMPTY_RECT, options)
                } catch (e: OutOfMemoryError) {
                    options.inSampleSize *= 2
                } finally {
                    closeSafe(stream)
                }
            } while (options.inSampleSize <= 512)
            throw RuntimeException("Failed to decode image: $uri")
        }

        private fun decodeSampledBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): BitmapSampled {
            try {
                val resolver: ContentResolver = context.contentResolver

                // First decode with inJustDecodeBounds=true to check dimensions
                val options: BitmapFactory.Options = decodeImageForOption(resolver, uri)

                if (options.outWidth == -1 && options.outHeight == -1) {
                    throw RuntimeException("File is not a picture")
                }

                // Calculate inSampleSize
                options.inSampleSize =
                    calculateInSampleSizeByRequestedSize(
                        options.outWidth, options.outHeight, reqWidth, reqHeight
                    ).coerceAtLeast(
                        calculateInSampleSizeByMaxTextureSize(
                            options.outWidth,
                            options.outHeight
                        )
                    )

                // Decode bitmap with inSampleSize set
                val bitmap: Bitmap? = decodeImage(resolver, uri, options)

                return BitmapSampled(bitmap, options.inSampleSize)
            } catch (e: Exception) {
                throw RuntimeException("Failed to load sampled bitmap: $uri\r\n${e.message}", e)
            }
        }

        private fun decodeSampledBitmap(context: Context, uri: Uri): BitmapSampled {
            // Get ContentResolver
            val resolver: ContentResolver = context.contentResolver

            // Get height and width of image through BitmapFactory.Options inJustDecodeBounds
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true

            try {
                resolver.openInputStream(uri)?.use { stream ->
                    // Just get height and width of image, not load into memory
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            val imageWidth = options.outWidth
            val imageHeight = options.outHeight

            return decodeSampledBitmap(context, uri, imageWidth, imageHeight)
        }


        /**
         * Close the given closeable object (Stream) in a safe way: check if it is null and catch-log
         * exception thrown.
         *
         * @param closeable the closable object to close
         */
        private fun closeSafe(closeable: Closeable?) {
            closeable?.let {
                try {
                    it.close()
                } catch (ignored: IOException) {
                }
            }
        }
    }

    /**
     * Holds bitmap instance and the sample size that the bitmap was loaded/cropped with.
     */
    data class BitmapSampled(
        /**
         * The bitmap instance
         */
        val bitmap: Bitmap?,
        /**
         * The sample size used to lower the size of the bitmap (1,2,4,8,...)
         */
        val sampleSize: Int
    )



}