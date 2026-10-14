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
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.lang.ref.SoftReference

class CoreProxyOnlyService : Service(), ServiceControl {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var setupJob: Job? = null
    private val stopping = AtomicBoolean(false)
    @Volatile private var ready = false

    /**
     * Initializes the service.
     */
    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Proxy: Service created")
        CoreServiceManager.serviceControl = SoftReference(this)
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
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
                    ready = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Service setup failed: proxy", error)
                stopService()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopService()
        super.onDestroy()
    }

    override fun getService(): Service {
        return this
    }

    /**
     * Starts the service.
     */
    override fun startService() {
        // do nothing
    }

    /**
     * Stops the service.
     */
    override fun stopService() {
        if (!stopping.compareAndSet(false, true)) return
        ready = false
        val pendingSetup = setupJob
        CoreServiceManager.stopCoreLoop(
            beforeStop = {
                pendingSetup?.cancelAndJoin()
            },
            onFinished = { serviceScope.cancel(); stopSelf() },
            onFailed = { serviceScope.cancel(); stopSelf() },
        )
    }

    /**
     * Protects the VPN socket.
     * @param socket The socket to protect.
     * @return True if the socket is protected, false otherwise.
     */
    override fun vpnProtect(socket: Int): Boolean {
        return true
    }

    /**
     * Binds the service.
     * @param intent The intent.
     * @return The binder.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Attaches the base context to the service.
     * @param newBase The new base context.
     */
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let(AppLocaleManager::localizedContext)
        super.attachBaseContext(context)
    }
}
