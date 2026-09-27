package com.v2ray.ang.service

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.StrictMode
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.contracts.Tun2SocksControl
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.root.RootLanSharing
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.SoftReference

@SuppressLint("VpnServicePolicy")
class CoreVpnService : VpnService(), ServiceControl {
    private lateinit var mInterface: ParcelFileDescriptor
    @Volatile private var isRunning = false
    @Volatile private var coreReady = false
    private val stopping = java.util.concurrent.atomic.AtomicBoolean(false)
    private val destroyed = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var networkRecovery = VpnNetworkRecoveryGate()
    @Volatile private var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var tun2SocksService: Tun2SocksControl? = null
    private val wakeRecoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeReceiverRegistered = false
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val gate = networkRecovery
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> gate.onScreenOff(SystemClock.elapsedRealtime())
                Intent.ACTION_USER_PRESENT -> recoverAfterIdle(gate)
                Intent.ACTION_SCREEN_ON ->
                    if (!(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
                        recoverAfterIdle(gate)
                    }
            }
        }
    }

    private fun recoverAfterIdle(gate: VpnNetworkRecoveryGate) {
        if (!gate.onUserPresent(SystemClock.elapsedRealtime(), canRecoverAmnezia())) return
        LogUtil.i(AppConfig.TAG, "StartCore-VPN: Long screen-off interval; recovering Amnezia tunnel")
        wakeRecoveryScope.launch {
            // Let Android restore network access after the screen wakes before
            // binding a new protected UDP socket on the same Wi-Fi/cellular network.
            delay(1_500L)
            if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive) {
                gate.cancelRecovery()
                return@launch
            }
            if (gate === networkRecovery && gate.recoveryRequested() && canRecoverAmnezia()) {
                recoverFromNetworkChange()
            }
        }
    }

    /**destroy
     * Unfortunately registerDefaultNetworkCallback is going to return our VPN interface: https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
    }

    private val connectivity by lazy { getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createNetworkCallback(gate: VpnNetworkRecoveryGate) =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (stopping.get() || !gate.isActive()) return
                setUnderlyingNetworks(arrayOf(network))
                // A protected AWG UDP socket can remain on a stale physical network
                // after Wi-Fi sleeps or hands over to cellular. Recreate it through
                // the existing coordinated stop/restart path, once per service run.
                if (gate.onAvailable(
                        network.networkHandle,
                        canRecoverAmnezia()
                    )) {
                    LogUtil.i(AppConfig.TAG, "StartCore-VPN: Physical network changed; recovering Amnezia tunnel")
                    recoverFromNetworkChange()
                }
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
                // Android 10+ reports Doze network blocking on the same physical
                // network. Reopen the Amnezia transport when access returns.
                if (gate.onBlockedStatus(network.networkHandle, blocked, canRecoverAmnezia())) {
                    LogUtil.i(AppConfig.TAG, "StartCore-VPN: Physical network unblocked; recovering Amnezia tunnel")
                    recoverFromNetworkChange()
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                // it's a good idea to refresh capabilities
                if (!stopping.get() && gate.isCurrent(network.networkHandle)) {
                    setUnderlyingNetworks(arrayOf(network))
                }
            }

            override fun onLost(network: Network) {
                if (!stopping.get() && gate.onLost(network.networkHandle)) setUnderlyingNetworks(null)
            }
        }

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-VPN: Service created")
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        CoreServiceManager.serviceControl = SoftReference(this)
    }

    override fun onRevoke() {
        LogUtil.w(AppConfig.TAG, "StartCore-VPN: Permission revoked")
        stopAllService()
    }

//    override fun onLowMemory() {
//        stopV2Ray()
//        super.onLowMemory()
//    }

    override fun onDestroy() {
        destroyed.set(true)
        stopAllService()
        super.onDestroy()
        LogUtil.i(AppConfig.TAG, "StartCore-VPN: Service destroyed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.i(AppConfig.TAG, "StartCore-VPN: Service command received")
        NotificationManager.showNotification(null)
        if (stopping.get()) return START_NOT_STICKY
        if (isRunning && CoreServiceManager.isRunning()) return START_STICKY
        if (!setupVpnService()) return START_NOT_STICKY
        startService()
        return if (CoreServiceManager.isRunning()) START_STICKY else START_NOT_STICKY
        //return super.onStartCommand(intent, flags, startId)
    }

    override fun getService(): Service {
        return this
    }

    override fun startService() {
        if (!::mInterface.isInitialized) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Interface not initialized")
            return
        }
        if (!CoreServiceManager.startCoreLoop(mInterface)) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to start core loop")
            stopAllService()
            return
        }
        coreReady = true

        if (CoreServiceManager.isAmneziaProfile()) registerAwakeRecovery()

        // Start LAN sharing if enabled in settings
        RootLanSharing.startClientSharing(this)
    }

    override fun stopService() {
        stopAllService()
    }

    override fun vpnProtect(socket: Int): Boolean {
        return protect(socket)
    }

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }

    /**
     * Sets up the VPN service.
     * Prepares the VPN and configures it if preparation is successful.
     */
    private fun setupVpnService(): Boolean {
        val prepare = prepare(this)
        if (prepare != null) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Permission not granted")
            stopSelf()
            return false
        }

        if (configureVpnService() != true) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Configuration failed")
            stopSelf()
            return false
        }

        runTun2socks()
        return true
    }

    /**
     * Configures the VPN service.
     * @return True if the VPN service was configured successfully, false otherwise.
     */
    private fun configureVpnService(): Boolean {
        val builder = Builder()

        // Configure network settings (addresses, routing and DNS)
        configureNetworkSettings(builder)

        // Configure app-specific settings (session name and per-app proxy)
        configurePerAppProxy(builder)

        // Close the old interface since the parameters have been changed
        try {
            if (::mInterface.isInitialized) {
                mInterface.close()
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Failed to close old interface", e)
        }

        // Configure platform-specific features
        configurePlatformFeatures(builder)

        // Create a new interface using the builder and save the parameters
        try {
            mInterface = builder.establish()!!
            isRunning = true
            return true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish VPN interface", e)
            stopAllService()
        }
        return false
    }

    /**
     * Configures the basic network settings for the VPN.
     * This includes IP addresses, routing rules, and DNS servers.
     *
     * @param builder The VPN Builder to configure
     */
    private fun configureNetworkSettings(builder: Builder) {
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val bypassLan = SettingsManager.routingRulesetsBypassLan()

        // Configure IPv4 settings
        builder.setMtu(SettingsManager.getVpnMtu())
        builder.addAddress(vpnConfig.ipv4Client, 30)

        // Configure routing rules
        if (bypassLan) {
            AppConfig.ROUTED_IP_LIST.forEach {
                val addr = it.split('/')
                builder.addRoute(addr[0], addr[1].toInt())
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        // Configure IPv6 if enabled
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED) == true) {
            builder.addAddress(vpnConfig.ipv6Client, 126)
            if (bypassLan) {
                builder.addRoute("2000::", 3) // Currently only 1/8 of total IPv6 is in use
                builder.addRoute("fc00::", 18) // Xray-core default FakeIPv6 Pool
            } else {
                builder.addRoute("::", 0)
            }
        }

        // Configure DNS servers
        //if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true) {
        //  builder.addDnsServer(PRIVATE_VLAN4_ROUTER)
        //} else {
        SettingsManager.getVpnDnsServers().forEach {
            if (Utils.isPureIpAddress(it)) {
                builder.addDnsServer(it)
            }
        }

        //builder.setSession(V2RayServiceManager.getRunningServerName())
    }

    /**
     * Configures platform-specific VPN features for different Android versions.
     *
     * @param builder The VPN Builder to configure
     */
    private fun configurePlatformFeatures(builder: Builder) {
        // Android P (API 28) and above: Configure network callbacks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val gate = VpnNetworkRecoveryGate()
                val callback = createNetworkCallback(gate)
                networkRecovery = gate
                defaultNetworkCallback = callback
                connectivity.requestNetwork(defaultNetworkRequest, callback)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to request network", e)
            }
        }

        // Android Q (API 29) and above: Configure metering and HTTP proxy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APPEND_HTTP_PROXY)) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy(LOOPBACK, SettingsManager.getHttpPort()))
            }
        }
    }

    /**
     * Configures per-app proxy rules for the VPN builder.
     *
     * - If per-app proxy is not enabled, disallow the VPN service's own package.
     * - If no apps are selected, disallow the VPN service's own package.
     * - If bypass mode is enabled, disallow all selected apps (including self).
     * - If proxy mode is enabled, only allow the selected apps (excluding self).
     *
     * @param builder The VPN Builder to configure.
     */
    private fun configurePerAppProxy(builder: Builder) {
        val selfPackageName = BuildConfig.APPLICATION_ID

        // If per-app proxy is not enabled, disallow the VPN service's own package and return
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY) == false) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        // If no apps are selected, disallow the VPN service's own package and return
        val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
        if (apps.isNullOrEmpty()) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        // Handle the VPN service's own package according to the mode
        if (bypassApps) apps.add(selfPackageName) else apps.remove(selfPackageName)

        apps.forEach {
            try {
                if (bypassApps) {
                    // In bypass mode, disallow the selected apps
                    builder.addDisallowedApplication(it)
                } else {
                    // In proxy mode, only allow the selected apps
                    builder.addAllowedApplication(it)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to configure app", e)
            }
        }
    }

    /**
     * Runs the tun2socks process.
     * Starts the tun2socks process with the appropriate parameters.
     */
    private fun runTun2socks() {
        if (SettingsManager.isUsingHevTun()) {
            tun2SocksService = TProxyService(
                context = applicationContext,
                vpnInterface = mInterface,
                isRunningProvider = { isRunning },
                restartCallback = { runTun2socks() }
            )
        } else {
            tun2SocksService = null
        }

        tun2SocksService?.startTun2Socks()
    }

    private fun canRecoverAmnezia() = !stopping.get() && coreReady && CoreServiceManager.isAmneziaProfile()

    @Synchronized private fun registerAwakeRecovery() {
        if (wakeReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            ContextCompat.registerReceiver(this, wakeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            wakeReceiverRegistered = true
            val power = getSystemService(POWER_SERVICE) as PowerManager
            if (!power.isInteractive) networkRecovery.onScreenOff(SystemClock.elapsedRealtime())
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-VPN: Failed to register Amnezia wake receiver", e)
        }
    }

    @Synchronized private fun unregisterAwakeRecovery() {
        if (!wakeReceiverRegistered) return
        wakeReceiverRegistered = false
        try {
            unregisterReceiver(wakeReceiver)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StopCore-VPN: Failed to unregister Amnezia wake receiver", e)
        }
    }

    private fun recoverFromNetworkChange() {
        if (!stopping.compareAndSet(false, true)) return
        releaseVpnAndCore()
    }

    private fun stopAllService() {
        networkRecovery.cancelRecovery()
        wakeRecoveryScope.cancel()
        if (!stopping.compareAndSet(false, true)) return
        releaseVpnAndCore()
    }

    private fun releaseVpnAndCore() {
        networkRecovery.stop()
        unregisterAwakeRecovery()
        coreReady = false
        isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                defaultNetworkCallback?.let { connectivity.unregisterNetworkCallback(it) }
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "StopCore-VPN: Failed to unregister callback", e)
            }
            defaultNetworkCallback = null
        }
        tun2SocksService?.stopTun2Socks()
        tun2SocksService = null
        RootLanSharing.stopClientSharing(this)

        val initiated = CoreServiceManager.stopCoreLoop(onStopped = {
            // Runs on the manager's IO shutdown scope. Closing this descriptor before
            // stopLoop returns can race native cleanup and a newly reused Android fd.
            try {
                if (::mInterface.isInitialized) mInterface.close()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StopCore-VPN: Failed to close interface", e)
            }
        }, onFinished = {
            if (networkRecovery.takeRecovery() && !destroyed.get()) {
                // Remain in the existing foreground VPN service: Android 12+ can
                // reject a new foreground-service start from a background callback.
                stopping.set(false)
                NotificationManager.showNotification(null)
                if (setupVpnService()) startService()
            } else {
                NotificationManager.cancelNotification()
                stopSelf()
            }
        }, keepForeground = networkRecovery.recoveryRequested())
        if (!initiated) {
            LogUtil.e(AppConfig.TAG, "StopCore-VPN: Shutdown unavailable, stopping service")
            NotificationManager.cancelNotification()
            try {
                if (::mInterface.isInitialized) mInterface.close()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StopCore-VPN: Failed to close interface after shutdown failure", e)
            }
            stopSelf()
        }
    }
}
