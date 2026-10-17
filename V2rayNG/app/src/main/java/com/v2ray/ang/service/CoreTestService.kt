package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** User-requested real-ping runs as a normal service, as in MobileTinaVPN. */
class CoreTestService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Repeated START commands must await the same initialization, not skip an
    // initialization still running on another worker thread.
    private val coreReady = scope.async(start = CoroutineStart.LAZY) {
        CoreNativeManager.initCoreEnv(this@CoreTestService)
    }
    private val batches = RealPingBatchState()
    private var worker: RealPingWorkerService? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.serializable<TestServiceMessage>("content")
        val token = invalidateBatch()
        if (message?.key != AppConfig.MSG_MEASURE_CONFIG_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                coreReady.await()
                val guids = when {
                    message.serverGuids.isNotEmpty() -> message.serverGuids
                    message.subscriptionId.isNotEmpty() -> MmkvManager.decodeServerList(message.subscriptionId)
                    else -> MmkvManager.decodeAllServerList()
                }
                synchronized(this@CoreTestService) {
                    if (!batches.accepts(token)) return@synchronized
                    if (guids.isEmpty()) {
                        stopSelf(startId)
                    } else {
                        worker = RealPingWorkerService(this@CoreTestService, guids) { event ->
                            handleEvent(token, startId, event)
                        }.also { it.start() }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Real-ping setup failed: group=${message.subscriptionId}", e)
                synchronized(this@CoreTestService) {
                    if (batches.accepts(token)) stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun invalidateBatch(): Long {
        val token = batches.next()
        worker?.cancel()
        worker = null
        return token
    }

    @Synchronized
    private fun handleEvent(token: Long, startId: Int, event: RealPingEvent) {
        // Native tests may return after cancellation; ignore their results and stop requests.
        if (!batches.accepts(token)) return
        when (event) {
            is RealPingEvent.Progress ->
                MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, event.text)
            is RealPingEvent.Result -> {
                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_SUCCESS, event.guid)
            }
            is RealPingEvent.Finish -> {
                worker = null
                MessageUtil.sendMsg2UI(this, AppConfig.MSG_MEASURE_CONFIG_FINISH, event.status)
                stopSelf(startId)
            }
        }
    }

    @Synchronized
    override fun onDestroy() {
        batches.close()
        invalidateBatch()
        scope.cancel()
        super.onDestroy()
    }
}
