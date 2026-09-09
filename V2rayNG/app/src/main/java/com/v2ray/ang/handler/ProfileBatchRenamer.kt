package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem

internal object ProfileBatchRenamer {
    fun plan(
        prefix: String,
        profiles: List<Pair<String, ProfileItem>>,
    ): LinkedHashMap<String, ProfileItem> {
        val normalizedPrefix = prefix.trim()
        if (normalizedPrefix.isEmpty()) return linkedMapOf()

        val result = linkedMapOf<String, ProfileItem>()
        profiles.forEachIndexed { index, (guid, profile) ->
            result[guid] = profile.copy(remarks = "$normalizedPrefix ${index + 1}")
        }
        return result
    }
}
