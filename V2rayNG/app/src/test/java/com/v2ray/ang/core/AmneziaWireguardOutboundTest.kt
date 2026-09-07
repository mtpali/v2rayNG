package com.v2ray.ang.core

import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.util.JsonUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmneziaWireguardOutboundTest {

    @Test
    fun mapsRealWorldLongInitPacketsToUserspaceCoreConfig() {
        val i1 = byteChain(348)
        val i2 = byteChain(245)
        val profile = WireguardFmt.parseWireguardConfFile(
            """
                [Interface]
                PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
                Address = 10.187.192.50/30
                MTU = 1280
                Jc = 4
                Jmin = 40
                Jmax = 70
                H1 = 1
                H2 = 2
                H3 = 3
                H4 = 4
                I1 = $i1
                I2 = $i2

                [Peer]
                PublicKey = BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
                Endpoint = 192.0.2.1:51820
                AllowedIPs = 0.0.0.0/0, ::/0
                PersistentKeepalive = 1500
            """.trimIndent(),
        )

        val outbound = requireNotNull(
            CoreOutboundBuilder.toOutboundWireguard(profile, ipv6Enabled = false),
        )
        val settings = requireNotNull(outbound.settings)
        val peer = requireNotNull(settings.peers?.single())
        val amnezia = requireNotNull(settings.amnezia)

        assertEquals(listOf("10.187.192.50/30"), settings.address)
        assertEquals("ForceIPv4", settings.domainStrategy)
        assertEquals(true, settings.noKernelTun)
        assertEquals(1500, peer.keepAlive)
        assertEquals(listOf("0.0.0.0/0", "::/0"), peer.allowedIPs)
        assertEquals(i1, amnezia.i1)
        assertEquals(i2, amnezia.i2)

        val json = JsonUtil.toJson(outbound)
        assertTrue(json.contains("\"noKernelTun\":true"))
        assertTrue(json.contains("\"keepAlive\":1500"))
        assertTrue(json.contains("\"i1\":\"$i1\""))
        assertFalse(json.contains("\"reserved\""))
    }

    private fun byteChain(byteCount: Int): String =
        "<b 0x${"ab".repeat(byteCount)}>"
}
