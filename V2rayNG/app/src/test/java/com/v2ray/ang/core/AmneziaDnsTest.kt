package com.v2ray.ang.core

import com.v2ray.ang.fmt.WireguardFmt
import org.junit.Assert.*
import org.junit.Test

class AmneziaDnsTest {
    private fun profile(dns: String) = WireguardFmt.parseWireguardConfFile("""
        [Interface]
        PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        Address = 172.16.0.2/32, fd00::2/128
        $dns
        Jc = 4
        Jmin = 40
        Jmax = 70
        S1 = 0
        S2 = 0
        S3 = 0
        S4 = 0
        H1 = 1
        H2 = 2
        H3 = 3
        H4 = 4
        I1 = <b 0x01020304>
        [Peer]
        PublicKey = BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
        Endpoint = peer.example:2408
        AllowedIPs = 0.0.0.0/0, ::/0
    """.trimIndent())

    @Test fun preservesZeroPaddingAndTunnelDnsAcrossIniAndUri() {
        for (dns in listOf("DNS = 1.1.1.1", "")) {
            val ini = profile(dns)
            val uri = requireNotNull(WireguardFmt.parseAmneziaWG(WireguardFmt.exportUri(ini)))
            for (source in listOf(ini, uri)) {
                val outbound = requireNotNull(CoreOutboundBuilder.toOutboundWireguard(source, true))
                val settings = requireNotNull(outbound.settings)
                assertEquals(if (dns.isEmpty()) null else listOf("1.1.1.1"), settings.dnsServers)
                assertEquals(listOf("172.16.0.2/32", "fd00::2/128"), settings.address)
                assertEquals("peer.example:2408", settings.peers!!.first().endpoint)
                assertEquals(listOf("0.0.0.0/0", "::/0"), settings.peers!!.first().allowedIPs)
                assertEquals(0, settings.amnezia!!.s4)
                assertEquals("4", settings.amnezia!!.h4)
            }
        }
    }

    @Test fun retainsOnlyIpv4AddressWhenIpv6Disabled() {
        val settings = CoreOutboundBuilder.toOutboundWireguard(profile(""), false)!!.settings!!
        assertEquals(listOf("172.16.0.2/32"), settings.address)
        assertNull(settings.dnsServers)
    }

    @Test fun idleKeepaliveAppliesOnlyToAmneziaWithoutAnExplicitValue() {
        val awg = profile("")
        val peer = { CoreOutboundBuilder.toOutboundWireguard(awg, true)!!.settings!!.peers!!.first() }
        assertEquals(25, peer().keepAlive)

        awg.keepAlive = 45
        assertEquals(45, peer().keepAlive)

        awg.keepAlive = 0
        assertEquals(0, peer().keepAlive)

        awg.keepAlive = null
        awg.awgKeepAliveRange = "25-35"
        assertNull(peer().keepAlive)
        assertEquals("25-35", peer().keepAliveRange)

        awg.awgKeepAliveRange = null
        awg.isAmneziaWG = false
        assertNull(peer().keepAlive)
    }
}
