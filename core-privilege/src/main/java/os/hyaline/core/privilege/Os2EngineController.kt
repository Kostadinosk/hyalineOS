package os.hyaline.core.privilege

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Privileged command execution engine powering both OS 2 user-selectable modes:
 * 1. App Freezing & Hiding (`pm disable-user --user 0` / `pm suspend` / `pm enable`)
 * 2. Freeform Floating Windows (`enable_freeform_support` + `WINDOWING_MODE_FREEFORM` + bounds)
 * 3. Interactive OS 2 Shell Terminal
 */
class Os2EngineController {

    data class CommandResult(
        val success: Boolean,
        val output: String,
    )

    suspend fun runShellCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        val status = PrivilegeGate.probe()
        if (status == PrivilegeStatus.NONE) {
            return@withContext CommandResult(false, "OS 2 strictly locked: Root or Shizuku required.")
        }
        executePrivileged(command, status)
    }

    suspend fun setPackageFrozen(packageName: String, freeze: Boolean): CommandResult =
        withContext(Dispatchers.IO) {
            val status = PrivilegeGate.probe()
            if (status == PrivilegeStatus.NONE) {
                return@withContext CommandResult(false, "OS 2 strictly locked: Root or Shizuku required.")
            }
            val safePkg = packageName.replace(Regex("[^a-zA-Z0-9._]"), "")
            if (safePkg.isEmpty()) return@withContext CommandResult(false, "Invalid package name")

            val cmd = if (freeze) {
                "pm disable-user --user 0 $safePkg || pm suspend --user 0 $safePkg"
            } else {
                "pm enable $safePkg; pm unsuspend --user 0 $safePkg"
            }
            executePrivileged(cmd, status)
        }

    suspend fun enableFreeformWindowing(): CommandResult = withContext(Dispatchers.IO) {
        val status = PrivilegeGate.probe()
        if (status == PrivilegeStatus.NONE) {
            return@withContext CommandResult(false, "OS 2 strictly locked: Root or Shizuku required.")
        }
        val cmd = "settings put global enable_freeform_support 1 && " +
            "settings put global force_resizable_activities 1 && " +
            "settings put global enable_non_resizable_multi_window 1"
        executePrivileged(cmd, status)
    }

    fun launchInFreeformWindow(
        context: Context,
        packageName: String,
        bounds: Rect? = null,
    ): Boolean {
        if (!PrivilegeGate.isOs2Unlocked()) return false
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
        )

        val dm = context.resources.displayMetrics
        val targetBounds = bounds ?: Rect(
            (dm.widthPixels * 0.06f).toInt(),
            (dm.heightPixels * 0.12f).toInt(),
            (dm.widthPixels * 0.94f).toInt(),
            (dm.heightPixels * 0.78f).toInt(),
        )

        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            options.setLaunchBounds(targetBounds)
        }
        // Set WINDOWING_MODE_FREEFORM = 5 via ActivityOptions hidden API where allowed
        runCatching {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, 5)
        }

        return runCatching {
            context.startActivity(launchIntent, options.toBundle())
            true
        }.getOrDefault(false)
    }

    private fun executePrivileged(command: String, status: PrivilegeStatus): CommandResult =
        when (status) {
            PrivilegeStatus.ROOT -> {
                val res = Shell.cmd(command).exec()
                val combined = (res.out + res.err).joinToString("\n").ifBlank { "(exit ${res.code})" }
                CommandResult(res.isSuccess, combined)
            }
            PrivilegeStatus.SHIZUKU -> runCatching {
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

                val proc = newProcessMethod.invoke(
                    null,
                    arrayOf("sh", "-c", command),
                    null,
                    null,
                ) as Process

                val stdout = proc.inputStream.bufferedReader().readText()
                val stderr = proc.errorStream.bufferedReader().readText()
                val code = proc.waitFor()
                val out = listOf(stdout.trim(), stderr.trim())
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                    .ifBlank { "(Shizuku exit $code)" }
                CommandResult(code == 0, out)
            }.getOrElse { err ->
                CommandResult(false, "Shizuku execution error: ${err.message}")
            }
            PrivilegeStatus.NONE -> CommandResult(false, "No privileged backend active.")
        }
}
