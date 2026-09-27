package com.v2ray.ang.service

import org.junit.Assert.*
import org.junit.Test

class VpnNetworkRecoveryGateTest {
    @Test fun firstNetworkAndDuplicateCallbacksDoNotRestart() {
        val gate = VpnNetworkRecoveryGate()
        assertFalse(gate.onAvailable(1L, true))
        assertFalse(gate.onAvailable(1L, true))
        assertTrue(gate.isCurrent(1L))
    }

    @Test fun oneAmneziaHandoverRestartsAndStopInvalidatesLateCallbacks() {
        val gate = VpnNetworkRecoveryGate()
        assertFalse(gate.onAvailable(1L, true))
        assertTrue(gate.onAvailable(2L, true))
        assertTrue(gate.recoveryRequested())
        assertFalse(gate.isCurrent(1L))
        assertFalse(gate.onAvailable(3L, true))
        gate.stop()
        assertFalse(gate.onAvailable(4L, true))
        assertFalse(gate.isCurrent(3L))
        assertTrue(gate.takeRecovery())
        assertFalse(gate.takeRecovery())
    }

    @Test fun otherProtocolsAndChangesBeforeStartupDoNotRestart() {
        val gate = VpnNetworkRecoveryGate()
        assertFalse(gate.onAvailable(1L, false))
        assertFalse(gate.onAvailable(2L, false))
        assertTrue(gate.onAvailable(3L, true))
        gate.cancelRecovery()
        assertFalse(gate.takeRecovery())
    }

    @Test fun onlyTransitionFromBlockedToAvailableCanRecoverOnSameNetwork() {
        val gate = VpnNetworkRecoveryGate()
        assertFalse(gate.onAvailable(1L, true))
        assertFalse(gate.onBlockedStatus(1L, false, true))
        assertFalse(gate.onBlockedStatus(1L, true, true))
        assertFalse(gate.onBlockedStatus(2L, false, true))
        assertTrue(gate.onBlockedStatus(1L, false, true))
        assertFalse(gate.onBlockedStatus(1L, false, true))
        gate.cancelRecovery()
        gate.stop()
        assertFalse(gate.onBlockedStatus(1L, false, true))
    }

    @Test fun losingAndRegainingTheSameNetworkReopensTheTransport() {
        val gate = VpnNetworkRecoveryGate()
        assertFalse(gate.onAvailable(1L, true))
        assertFalse(gate.onLost(2L))
        assertTrue(gate.onLost(1L))
        assertTrue(gate.onAvailable(1L, true))
    }

    @Test fun longSleepRecoversAmneziaEvenWhenTheUnderlyingNetworkNeverChanges() {
        val gate = VpnNetworkRecoveryGate()
        assertFalse(gate.onAvailable(7L, true))
        gate.onScreenOff(1_000L)
        gate.onScreenOff(30_000L) // Duplicate broadcast must not reset the timer.
        assertTrue(gate.onUserPresent(301_000L, true))
        assertFalse(gate.onUserPresent(301_001L, true))
        assertFalse(gate.onAvailable(7L, true))
        assertTrue(gate.takeRecovery())
    }

    @Test fun wakeRecoverySkipsShortSleepsAndOtherProtocols() {
        val gate = VpnNetworkRecoveryGate()
        gate.onScreenOff(1_000L)
        assertFalse(gate.onUserPresent(180_999L, true))
        gate.onScreenOff(200_000L)
        assertFalse(gate.onUserPresent(500_000L, false))
        gate.onScreenOff(510_000L)
        assertTrue(gate.onUserPresent(690_000L, true))
    }

    @Test fun stopRacingWithWakeCancelsPendingRecovery() {
        val gate = VpnNetworkRecoveryGate()
        gate.onScreenOff(0L)
        assertTrue(gate.onUserPresent(300_000L, true))
        gate.cancelRecovery()
        gate.stop()
        assertFalse(gate.recoveryRequested())
        assertFalse(gate.onUserPresent(600_000L, true))
        gate.onScreenOff(601_000L)
        assertFalse(gate.onUserPresent(900_000L, true))
    }

    @Test fun sleepDuringPendingWakeCanRetryOnTheNextUnlock() {
        val gate = VpnNetworkRecoveryGate()
        gate.onScreenOff(0L)
        assertTrue(gate.onUserPresent(300_000L, true))
        gate.onScreenOff(301_000L)
        gate.cancelRecovery()
        assertTrue(gate.onUserPresent(601_000L, true))
    }
}
