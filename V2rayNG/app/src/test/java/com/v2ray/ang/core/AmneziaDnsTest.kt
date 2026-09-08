package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import org.junit.Assert.*
import org.junit.Test

class AmneziaDnsTest {
    @Test
    fun routesPrivateResolverBeforeBypassRules() {
        val config = V2rayConfig(
            log = V2rayConfig.LogBean(), inbounds = arrayListOf(),
            outbounds = arrayListOf(V2rayConfig.OutboundBean(protocol = "wireguard", settings =
                V2rayConfig.OutboundBean.OutSettingsBean(dnsServers = listOf("100.64.0.1", "8.8.4.4"),
                    amnezia = V2rayConfig.OutboundBean.OutSettingsBean.AmneziaWGOptionsBean()))),
            routing = V2rayConfig.RoutingBean("AsIs", rules = arrayListOf(
                V2rayConfig.RoutingBean.RulesBean(ip = listOf("geoip:private"), outboundTag = "direct"))),
        )
        CoreConfigManager.applyAmneziaProfileDns(config)
        assertEquals(listOf("100.64.0.1", "8.8.4.4"), config.dns?.servers)
        assertEquals("dns-out", config.routing.rules[0].outboundTag)
        assertEquals(listOf(AppConfig.TAG_DNS), config.routing.rules[1].inboundTag)
        assertEquals("proxy", config.routing.rules[1].outboundTag)
        assertEquals("direct", config.routing.rules[2].outboundTag)
        assertEquals(1, config.outbounds.count { it.protocol == "dns" })
    }

    @Test
    fun leavesProfilesWithoutExplicitAmneziaDnsUntouched() {
        val config = V2rayConfig(log = V2rayConfig.LogBean(), inbounds = arrayListOf(),
            outbounds = arrayListOf(V2rayConfig.OutboundBean(protocol = "wireguard")),
            routing = V2rayConfig.RoutingBean("AsIs", rules = arrayListOf()))
        CoreConfigManager.applyAmneziaProfileDns(config)
        assertNull(config.dns)
        assertTrue(config.routing.rules.isEmpty())
    }
}
