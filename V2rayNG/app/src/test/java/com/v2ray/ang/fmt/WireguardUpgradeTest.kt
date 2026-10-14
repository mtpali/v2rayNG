package com.v2ray.ang.fmt

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.ProfileBatchRenamer
import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WireguardUpgradeTest {
    @Test
    fun standardWireguardKeepsNewDnsAndFinalMaskAcrossSharing() {
        val profile = ProfileItem.create(EConfigType.WIREGUARD).apply {
            server = "peer.example"
            serverPort = "51820"
            secretKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
            publicKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
            localAddress = "10.0.0.2/32"
            remoteDNS = "9.9.9.9,1.1.1.1"
            finalMask = "{\"udp\":[]}"
            allowedIPs = "0.0.0.0/0,::/0"
            keepAlive = 25
        }
        val imported = requireNotNull(WireguardFmt.parse(WireguardFmt.exportUri(profile)))
        assertEquals(profile.remoteDNS, imported.remoteDNS)
        assertEquals(profile.finalMask, imported.finalMask)
        assertEquals(profile.allowedIPs, imported.allowedIPs)
        assertEquals(profile.keepAlive, imported.keepAlive)
        assertFalse(imported.isAmneziaWG)
    }

    @Test
    fun previousCustomProfileRetainsAmneziaFieldsDuringUpgradeAndRename() {
        val previous = """{"configType":"WIREGUARD","configVersion":3,"remarks":"Old","server":"peer.example","serverPort":"51820","isAmneziaWG":true,"awgS4":12,"awgH1":"10-20","awgKeepAliveRange":"25-35","awgDns":"100.64.0.1","allowedIPs":"0.0.0.0/0,::/0"}"""
        val profile = requireNotNull(JsonUtil.fromJsonSafe(previous, ProfileItem::class.java))
        val renamed = ProfileBatchRenamer.plan("VPN", listOf("stable-guid" to profile)).getValue("stable-guid")
        val restored = requireNotNull(JsonUtil.fromJsonSafe(JsonUtil.toJson(renamed), ProfileItem::class.java))
        assertEquals("VPN 1", restored.remarks)
        assertTrue(restored.isAmneziaWG)
        assertEquals(12, restored.awgS4)
        assertEquals("10-20", restored.awgH1)
        assertEquals("25-35", restored.awgKeepAliveRange)
        assertEquals("100.64.0.1", restored.awgDns)
        assertEquals(profile.allowedIPs, restored.allowedIPs)
        assertEquals("Old", profile.remarks)
    }

    @Test
    fun editingNewDnsFieldOverridesLegacyAmneziaDnsDuringSharing() {
        val profile = ProfileItem.create(EConfigType.WIREGUARD).apply {
            isAmneziaWG = true
            awgDns = "100.64.0.1"
            remoteDNS = "9.9.9.9"
            server = "peer.example"
            serverPort = "51820"
        }
        val imported = requireNotNull(WireguardFmt.parseAmneziaWG(WireguardFmt.exportUri(profile)))
        assertEquals("9.9.9.9", imported.awgDns)
        assertEquals("9.9.9.9", imported.remoteDNS)
    }

    @Test
    fun profilesWithDifferentAmneziaParametersRemainDistinct() {
        val first = ProfileItem.create(EConfigType.WIREGUARD).apply {
            isAmneziaWG = true
            awgS4 = 12
        }
        assertNotEquals(first.duplicateIdentity(), first.copy(awgS4 = 0).duplicateIdentity())
        assertEquals(first.duplicateIdentity(), first.copy(remarks = "Other name").duplicateIdentity())
    }
}
