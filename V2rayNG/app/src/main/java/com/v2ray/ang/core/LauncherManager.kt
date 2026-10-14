package com.v2ray.ang.core

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.service.CoreProxyOnlyService
import com.v2ray.ang.service.CoreRootService
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.*

/** UI-process launcher and stop watchdog; never loads or calls the native core. */
object LauncherManager {
    private const val STOP_TIMEOUT_MS = 3_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stopJob: Job? = null
    private var startJob: Job? = null
    private var listening = false
    private fun listen(context: Context) {
        if (listening) return
        listening = true
        ContextCompat.registerReceiver(context.applicationContext, object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra("key", 0)) {
                    AppConfig.MSG_STATE_START_SUCCESS, AppConfig.MSG_STATE_START_FAILURE -> onStarted()
                    AppConfig.MSG_STATE_STOP_SUCCESS -> onStopped()
                }
            }
        }, android.content.IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    @Volatile private var starting = false
    fun isStarting() = starting
    fun onStarted() { starting = false }
    fun isStopping(): Boolean = stopJob?.isActive == true
    fun onStopped() { stopJob?.cancel(); stopJob = null }

    fun hasDaemon(context: Context): Boolean = ownedDaemon(context)?.pid == MmkvManager.decodeSettingsInt(AppConfig.PREF_CORE_PID, 0)
    private fun ownedDaemon(context: Context): ActivityManager.RunningAppProcessInfo? =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
            ?.firstOrNull { DaemonIdentity.isOwned(it.processName, it.uid, it.pid,
                context.packageName, Process.myUid(), Process.myPid()) }

    private fun stopServiceInstances(context: Context) {
        listOf(CoreVpnService::class.java, CoreRootService::class.java, CoreProxyOnlyService::class.java)
            .forEach { context.stopService(Intent(context, it)) }
    }

    /**
     * Starts the V2Ray service from a toggle action.
     * @param context The context from which the service is started.
     * @return True if the service was started successfully, false otherwise.
     */
    fun startVServiceFromToggle(context: Context): Boolean {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        try {
            startVService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: ${e.message}", e)
            context.toast(e.message ?: e.javaClass.simpleName)
            return false
        }
        return true
    }

    /**
     * Starts the V2Ray service.
     * @param context The context from which the service is started.
     * @param guid The GUID of the server configuration to use (optional).
     */
    fun startVService(context: Context, guid: String? = null) {
        listen(context)
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: startVService from ${context::class.java.simpleName}")

        if (isStopping() || starting) return
        starting = true
        startJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (guid != null) MmkvManager.setSelectServer(guid)
                    startContextService(context.applicationContext)
                }
            } catch (e: Exception) {
                starting = false
                if (e is CancellationException) throw e
                LogUtil.e(AppConfig.TAG, "Service launch failed", e)
                context.toast(e.message ?: e.javaClass.simpleName)
                MessageUtil.sendMsg2UI(context, AppConfig.MSG_STATE_START_FAILURE, e.message.orEmpty())
            }
        }
    }

    /**
     * Stops the V2Ray service.
     * @param context The context from which the service is stopped.
     */
    fun stopVService(context: Context) {
        listen(context)
        if (isStopping()) return
        startJob?.cancel()
        starting = false
        val app = context.applicationContext
        val token = java.util.UUID.randomUUID().toString()
        stopJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    check(MmkvManager.encodeSettings(AppConfig.PREF_STOP_REQUESTED, token))
                }
                MessageUtil.sendMsg2Service(app, AppConfig.MSG_STATE_STOP, "")
                // This runs in the UI process, independently of native calls and daemon locks.
                delay(STOP_TIMEOUT_MS)
                withContext(Dispatchers.IO) {
                    if (MmkvManager.decodeSettingsString(AppConfig.PREF_STOP_REQUESTED) != token) return@withContext
                    stopServiceInstances(app)
                    repeat(20) {
                        if (MmkvManager.decodeSettingsString(AppConfig.PREF_STOP_REQUESTED) != token) return@withContext
                        ownedDaemon(app)?.let { Process.killProcess(it.pid) }
                        if (ownedDaemon(app) == null) return@withContext
                        delay(100)
                    }
                    check(ownedDaemon(app) == null) { "VPN process did not stop" }
                }
                if (MmkvManager.decodeSettingsString(AppConfig.PREF_STOP_REQUESTED) != token) return@launch
                (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(1)
                MessageUtil.sendMsg2UI(app, AppConfig.MSG_STATE_STOP_SUCCESS, "")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                LogUtil.e(AppConfig.TAG, "Forced service stop failed", e)
                MessageUtil.sendMsg2UI(app, AppConfig.MSG_STATE_STOP_FAILURE, app.getString(R.string.toast_services_stop_failure))
            }
        }
    }

    /**
     * Starts the context service for V2Ray.
     * Chooses between VPN service or Proxy-only service based on user settings.
     * @param context The context from which the service is started.
     * @throws IllegalStateException if the core is already running, no server is selected,
     *   server config cannot be decoded, or server configuration is invalid.
     * @throws Exception if the foreground service fails to start.
     */
    @Throws(Exception::class)
    private suspend fun startContextService(context: Context) {
        if (isStopping()) return

        val guid = MmkvManager.getSelectServer()
            ?: run {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: No server selected")
                error(context.getString(R.string.app_tile_first_use))
            }

        val config = MmkvManager.decodeServerConfig(guid)
            ?: run {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to decode server config")
                error(context.getString(R.string.toast_config_file_invalid))
            }

        if (!config.configType.isComplexType()
            && !Utils.isValidUrl(config.server)
            && !Utils.isPureIpAddress(config.server.orEmpty())
        ) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Invalid server configuration")
            error(context.getString(R.string.toast_config_file_invalid))
        }

        // refresh socks port when enabled dynamic socks port
        SettingsManager.refreshRuntimeSocksPort()

//        val result = V2rayConfigUtil.getV2rayConfig(context, guid)
//        if (!result.status) error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })

        withContext(Dispatchers.Main) {
            if (config.insecure == true) {
                context.toastError(R.string.toast_allow_insecure_deprecated)
                context.toastError(R.string.toast_allow_insecure_deprecated)
            }

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
                context.toast(R.string.toast_warning_pref_proxysharing_short)
            } else {
                context.toast(R.string.toast_services_start)
            }

        }
        val isRootMode = SettingsManager.isRootMode()
        if (isRootMode && !RootManager.isRootAvailable()) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: root mode requires root but none available")
            error(context.getString(R.string.toast_root_required))
        }

        val intent = if (isRootMode) {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting Root service")
            Intent(context.applicationContext, CoreRootService::class.java)
        } else if (SettingsManager.isVpnMode()) {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting VPN service")
            Intent(context.applicationContext, CoreVpnService::class.java)
        } else {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting Proxy service")
            Intent(context.applicationContext, CoreProxyOnlyService::class.java)
        }

        currentCoroutineContext().ensureActive()
        check(MmkvManager.encodeSettings(AppConfig.PREF_STOP_REQUESTED, ""))
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Missing permission to start foreground service", e)
            throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Foreground service start not allowed", e)
                throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
            }
            throw e
        }
    }

}
