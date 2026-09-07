package com.v2ray.ang.viewmodel

import java.util.concurrent.atomic.AtomicBoolean

internal class SubscriptionUpdateGate {
    private val updating = AtomicBoolean(false)

    fun tryBegin(): Boolean = updating.compareAndSet(false, true)

    fun finish() {
        updating.set(false)
    }
}
