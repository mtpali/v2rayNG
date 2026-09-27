package com.v2ray.ang.service

/** Tracks physical-network handovers and sleep/wake recovery for one VPN service instance. */
internal class VpnNetworkRecoveryGate {
    companion object {
        // Android can resume the same underlying network after Doze without a
        // handover callback. Remove this fallback once the native AWG transport
        // can confirm a fresh handshake or rebind its protected UDP socket on wake.
        private const val LONG_IDLE_MS = 3 * 60 * 1000L
    }

    private var lastNetwork: Long? = null
    private var screenOffAt: Long? = null
    private var lost = false
    private var restartRequested = false
    private var blocked = false
    private var stopped = false

    @Synchronized fun onAvailable(network: Long, canRestart: Boolean): Boolean {
        if (stopped) return false
        val changed = lastNetwork != null && (lastNetwork != network || lost)
        if (changed) blocked = false
        lastNetwork = network
        lost = false
        if (!changed || !canRestart || restartRequested) return false
        restartRequested = true
        return true
    }

    @Synchronized fun isCurrent(network: Long): Boolean = !stopped && lastNetwork == network

    @Synchronized fun onLost(network: Long): Boolean {
        if (stopped || lastNetwork != network) return false
        lost = true
        return true
    }

    @Synchronized fun onBlockedStatus(network: Long, isBlocked: Boolean, canRestart: Boolean): Boolean {
        if (stopped || lastNetwork != network) return false
        val becameAvailable = blocked && !isBlocked
        blocked = isBlocked
        if (!becameAvailable || !canRestart || restartRequested) return false
        restartRequested = true
        return true
    }

    @Synchronized fun onScreenOff(elapsedRealtime: Long) {
        if (!stopped && screenOffAt == null) screenOffAt = elapsedRealtime
    }

    @Synchronized fun onUserPresent(elapsedRealtime: Long, canRestart: Boolean): Boolean {
        val offAt = screenOffAt ?: return false
        screenOffAt = null
        if (stopped || restartRequested || !canRestart || elapsedRealtime - offAt < LONG_IDLE_MS) return false
        restartRequested = true
        return true
    }

    @Synchronized fun isActive(): Boolean = !stopped

    @Synchronized fun recoveryRequested(): Boolean = restartRequested

    @Synchronized fun cancelRecovery() {
        restartRequested = false
    }

    @Synchronized fun takeRecovery(): Boolean = restartRequested.also { restartRequested = false }

    @Synchronized fun stop() {
        stopped = true
    }
}
