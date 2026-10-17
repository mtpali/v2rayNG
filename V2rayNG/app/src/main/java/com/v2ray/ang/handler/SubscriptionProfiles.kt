package com.v2ray.ang.handler

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil

/** Reconciles a refresh by connection identity, keeping each profile's GUID and usage. */
internal object SubscriptionProfiles {
    data class Entry(val profile: ProfileItem, val raw: String? = null)
    data class Saved(val guid: String, val entry: Entry, val reused: Boolean)
    private data class Identity(val profile: JsonElement, val raw: JsonElement?)

    fun plan(existing: Map<String, Entry>, incoming: List<Entry>, newGuid: () -> String): List<Saved> {
        val available = existing.entries.groupBy { identity(it.value) }
            .mapValues { (_, entries) -> entries.toMutableList() }
        return incoming.map { entry ->
            val matches = available[identity(entry)]
            val match = matches?.firstOrNull { it.value.profile.remarks == entry.profile.remarks }
                ?: matches?.firstOrNull()
            if (match == null) Saved(newGuid(), entry, false)
            else {
                matches?.remove(match)
                Saved(match.key, entry.copy(profile = entry.profile.copy(addedTime = match.value.profile.addedTime)), true)
            }
        }
    }

    private fun identity(entry: Entry): Identity {
        // ProfileItem.equals omits some security fields; compare all persisted connection fields.
        val profile = entry.profile.copy(subscriptionId = "", addedTime = 0, remarks = "", description = null)
        val raw = if (profile.configType == EConfigType.CUSTOM) entry.raw?.let {
            JsonParser.parseString(it).also { json -> if (json.isJsonObject) json.asJsonObject.remove("remarks") }
        } else null
        return Identity(JsonParser.parseString(JsonUtil.toJson(profile)), raw)
    }
}
