package com.v2ray.ang.root

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.MessageUtil
import java.io.File

/** Hotspot belongs to this app session, rather than a permanent startup preference. */
object HotspotSession {
    private fun ownerFile(context: Context) = File(context.filesDir, "${AppConfig.ROOT_RUNTIME_DIR}/hotspot.owner")

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (!enabled) return disable(context)
        val identity = "${Process.myPid()} ${startTime(Process.myPid())}"
        if (!MmkvManager.encodeSettings(AppConfig.PREF_HOTSPOT_OWNER, identity)) return false
        ownerFile(context).apply { parentFile?.mkdirs(); writeText(identity) }
        return MmkvManager.encodeSettings(AppConfig.PREF_ROOT_LAN_SHARING, true)
    }

    fun disable(context: Context): Boolean {
        val saved = MmkvManager.encodeSettings(AppConfig.PREF_ROOT_LAN_SHARING, false)
        ownerFile(context).delete()
        MessageUtil.sendMsg2UI(context, AppConfig.MSG_TRAFFIC_UPDATED, "")
        return saved
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
