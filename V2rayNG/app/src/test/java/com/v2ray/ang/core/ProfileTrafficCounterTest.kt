package com.v2ray.ang.core

import com.google.gson.JsonParser
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.entities.ProfileTraffic
import org.junit.Assert.*
import org.junit.Test

class ProfileTrafficCounterTest {
    @Test fun customProfilesGetCountersInEveryModeWithoutLosingPolicy() {
        val content = """{"policy":{"levels":{"0":{"handshake":9}},"system":{"statsInboundUplink":true}},"outbounds":[{"protocol":"vless","tag":"custom"}]}"""
        val json = JsonParser.parseString(ProfileTrafficCounter.prepare(content)).asJsonObject
        assertTrue(json.has("stats"))
        assertTrue(json.getAsJsonObject("policy").getAsJsonObject("system")["statsOutboundDownlink"].asBoolean)
        assertTrue(json.getAsJsonObject("policy").getAsJsonObject("system")["statsInboundUplink"].asBoolean)
        assertEquals(9, json.getAsJsonObject("policy").getAsJsonObject("levels").getAsJsonObject("0")["handshake"].asInt)
    }
    @Test fun untaggedCustomOutboundsGetUniqueTags() {
        val prepared = ProfileTrafficCounter.prepare("""{"policy":null,"stats":null,"outbounds":[{"protocol":"wireguard"},{"protocol":"vless","tag":"usage-outbound-0"}]}""")
        assertEquals(setOf("usage-outbound-0-", "usage-outbound-0"), ProfileTrafficCounter.tags(prepared))
    }
    @Test fun balancerMembersAreCountedAndDirectIsExcluded() {
        val content = """{"outbounds":[{"protocol":"blackhole","tag":"proxy"},{"protocol":"vless","tag":"proxy-proxy-a-0"},{"protocol":"wireguard","tag":"proxy-proxy-b-1"},{"protocol":"freedom","tag":"direct"},{"protocol":"dns","tag":"dns"}]}"""
        val tags = ProfileTrafficCounter.tags(content)
        assertEquals(ProfileTraffic(30, 90), ProfileTrafficCounter.total(listOf(
            OutboundTrafficStat("proxy-proxy-a-0", "uplink", 10), OutboundTrafficStat("proxy-proxy-b-1", "uplink", 20),
            OutboundTrafficStat("proxy-proxy-b-1", "downlink", 90), OutboundTrafficStat("direct", "uplink", 1000)), tags))
    }
    @Test fun chainedOutboundsAreNotCountedTwice() {
        val content = """{"outbounds":[{"protocol":"vless","tag":"proxy","streamSettings":{"sockopt":{"dialerProxy":"hop"}}},{"protocol":"socks","tag":"hop","proxySettings":{"tag":"last"}},{"protocol":"vless","tag":"last"}]}"""
        assertEquals(setOf("proxy"), ProfileTrafficCounter.tags(content))
    }
    @Test fun nullOptionalSectionsAreAccepted() {
        assertEquals(setOf("custom"), ProfileTrafficCounter.tags("""{"outbounds":[{"tag":"custom","protocol":"trojan","proxySettings":null,"streamSettings":null}]}"""))
    }
    @Test fun overflowAndMalformedDirectionsCannotCorruptTotals() {
        val stats = listOf(OutboundTrafficStat("proxy", "uplink", Long.MAX_VALUE), OutboundTrafficStat("proxy", "uplink", 50),
            OutboundTrafficStat("proxy", "downlink", -99), OutboundTrafficStat("proxy", "invalid", 999))
        assertEquals(ProfileTraffic(Long.MAX_VALUE, 0), ProfileTrafficCounter.total(stats, setOf("proxy")))
    }
}
