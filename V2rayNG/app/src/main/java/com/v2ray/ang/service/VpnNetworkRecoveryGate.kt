package com.v2ray.ang.service

/** Tracks physical-network handovers for one VPN service instance. */
internal class VpnNetworkRecoveryGate {
    private var lastNetwork: Long? = null
    private var restartRequested = false
    private var blocked = false
    private var stopped = false

    @Synchronized fun onAvailable(network: Long, canRestart: Boolean): Boolean {
        if (stopped) return false
        val changed = lastNetwork != null && lastNetwork != network
        if (changed) blocked = false
        lastNetwork = network
        if (!changed || !canRestart || restartRequested) return false
        restartRequested = true
        return true
    }

    @Synchronized fun isCurrent(network: Long): Boolean = !stopped && lastNetwork == network

    @Synchronized fun onBlockedStatus(network: Long, isBlocked: Boolean, canRestart: Boolean): Boolean {
        if (stopped || lastNetwork != network) return false
        val becameAvailable = blocked && !isBlocked
        blocked = isBlocked
        if (!becameAvailable || !canRestart || restartRequested) return false
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
