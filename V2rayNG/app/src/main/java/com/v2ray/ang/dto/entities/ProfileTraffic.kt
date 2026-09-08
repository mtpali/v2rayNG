package com.v2ray.ang.dto.entities

// Separate from latency metadata: only the daemon adds traffic, while tests update latency.
data class ProfileTraffic(val upload: Long = 0, val download: Long = 0) {
    fun add(up: Long, down: Long) = ProfileTraffic(sum(upload, up), sum(download, down))
    private fun sum(value: Long, delta: Long): Long =
        value + delta.coerceAtLeast(0).coerceAtMost(Long.MAX_VALUE - value)
}

data class TrafficSnapshot(
    val generation: String = "initial",
    val profiles: MutableMap<String, ProfileTraffic> = mutableMapOf(),
)
