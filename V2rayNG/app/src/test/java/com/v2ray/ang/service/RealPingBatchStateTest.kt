package com.v2ray.ang.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealPingBatchStateTest {
    @Test
    fun newerStartOrCancelRejectsLateNativeResults() {
        val state = RealPingBatchState()
        val first = state.next()
        assertTrue(state.accepts(first))
        val second = state.next()
        assertFalse(state.accepts(first))
        assertTrue(state.accepts(second))
        state.next() // Cancel invalidates the active token before native work returns.
        assertFalse(state.accepts(second))
    }

    @Test
    fun destroyRejectsPendingSetupAndRepeatedStopIsSafe() {
        val state = RealPingBatchState()
        val setup = state.next()
        state.close()
        state.close()
        assertFalse(state.accepts(setup))
        assertFalse(state.accepts(state.next()))
    }
}
