package io.github.sauvio.ocr.tasker

import com.joaomgcd.taskerpluginlibrary.SimpleResult
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

@TaskerInputRoot
class OCRInput @JvmOverloads constructor(
    @field:TaskerInputField("imagePathName", labelResIdName = "image_path_name")
    var imagePathName: String?= null,

    @field:TaskerInputField("resultVariableName", labelResIdName = "result_variable_name")
    var resultVariableName: String?= null) {

    val invalid = SimpleResult.get {
        if (imagePathName.isNullOrEmpty()) {
            throw IllegalArgumentException("Image path name must not be null or empty.")
        }
        imagePathName
    }
}
/**
 * list of infos that could come from a main app. Are not related to the Tasker UI.
 * In this list each item has a property that says if it's a Tasker value or not
 */
class InfoFromMainApp(val name: String, val hasTaskerVariable: Boolean = false)
class InfosFromMainApp : ArrayList<InfoFromMainApp>()

val infos = InfosFromMainApp().apply {
    addAll(arrayOf(
        InfoFromMainApp("image_path_name", true),
        InfoFromMainApp("genre", false)
    ))
}

/**
 * Get all infos that are Tasker values
 */
val infosForTasker get() = infos.filter { it.hasTaskerVariable }