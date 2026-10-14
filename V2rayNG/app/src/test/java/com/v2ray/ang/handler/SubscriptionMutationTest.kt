package com.v2ray.ang.handler

import org.junit.Assert.*
import org.junit.Test

class SubscriptionMutationTest {
    @Test fun failedDeletionRestoresAllRecordsIncludingFailingWrite() {
        val records = mutableMapOf("subscription" to "name", "servers" to "guid", "traffic" to "bytes", "selected" to "guid")
        val before = records.toMap()
        var failOnce = true
        try {
            SubscriptionMutation.apply(linkedMapOf("subscription" to null, "servers" to null, "traffic" to null, "selected" to null),
                { records[it] }, { key, value ->
                    if (value == null) records.remove(key) else records[key] = value
                    if (key == "traffic" && failOnce) { failOnce = false; false } else true
                }, { fail("Rollback failed") })
            fail("Expected write failure")
        } catch (_: IllegalStateException) { }
        assertEquals(before, records)
    }
    @Test fun exceptionAlsoRestoresNewDefaultAndSelectedProfile() {
        val records = mutableMapOf("selected" to "server")
        var failOnce = true
        try {
            SubscriptionMutation.apply(linkedMapOf("default" to "new", "selected" to null), { records[it] }, { key, value ->
                if (key == "selected" && failOnce) { failOnce = false; throw IllegalArgumentException("disk") }
                if (value == null) records.remove(key) else records[key] = value
                true
            }, { fail("Rollback failed") })
            fail("Expected exception")
        } catch (_: IllegalArgumentException) { }
        assertEquals(mapOf("selected" to "server"), records)
    }
}
