package com.v2ray.ang.service

import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Dedicated thread: a stuck JNI call or shutdown lock cannot suppress this timeout. */
internal object DaemonStopWatchdog {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "VPN shutdown watchdog").apply { isDaemon = true }
    }
    private var timeout: ScheduledFuture<*>? = null

    @Synchronized fun arm() {
        if (timeout != null) return
        timeout = executor.schedule({ Process.killProcess(Process.myPid()) }, 4, TimeUnit.SECONDS)
    }

    @Synchronized fun disarm() {
        timeout?.cancel(false)
        timeout = null
    }
}
