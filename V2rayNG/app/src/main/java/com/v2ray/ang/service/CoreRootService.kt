package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.root.RootProxyManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var setupJob: Job? = null
    private val stopping = AtomicBoolean(false)
    @Volatile private var ready = false



    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service created")
        CoreServiceManager.serviceControl = SoftReference(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (stopping.get()) return START_NOT_STICKY
        NotificationManager.ensureForeground()
        if (ready && CoreServiceManager.isRunning()) return START_STICKY
        if (setupJob?.isActive == true) return START_NOT_STICKY
        setupJob = serviceScope.launch {
            try {
                if (!CoreServiceManager.startCoreLoop(null)) {
                    stopService()
                } else {
                    if (!RootProxyManager.start(this@CoreRootService)) {
                        LogUtil.e(AppConfig.TAG, "StartCore-Root: failed to start routing")
                        stopService()
                    } else {
                        ready = true
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Service setup failed: root", error)
                stopService()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopService()
        super.onDestroy()
    }

    override fun getService(): Service = this

    override fun startService() {
        // do nothing
    }

    override fun stopService() {
        if (!stopping.compareAndSet(false, true)) return
        ready = false
        val pendingSetup = setupJob
        CoreServiceManager.stopCoreLoop(
            beforeStop = {
                pendingSetup?.cancelAndJoin()
                RootProxyManager.stop(this@CoreRootService)
            },
            onFinished = { serviceScope.cancel(); stopSelf() },
            onFailed = { serviceScope.cancel(); stopSelf() },
        )
    }

    override fun vpnProtect(socket: Int): Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let(AppLocaleManager::localizedContext)
        super.attachBaseContext(context)
    }
}
