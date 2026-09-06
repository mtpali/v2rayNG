package com.v2ray.ang.core

internal object WireguardReservedPolicy {

    /**
     * Omitting the all-zero default is important for AmneziaWG because forcing it onto every
     * outbound packet can overwrite bytes used by custom I1-I5 signature packets.
     */
    fun outboundBytes(reserved: String?): List<Int>? {
        val values = reserved
            ?.split(',')
            ?.map { it.trim().toIntOrNull() ?: return null }
            ?.takeIf { it.size == 3 && it.all { value -> value in 0..255 } }
            ?: return null

        return values.takeUnless { it.all { value -> value == 0 } }
    }
}
