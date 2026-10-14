package com.v2ray.ang.handler

import org.junit.Assert.*
import org.junit.Test

class SubscriptionOrderTest {
    private val ids = (1..6).map { "group-$it" }
    @Test fun sixthGroupMovesToFirstWithoutSwappingAwayOtherPositions() {
        assertEquals(listOf("group-6") + ids.take(5), SubscriptionOrder.move(ids, "group-6", "group-1"))
    }
    @Test fun firstMovesToLastAndOrderSurvivesReload() {
        val stored = SubscriptionOrder.move(ids, "group-1", "group-6")
        assertEquals(ids.drop(1) + "group-1", stored)
        assertEquals(ids, SubscriptionOrder.move(stored, "group-1", "group-2"))
    }
    @Test fun concurrentDeletionAndAllTabAreNoOps() {
        assertEquals(ids, SubscriptionOrder.move(ids, "removed", "group-1"))
        assertEquals(ids, SubscriptionOrder.move(ids, "group-6", "removed"))
        assertEquals(ids, SubscriptionOrder.move(ids, "", "group-1"))
    }
    @Test fun currentOrderResolvesStableIdentityAfterAnotherMove() {
        val current = SubscriptionOrder.move(ids, "group-3", "group-6")
        assertEquals(listOf("group-6", "group-1", "group-2", "group-4", "group-5", "group-3"),
            SubscriptionOrder.move(current, "group-6", "group-1"))
    }
}
