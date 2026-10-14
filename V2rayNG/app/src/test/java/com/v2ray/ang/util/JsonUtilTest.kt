package com.v2ray.ang.util

import com.google.gson.JsonParser
import com.google.gson.JsonParseException
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonUtilTest {

    @Test
    fun legacyProfileUsesDefaultsForAbsentFieldsAndPreservesStoredValues() {
        val profile = requireNotNull(JsonUtil.fromJson(
            """{"configType":"WIREGUARD","configVersion":3,"remarks":"Legacy","isAmneziaWG":true,"awgS4":12,"awgDns":"100.64.0.1"}""",
            ProfileItem::class.java,
        ))
        assertEquals(EConfigType.WIREGUARD, profile.configType)
        assertEquals(3, profile.configVersion)
        assertEquals("", profile.subscriptionId)
        assertTrue(profile.addedTime > 0)
        assertEquals(12, profile.awgS4)
        assertEquals("100.64.0.1", profile.awgDns)
        assertFalse(profile.awgDisableCookieReplies)
        assertEquals("Renamed", profile.copy(remarks = "Renamed").remarks)
        assertEquals("Legacy", profile.remarks)
    }

    @Test
    fun absentOrUnknownProfileTypeIsRejected() {
        for (json in listOf("{}", """{"configType":"UNKNOWN"}""")) {
            assertThrows(JsonParseException::class.java) {
                JsonUtil.fromJson(json, ProfileItem::class.java)
            }
        }
    }

    @Test
    fun toJsonPrettyKeepsNumbersOfConfigArrayElements() {
        val configs = JsonUtil.fromJson(
            """[{"port":443,"tolerance":0.4,"offset":-0.5,"timestamp":1700000000000,"name":"0.4"}]""",
            Array<Any>::class.java
        )

        val output = JsonParser.parseString(JsonUtil.toJsonPretty(configs!![0])).asJsonObject

        assertEquals("443", output["port"].asString)
        assertEquals("0.4", output["tolerance"].asString)
        assertEquals("-0.5", output["offset"].asString)
        assertEquals("1700000000000", output["timestamp"].asString)
        assertEquals("0.4", output["name"].asString)
    }
}
