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
}
