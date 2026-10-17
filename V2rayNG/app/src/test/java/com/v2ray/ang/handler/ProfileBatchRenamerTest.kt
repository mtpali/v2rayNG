package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBatchRenamerTest {
    @Test
    fun assignsStableOneBasedNamesInGroupOrder() {
        val source = listOf(
            "first" to profile("Old A"),
            "second" to profile("Old B"),
            "third" to profile("Old C"),
        )

        val result = ProfileBatchRenamer.plan("  v2rayNG  ", source)

        assertEquals(listOf("first", "second", "third"), result.keys.toList())
        assertEquals(
            listOf("v2rayNG 1", "v2rayNG 2", "v2rayNG 3"),
            result.values.map { it.remarks },
        )
        assertEquals(listOf("Old A", "Old B", "Old C"), source.map { it.second.remarks })
    }

    @Test
    fun emptyAndWhitespacePrefixesNumberProfilesWithoutLeadingSpaces() {
        val source = listOf("first" to profile("Old A"), "second" to profile("Old B"))

        listOf("", "   ", "\t\n").forEach { prefix ->
            val result = ProfileBatchRenamer.plan(prefix, source)
            assertEquals(listOf("first", "second"), result.keys.toList())
            assertEquals(listOf("1", "2"), result.values.map { it.remarks })
        }
        assertEquals(listOf("Old A", "Old B"), source.map { it.second.remarks })
    }

    @Test
    fun emptyGroupProducesNoWritesWithOrWithoutAPrefix() {
        assertTrue(ProfileBatchRenamer.plan("", emptyList()).isEmpty())
        assertTrue(ProfileBatchRenamer.plan("Name", emptyList()).isEmpty())
    }

    private fun profile(name: String) = ProfileItem.create(EConfigType.VMESS).apply {
        remarks = name
    }
}
