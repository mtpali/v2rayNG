package com.v2ray.ang.service

/** Owns callback validity for a test-service instance, independently of native cancellation. */
internal class RealPingBatchState {
    private var generation = 0L
    private var closed = false

    @Synchronized
    fun next(): Long = ++generation

    @Synchronized
    fun accepts(token: Long): Boolean = !closed && token == generation

    @Synchronized
    fun close() {
        closed = true
        generation++
    }
}
