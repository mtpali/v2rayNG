package com.v2ray.ang.core

import org.junit.Assert.*
import org.junit.Test

class DaemonIdentityTest {
    @Test fun onlyThisAppDaemonWithSameUidCanBeKilled() {
        assertTrue(DaemonIdentity.isOwned("com.app:RunSoLibV2RayDaemon", 1001, 123, "com.app", 1001, 456))
        assertFalse(DaemonIdentity.isOwned("com.app", 1001, 123, "com.app", 1001, 456))
        assertFalse(DaemonIdentity.isOwned("com.other:RunSoLibV2RayDaemon", 1001, 123, "com.app", 1001, 456))
        assertFalse(DaemonIdentity.isOwned("com.app:bg", 1001, 123, "com.app", 1001, 456))
        assertFalse(DaemonIdentity.isOwned("com.app:RunSoLibV2RayDaemon", 1002, 123, "com.app", 1001, 456))
        assertFalse(DaemonIdentity.isOwned("com.app:RunSoLibV2RayDaemon", 1001, 456, "com.app", 1001, 456))
        assertFalse(DaemonIdentity.isOwned("com.app:RunSoLibV2RayDaemon", 1001, 0, "com.app", 1001, 456))
    }
}
