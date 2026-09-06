package com.v2ray.ang.core

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.v2ray.ang.AppConfig

/** Keeps routing valid when the optional compact CN/private GeoIP database is unavailable. */
internal object GeoIpRuleResolver {
    fun resolve(ipRules: List<String>, compactDatabaseAvailable: Boolean): List<String> =
        ipRules.map { resolveRule(it, compactDatabaseAvailable) }

    fun normalizeCustomRouting(
        config: JsonObject,
        compactDatabaseAvailable: Boolean,
    ): Boolean {
        val rules = config.get("routing")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("rules")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: return false

        var changed = false
        for (ruleElement in rules) {
            val ipRules = ruleElement
                .takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("ip")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?: continue

            for (index in 0 until ipRules.size()) {
                val element = ipRules[index]
                if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) continue
                val source = element.asString
                val resolved = resolveRule(source, compactDatabaseAvailable)
                if (source != resolved) {
                    ipRules.set(index, JsonPrimitive(resolved))
                    changed = true
                }
            }
        }
        return changed
    }

    private fun resolveRule(rule: String, compactDatabaseAvailable: Boolean): String {
        val compactPrefix = "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:"
        val compactList = rule
            .takeIf { it.startsWith(compactPrefix, ignoreCase = true) }
            ?.substring(compactPrefix.length)
            ?.lowercase()
            ?.takeIf { it == "cn" || it == "private" }

        if (compactList != null) {
            return if (compactDatabaseAvailable) "$compactPrefix$compactList" else "geoip:$compactList"
        }

        return when {
            rule.equals(AppConfig.GEOIP_CN, ignoreCase = true) ->
                if (compactDatabaseAvailable) "${compactPrefix}cn" else AppConfig.GEOIP_CN
            rule.equals(AppConfig.GEOIP_PRIVATE, ignoreCase = true) ->
                if (compactDatabaseAvailable) "${compactPrefix}private" else AppConfig.GEOIP_PRIVATE
            else -> rule
        }
    }
}
