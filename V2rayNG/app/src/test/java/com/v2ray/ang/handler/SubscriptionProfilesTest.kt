package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.*
import org.junit.Test

class SubscriptionProfilesTest {
    private fun profile(name: String, address: String = "peer.example") = ProfileItem(
        configType = EConfigType.VLESS, subscriptionId = "sub", remarks = name,
        server = address, serverPort = "443", password = "synthetic-user", addedTime = 100,
    )

    @Test fun reorderedAndRenamedProfilesKeepGuidsAndCreationTime() {
        val one = profile("one")
        val two = profile("two", "other.example")
        val old = linkedMapOf("guid-one" to SubscriptionProfiles.Entry(one), "guid-two" to SubscriptionProfiles.Entry(two))
        val incoming = listOf(two.copy(addedTime = 999), one.copy(remarks = "renamed", addedTime = 999))
            .map { SubscriptionProfiles.Entry(it) }
        val saved = SubscriptionProfiles.plan(old, incoming) { error("Unchanged profiles must retain their GUIDs") }
        assertEquals(listOf("guid-two", "guid-one"), saved.map { it.guid })
        assertTrue(saved.all { it.reused })
        assertEquals(100L, saved.last().entry.profile.addedTime)
        assertEquals("renamed", saved.last().entry.profile.remarks)
    }

    @Test fun securityChangesNeverBorrowAnOldProfilesTraffic() {
        val oldProfile = profile("one").copy(xhttpExtra = "old", insecure = false, mldsa65Verify = "old-key")
        val old = mapOf("old-guid" to SubscriptionProfiles.Entry(oldProfile))
        listOf(oldProfile.copy(xhttpExtra = "new"), oldProfile.copy(insecure = true),
            oldProfile.copy(mldsa65Verify = "new-key"), oldProfile.copy(configType = EConfigType.TROJAN))
            .forEach { changed ->
                val saved = SubscriptionProfiles.plan(old, listOf(SubscriptionProfiles.Entry(changed))) { "new-guid" }.single()
                assertEquals("new-guid", saved.guid)
                assertFalse(saved.reused)
            }
    }

    @Test fun amneziaParameterChangesCreateANewIdentity() {
        val oldProfile = profile("awg").copy(configType = EConfigType.WIREGUARD, isAmneziaWG = true, awgS4 = 8)
        val saved = SubscriptionProfiles.plan(mapOf("old" to SubscriptionProfiles.Entry(oldProfile)),
            listOf(SubscriptionProfiles.Entry(oldProfile.copy(awgS4 = 16)))) { "new" }
        assertEquals("new", saved.single().guid)
    }

    @Test fun duplicateConnectionsReuseEachOldGuidOnceAndPreferMatchingNames() {
        val one = profile("one")
        val two = one.copy(remarks = "two")
        val old = linkedMapOf("one" to SubscriptionProfiles.Entry(one), "two" to SubscriptionProfiles.Entry(two))
        val saved = SubscriptionProfiles.plan(old, listOf(two, one, one).map { SubscriptionProfiles.Entry(it) }) { "new" }
        assertEquals(listOf("two", "one", "new"), saved.map { it.guid })
    }

    @Test fun customJsonOrderAndNamesDoNotChangeIdentityButOutboundCredentialsDo() {
        val oldProfile = profile("custom").copy(configType = EConfigType.CUSTOM)
        val old = mapOf("custom-guid" to SubscriptionProfiles.Entry(oldProfile,
            """{"remarks":"old","outbounds":[{"protocol":"vless","password":"synthetic-a"}]}"""))
        val same = SubscriptionProfiles.Entry(oldProfile.copy(remarks = "new"),
            """{"outbounds":[{"password":"synthetic-a","protocol":"vless"}],"remarks":"new"}""")
        assertEquals("custom-guid", SubscriptionProfiles.plan(old, listOf(same)) { "new-guid" }.single().guid)
        val changed = same.copy(raw = """{"outbounds":[{"password":"synthetic-b","protocol":"vless"}]}""")
        assertEquals("new-guid", SubscriptionProfiles.plan(old, listOf(changed)) { "new-guid" }.single().guid)
    }

    @Test fun emptyReplacementAndNewImportsHaveNoReusedRecords() {
        assertTrue(SubscriptionProfiles.plan(mapOf("old" to SubscriptionProfiles.Entry(profile("old"))), emptyList()) { "new" }.isEmpty())
        val saved = SubscriptionProfiles.plan(emptyMap(), listOf(SubscriptionProfiles.Entry(profile("new")))) { "new" }.single()
        assertEquals("new", saved.guid)
        assertFalse(saved.reused)
    }
}
