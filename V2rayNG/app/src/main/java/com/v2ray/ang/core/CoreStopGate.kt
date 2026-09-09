package com.v2ray.ang.core

/** Pure stop-ordering decisions; CoreServiceManager owns the operation and scope. */
internal class CoreStopGate {
    private var stopping = false
    private val callbacks = mutableListOf<() -> Unit>()

    @Synchronized fun isStopping(): Boolean = stopping

    @Synchronized fun begin(callback: (() -> Unit)?): Boolean {
        if (callback != null) callbacks.add(callback)
        if (stopping) return false
        stopping = true
        return true
    }

    @Synchronized fun drain(): List<() -> Unit> = callbacks.toList().also { callbacks.clear() }
    @Synchronized fun finishIfDrained(): Boolean {
        if (callbacks.isNotEmpty()) return false
        stopping = false
        return true
    }
}
