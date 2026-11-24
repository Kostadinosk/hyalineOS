package os.hyaline.core.privilege

import android.content.pm.PackageManager
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

/**
 * Strict Privilege Gate for hyalineOS OS 2.
 *
 * Policy: OS 2 remains completely locked and hidden until either Shizuku or Root (`su`)
 * is granted. Zero non-privileged fallback is permitted.
 */
enum class PrivilegeStatus {
    NONE,
    SHIZUKU,
    ROOT,
}

object PrivilegeGate {
    const val SHIZUKU_REQUEST_CODE = 1337

    fun probe(): PrivilegeStatus {
        if (isShizukuGranted()) return PrivilegeStatus.SHIZUKU
        if (isRootGranted()) return PrivilegeStatus.ROOT
        return PrivilegeStatus.NONE
    }

    fun isShizukuAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isShizukuGranted(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestShizukuPermission() {
        if (isShizukuAvailable() && !isShizukuGranted()) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    fun isRootGranted(): Boolean = runCatching {
        Shell.getShell().isRoot
    }.getOrDefault(false)

    fun isOs2Unlocked(): Boolean = probe() != PrivilegeStatus.NONE
}
