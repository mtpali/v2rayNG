package com.v2ray.ang.root

/** Root helper also survives Android force-stop; it must clean up when its owner dies. */
internal object RootRuntimeGuard {
    fun script(corePid: Int, coreStart: String, directory: String, token: String,
               captureDevice: Boolean): String = """
        same_process() {
            [ -r "/proc/${'$'}1/stat" ] || return 1
            [ "${'$'}(awk '{print ${'$'}22}' /proc/${'$'}1/stat 2>/dev/null)" = "${'$'}2" ]
        }
        stop_setup() {
            SETUP=${'$'}(cat '$directory/setup.owner' 2>/dev/null)
            set -- ${'$'}SETUP
            if [ "${'$'}#" = 2 ] && same_process "${'$'}1" "${'$'}2"; then
                kill "${'$'}1" 2>/dev/null || true
            fi
        }
        while [ "${'$'}(cat '$directory/runtime.token' 2>/dev/null)" = '$token' ]; do
            if ! same_process $corePid '$coreStart'; then
                stop_setup
                sh '$directory/guard_cleanup.sh'
                exit 0
            fi
            echo -1000 > /proc/$corePid/oom_score_adj 2>/dev/null || true
            if [ -f '$directory/guard_lan_cleanup.sh' ]; then
                OWNER=${'$'}(cat '$directory/hotspot.owner' 2>/dev/null)
                set -- ${'$'}OWNER
                if [ "${'$'}#" != 2 ] || ! same_process "${'$'}1" "${'$'}2"; then
                    sh '$directory/guard_lan_cleanup.sh'
                    ${if (captureDevice) "[ -f '$directory/setup.owner' ] || rm -f '$directory/guard_lan_cleanup.sh'" else "stop_setup; sh '$directory/guard_cleanup.sh'; exit 0"}
                fi
            fi
            sleep 1
        done
    """.trimIndent()
}
