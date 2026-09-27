package com.v2ray.ang.core

import org.junit.Assert.*
import org.junit.Test

class CoreStopGateTest {
    @Test fun shutdownFailureDrainsOwnerCleanupAndReopensGate() {
        val gate = CoreStopGate()
        var closed = 0
        assertTrue(gate.begin { closed++ })
        assertFalse(gate.begin { closed++ })
        // Native stop can throw; owner cleanup and gate release still have to run.
        gate.drain().forEach { it() }
        assertEquals(2, closed)
        assertTrue(gate.finishIfDrained())
        assertFalse(gate.isStopping())
        assertTrue(gate.begin(null))
    }

    @Test fun duplicateStopDoesNotStartAnotherShutdownAndCleanupPrecedesRestart() {
        val gate = CoreStopGate()
        val events = mutableListOf<String>()
        assertFalse(gate.isStopping())
        assertTrue(gate.begin { assertTrue(gate.isStopping()); events.add("close-fd") })
        assertFalse(gate.begin { events.add("second-owner") })
        assertTrue(gate.isStopping())
        gate.drain().forEach { it() }
        assertEquals(listOf("close-fd", "second-owner"), events)
        assertTrue(gate.drain().isEmpty())
        assertFalse(gate.begin { events.add("late-owner") })
        assertFalse(gate.finishIfDrained())
        gate.drain().forEach { it() }
        assertEquals("late-owner", events.last())
        assertTrue(gate.finishIfDrained())
        assertFalse(gate.isStopping())
        assertTrue(gate.begin(null))
    }
}
