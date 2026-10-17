package com.v2ray.ang.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
