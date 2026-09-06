package com.v2ray.ang.core

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoIpRuleResolverTest {
    @Test
    fun builtInRulesStayBuiltInWhenCompactDatabaseIsMissing() {
        assertEquals(
            listOf(AppConfig.GEOIP_CN, AppConfig.GEOIP_PRIVATE),
            GeoIpRuleResolver.resolve(
                listOf(AppConfig.GEOIP_CN, AppConfig.GEOIP_PRIVATE),
                compactDatabaseAvailable = false,
            ),
        )
    }

    @Test
    fun builtInRulesUseCompactDatabaseWhenAvailable() {
        assertEquals(
            listOf(
                "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:cn",
                "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:private",
            ),
            GeoIpRuleResolver.resolve(
                listOf(AppConfig.GEOIP_CN, AppConfig.GEOIP_PRIVATE),
                compactDatabaseAvailable = true,
            ),
        )
    }

    @Test
    fun customExtRulesFallBackToBundledGeoIp() {
        val json = JsonParser.parseString(
            """{"routing":{"rules":[{"ip":["ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:cn","1.1.1.1"]}]}}"""
        ).asJsonObject

        assertTrue(GeoIpRuleResolver.normalizeCustomRouting(json, false))
        assertEquals("geoip:cn", json["routing"].asJsonObject["rules"].asJsonArray[0]
            .asJsonObject["ip"].asJsonArray[0].asString)
        assertFalse(GeoIpRuleResolver.normalizeCustomRouting(json, false))
    }
}
