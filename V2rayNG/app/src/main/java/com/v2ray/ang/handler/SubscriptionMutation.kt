package com.v2ray.ang.handler

/** Roll back every touched record, including a write that reported failure. */
internal object SubscriptionMutation {
    fun <Key> apply(changes: Map<Key, String?>, read: (Key) -> String?,
                    write: (Key, String?) -> Boolean, rollbackFailure: (Key, Exception?) -> Unit) {
        val originals = changes.keys.associateWith(read)
        val written = mutableListOf<Key>()
        try {
            changes.forEach { (key, value) -> written.add(key); check(write(key, value)) }
        } catch (error: Exception) {
            written.asReversed().forEach { key ->
                try {
                    if (!write(key, originals[key])) rollbackFailure(key, null)
                } catch (rollbackError: Exception) {
                    rollbackFailure(key, rollbackError)
                }
            }
            throw error
        }
    }
}
