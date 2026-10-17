package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem

internal object ProfileBatchRenamer {
    fun plan(
        prefix: String,
        profiles: List<Pair<String, ProfileItem>>,
    ): LinkedHashMap<String, ProfileItem> {
        val normalizedPrefix = prefix.trim()

        val result = linkedMapOf<String, ProfileItem>()
        profiles.forEachIndexed { index, (guid, profile) ->
            val number = (index + 1).toString()
            result[guid] = profile.copy(remarks = if (normalizedPrefix.isEmpty()) number else "$normalizedPrefix $number")
        }
        return result
    }
}
