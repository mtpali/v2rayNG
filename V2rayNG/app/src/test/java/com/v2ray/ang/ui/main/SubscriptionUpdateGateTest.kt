package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionUpdateGateTest {
    @Test
    fun ignoresDuplicateRefreshUntilActiveRefreshFinishes() {
        val gate = SubscriptionUpdateGate()

        assertTrue(gate.tryBegin())
        assertFalse(gate.tryBegin())

        gate.finish()

        assertTrue(gate.tryBegin())
    }
}
