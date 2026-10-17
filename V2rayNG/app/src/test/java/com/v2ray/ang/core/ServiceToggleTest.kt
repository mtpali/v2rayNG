package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceToggleTest {
    @Test fun runningDaemonRequestsStop() {
        assertEquals(ServiceToggle.Action.STOP, action(AppConfig.MSG_STATE_RUNNING))
    }

    @Test fun stoppedOrAbsentReceiverRequestsStartEvenIfItsProcessWasCached() {
        assertEquals(ServiceToggle.Action.START, action(AppConfig.MSG_STATE_NOT_RUNNING))
        assertEquals(ServiceToggle.Action.START, action(0))
    }

    @Test fun unresponsiveDaemonRequestsTheBoundedStopInsteadOfAnotherStart() {
        assertEquals(ServiceToggle.Action.STOP, action(null))
        assertEquals(ServiceToggle.Action.STOP, action(-1))
    }

    @Test fun pendingStartupCanBeCancelledEvenBeforeTheDaemonRegisters() {
        assertEquals(ServiceToggle.Action.STOP, ServiceToggle.action(0, starting = true, stopping = false))
        assertEquals(ServiceToggle.Action.STOP, action(AppConfig.MSG_STATE_STARTING))
    }

    @Test fun recoveryShutdownStillAcceptsExplicitStop() {
        assertEquals(ServiceToggle.Action.STOP, action(AppConfig.MSG_STATE_STOPPING))
    }

    @Test fun repeatedToggleDoesNotStartAgainWhileExplicitStopIsPending() {
        listOf(null, 0, AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_RUNNING).forEach {
            assertEquals(ServiceToggle.Action.NONE, ServiceToggle.action(it, starting = false, stopping = true))
        }
    }

    private fun action(reply: Int?) = ServiceToggle.action(reply, starting = false, stopping = false)
}
