package io.github.sauvio.ocr.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerOutputRename
import com.joaomgcd.taskerpluginlibrary.runner.TaskerOutputRenames
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import io.github.sauvio.ocr.R
import kotlinx.coroutines.runBlocking
import net.dinglisch.android.tasker.TaskerPlugin

const val TAG = "OCRRunner"
class OCRRunner : TaskerPluginRunnerAction<OCRInput, OCROutput>() {

    override val notificationProperties get() = NotificationProperties(iconResId = R.drawable.plugin)

    override fun run(context: Context,input: TaskerInput<OCRInput>): TaskerPluginResult<OCROutput> {
        val imagePathName = input.regular.imagePathName?.takeUnless {
            it.startsWith(TaskerPlugin.VARIABLE_PREFIX) || it.isBlank()
        } ?: return TaskerPluginResultErrorWithOutput(0, context.getString(R.string.hint_image_path_name))
        val deferred = runBlocking {
            BackgroundWork().readText(context, imagePathName).await()
        }
        return TaskerPluginResultSucess(deferred)
    }

    override fun addOutputVariableRenames(context: Context, input: TaskerInput<OCRInput>, renames: TaskerOutputRenames) {
        super.addOutputVariableRenames(context, input, renames)
        renames.add(TaskerOutputRename(OCROutput.VAR_RESULT, input.regular.resultVariableName))
    }

}