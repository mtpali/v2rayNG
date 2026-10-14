package com.v2ray.ang.handler

/** Resolve identities against the current persisted order, never a stale tab index. */
internal object SubscriptionOrder {
    fun move(ids: List<String>, source: String, target: String): List<String> {
        val from = ids.indexOf(source)
        val to = ids.indexOf(target)
        if (from < 0 || to < 0 || from == to) return ids
        return ids.toMutableList().apply { add(to, removeAt(from)) }
    }
}
