package com.v2ray.ang.core

internal object DaemonIdentity {
    fun isOwned(name: String, uid: Int, pid: Int, app: String, ownUid: Int, ownPid: Int): Boolean =
        name == "$app:RunSoLibV2RayDaemon" && uid == ownUid && pid > 0 && pid != ownPid
}
