package com.v2ray.ang.ui.server

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AmneziaEditorUpgradeTest {
    @Test
    fun reopeningLegacyProfileUsesItsTunnelDnsAndPreservesAdvancedOptions() {
        val original = ProfileItem.create(EConfigType.WIREGUARD).apply {
            isAmneziaWG = true
            awgDns = "100.64.0.1"
            awgS4 = 12
            awgH1 = "10-20"
            awgKeepAliveRange = "25-35"
            allowedIPs = "0.0.0.0/0,::/0"
        }
        val state = ServerUiState.fromProfileItem(original)
        assertEquals("100.64.0.1", state.remoteDNS)
        state.remoteDNS = "9.9.9.9"
        val saved = state.toProfileItem(original)
        assertEquals("9.9.9.9", saved.awgDns)
        assertEquals("9.9.9.9", saved.remoteDNS)
        assertEquals(12, saved.awgS4)
        assertEquals("10-20", saved.awgH1)
        assertEquals("25-35", saved.awgKeepAliveRange)
        assertEquals(original.allowedIPs, saved.allowedIPs)
        assertTrue(saved.isAmneziaWG)
        assertEquals("100.64.0.1", original.awgDns)
    }
}
