package com.v2ray.ang.root

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import java.io.File

/** Hotspot belongs to this app session, rather than a permanent startup preference. */
object HotspotSession {
    private fun ownerFile(context: Context) = File(context.filesDir, "${AppConfig.ROOT_RUNTIME_DIR}/hotspot.owner")

    @Synchronized fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (!enabled) return disable(context)
        return try {
            val identity = "${Process.myPid()} ${startTime(Process.myPid())}"
            ownerFile(context).apply { parentFile?.mkdirs(); writeText(identity) }
            val saved = MmkvManager.encodeSettings(AppConfig.PREF_HOTSPOT_OWNER, identity) &&
                MmkvManager.encodeSettings(AppConfig.PREF_ROOT_LAN_SHARING, true)
            if (!saved) disable(context)
            saved
        } catch (error: Exception) {
            LogUtil.e(AppConfig.TAG, "Hotspot-session: failed to save owner; disabling sharing", error)
            disable(context)
            false
        }
    }

    @Synchronized fun disable(context: Context): Boolean {
        val saved = MmkvManager.encodeSettings(AppConfig.PREF_ROOT_LAN_SHARING, false)
        MmkvManager.encodeSettings(AppConfig.PREF_HOTSPOT_OWNER, "")
        val removed = try {
            val file = ownerFile(context)
            (!file.exists() || file.delete()).also { if (!it) LogUtil.e(AppConfig.TAG, "Hotspot-session: owner cleanup failed") }
        } catch (error: Exception) {
            LogUtil.e(AppConfig.TAG, "Hotspot-session: owner cleanup failed", error)
            false
        }
        MessageUtil.sendMsg2UI(context, AppConfig.MSG_TRAFFIC_UPDATED, "")
        return saved && removed
    }

    fun resetOnFreshUiProcess(context: Context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (manager.runningAppProcesses?.any { it.pid == Process.myPid() && it.processName == context.packageName } == true) {
            disable(context)
        }
    }

    fun startTime(pid: Int): String = File("/proc/$pid/stat").readText()
        .substringAfterLast(") ").split(' ')[19]
}
