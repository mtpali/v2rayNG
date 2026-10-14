package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.root.RootProxyManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MyContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.lang.ref.SoftReference

/**
 * Foreground service for the root (system-wide) run modes. Unlike [CoreVpnService] it
 * does not use Android VpnService — traffic is routed by iptables instead
 * (see [RootProxyManager]).
 *
 * The in-process core is started first (so its listener is up and the foreground
 * notification is posted promptly), then the root routing rules are installed off the
 * main thread. On teardown the rules are removed before the core stops.
 */
class CoreRootService : Service(), ServiceControl {

    private var setupJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopping = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service created")
        CoreServiceManager.serviceControl = SoftReference(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.i(AppConfig.TAG, "StartCore-Root: command received")
        if (!MmkvManager.decodeSettingsString(AppConfig.PREF_STOP_REQUESTED).isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (stopping.get()) return START_NOT_STICKY
        if (CoreServiceManager.isRunning() || setupJob?.isActive == true) return START_STICKY
        // Start the in-process core first (this also posts the foreground notification),
        // then install the root routing off the main thread.
        if (!CoreServiceManager.startCoreLoop(null)) {
            LogUtil.e(AppConfig.TAG, "StartCore-Root: core failed to start")
            stopService()
            return START_NOT_STICKY
        }

        setupJob = serviceScope.launch {
            if (!RootProxyManager.start(this@CoreRootService)) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: failed to start root mode, stopping")
                stopService()
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        com.v2ray.ang.root.HotspotSession.disable(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService()
    }

    override fun getService(): Service = this

    override fun startService() {
        // do nothing
    }

    override fun stopService() {
        if (!stopping.compareAndSet(false, true)) return
        DaemonStopWatchdog.arm()
        serviceScope.launch {
            setupJob?.cancelAndJoin()
            RootProxyManager.stop(this@CoreRootService)
            CoreServiceManager.stopCoreLoop(onFinished = {
                stopSelf()
                serviceScope.cancel()
            }, onFailed = { stopSelf() })
        }
    }

    override fun vpnProtect(socket: Int): Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }
}
