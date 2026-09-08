package com.v2ray.ang.core

import com.google.gson.Gson
import com.v2ray.ang.dto.entities.ProfileTraffic
import com.v2ray.ang.dto.entities.TrafficSnapshot
import org.junit.Assert.*
import org.junit.Test

class ProfileTrafficTest {
    @Test fun accumulatesBothDirectionsAndIgnoresNegativeDeltas() {
        assertEquals(ProfileTraffic(12, 24), ProfileTraffic(5, 9).add(7, 15))
        assertEquals(ProfileTraffic(5, 9), ProfileTraffic(5, 9).add(-1, -2))
        assertEquals(Long.MAX_VALUE, ProfileTraffic(Long.MAX_VALUE - 1, 0).add(9, 0).upload)
    }
    @Test fun retainsServerIdentityAcrossStorageAndReset() {
        val original = TrafficSnapshot(profiles = mutableMapOf("guid-b" to ProfileTraffic(2, 8), "guid-a" to ProfileTraffic(4, 6)))
        val restored = Gson().fromJson(Gson().toJson(original), TrafficSnapshot::class.java)
        assertEquals(ProfileTraffic(2, 8), restored.profiles["guid-b"])
        assertEquals(ProfileTraffic(4, 6), restored.profiles["guid-a"])
        val reset = TrafficSnapshot("new-generation")
        assertNotEquals(restored.generation, reset.generation)
        assertTrue(reset.profiles.isEmpty())
    }
}
