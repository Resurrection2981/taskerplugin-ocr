package io.github.sauvio.ocr.tasker

import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable

@TaskerOutputObject()
class OCROutput(
    @get:TaskerOutputVariable(VAR_RESULT, labelResIdName = "result", htmlLabelResIdName = "result_description") var result: String?,
    @get:TaskerOutputVariable("coordinates", labelResIdName = "coordinates", htmlLabelResIdName = "coordinates_description") var coordinates: String?,
    @get:TaskerOutputVariable("text", labelResIdName = "text", htmlLabelResIdName = "text_description") var text: String?
) {
    companion object {
        const val VAR_RESULT = "result"
    }
}