package com.v2ray.ang.root

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class RootProcessTimeoutTest {
    @Test fun alreadyExitedProcessNeedsNoDelay() {
        val process = mock(Process::class.java)
        `when`(process.exitValue()).thenReturn(0)
        assertTrue(RootShell.awaitExit(process, 0, { 0 }) { fail("Exited process must not wait") })
    }

    @Test fun runningProcessCanExitBeforeDeadline() {
        val process = mock(Process::class.java)
        `when`(process.exitValue()).thenThrow(IllegalThreadStateException()).thenReturn(0)
        var time = 0L
        var pauses = 0
        assertTrue(RootShell.awaitExit(process, 100, { time }) { time += 20; pauses++ })
        assertEquals(1, pauses)
    }

    @Test fun stuckProcessStopsWaitingAtMonotonicDeadline() {
        val process = mock(Process::class.java)
        `when`(process.exitValue()).thenThrow(IllegalThreadStateException())
        var time = 0L
        assertFalse(RootShell.awaitExit(process, 100, { time }) { time += 25 })
        assertEquals(100L, time)
    }
}
