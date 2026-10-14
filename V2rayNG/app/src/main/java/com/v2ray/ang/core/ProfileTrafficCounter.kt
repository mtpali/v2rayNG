package com.v2ray.ang.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.entities.ProfileTraffic

/** Counts the logical proxy outbounds, including balancer members and custom tags. */
internal object ProfileTrafficCounter {
    private val localProtocols = setOf("freedom", "blackhole", "dns", "loopback")

    fun prepare(content: String): String {
        val json = JsonParser.parseString(content).asJsonObject
        if (!json.has("stats") || json["stats"].isJsonNull) json.add("stats", JsonObject())
        val policy = json.objectOrCreate("policy")
        val system = policy.objectOrCreate("system")
        system.addProperty("statsOutboundUplink", true)
        system.addProperty("statsOutboundDownlink", true)
        val outbounds = json.getAsJsonArray("outbounds") ?: return json.toString()
        val used = outbounds.mapNotNull { it.asJsonObject["tag"]?.asString }.toMutableSet()
        outbounds.forEachIndexed { index, element ->
            val outbound = element.asJsonObject
            if (outbound["tag"]?.asString.isNullOrBlank()) {
                var tag = "usage-outbound-$index"
                while (tag in used) tag += "-"
                used.add(tag)
                outbound.addProperty("tag", tag)
            }
        }
        return json.toString()
    }

    fun tags(content: String): Set<String> {
        val outbounds = JsonParser.parseString(content).asJsonObject.getAsJsonArray("outbounds")
            ?.map { it.asJsonObject }.orEmpty()
        // A detour's counters contain the same logical bytes as its caller's counters.
        val hops = outbounds.flatMap { outbound ->
            listOfNotNull(outbound.existingObject("proxySettings")?.get("tag")?.asString,
                outbound.existingObject("streamSettings")?.existingObject("sockopt")?.get("dialerProxy")?.asString)
        }.toSet()
        return outbounds.filter { it["protocol"]?.asString !in localProtocols }
            .mapNotNull { it["tag"]?.asString }.filter { it !in hops }.toSet()
    }

    fun total(stats: List<OutboundTrafficStat>, tags: Set<String>): ProfileTraffic =
        stats.filter { it.tag in tags }.fold(ProfileTraffic()) { total, stat ->
            when (stat.direction) {
                "uplink" -> total.add(stat.value, 0)
                "downlink" -> total.add(0, stat.value)
                else -> total
            }
        }

    private fun JsonObject.existingObject(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.objectOrCreate(key: String): JsonObject =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { add(key, it) }
}
