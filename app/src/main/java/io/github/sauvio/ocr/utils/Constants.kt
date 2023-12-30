package io.github.sauvio.ocr.utils

class Constants {
    companion object {
        const val KEY_GRAYSCALE: String = "key_grayscale"
        const val KEY_ADAPTIVE_THRESHOLD_MEAN: String = "key_adaptive_threshold_mean"
        const val KEY_ADAPTIVE_THRESHOLD_BLOCK_SIZE: String = "key_adaptive_threshold_block_size"
        const val KEY_ADAPTIVE_THRESHOLD_TYPE: String = "key_adaptive_threshold_type"
        const val KEY_ADAPTIVE_THRESHOLD_METHOD: String = "key_adaptive_threshold_method"
        const val KEY_ADAPTIVE_THRESHOLD_MAX_VALUE: String = "key_adaptive_threshold_max_value"
        const val KEY_CONTRAST = "process_contrast"
        const val KEY_UN_SHARP_MASKING = "un_sharp_mask"
        const val KEY_OTSU_THRESHOLD = "otsu_threshold"
        const val KEY_FIND_SKEW_AND_DESKEW = "deskew_img"
        const val KEY_ADAPTIVE_THRESHOLD = "adaptive_threshold"
        const val KEY_PERSIST_DATA = "persist_data"
        const val VALUE_ADAPTIVE_THRESHOLD_MAX_VALUE_DEFAULT = "200.0"
        const val VALUE_ADAPTIVE_THRESHOLD_METHOD_DEFAULT = "0"
        const val VALUE_ADAPTIVE_THRESHOLD_TYPE_DEFAULT = "0"
        const val VALUE_ADAPTIVE_THRESHOLD_BLOCK_SIZE_DEFAULT = "25"
        const val VALUE_ADAPTIVE_THRESHOLD_MEAN_DEFAULT = "10.0"
    }
}