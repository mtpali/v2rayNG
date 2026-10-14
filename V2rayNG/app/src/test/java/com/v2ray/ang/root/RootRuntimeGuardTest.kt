package com.v2ray.ang.root

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class RootRuntimeGuardTest {
    private fun exercise(captureDevice: Boolean, ownerExists: Boolean, test: (File, Process, Process) -> Unit) {
        val dir = Files.createTempDirectory("v2ray-runtime-guard").toFile()
        val core = ProcessBuilder("sleep", "20").start()
        val pid = core.pid().toInt()
        val start = File("/proc/$pid/stat").readText().substringAfterLast(") ").split(' ')[19]
        File(dir, "runtime.token").writeText("run-1")
        if (ownerExists) File(dir, "hotspot.owner").writeText("$pid $start")
        File(dir, "guard_lan_cleanup.sh").writeText("echo lan >> '${dir.absolutePath}/events'\n")
        File(dir, "guard_cleanup.sh").writeText("echo full >> '${dir.absolutePath}/events'\nrm -f '${dir.absolutePath}/runtime.token'\n")
        val guardFile = File(dir, "guard.sh").apply { writeText(RootRuntimeGuard.script(pid, start, dir.absolutePath, "run-1", captureDevice)) }
        val syntax = ProcessBuilder("sh", "-n", guardFile.absolutePath).start()
        assertTrue(syntax.waitFor(2, TimeUnit.SECONDS)); assertEquals(0, syntax.exitValue())
        val guard = ProcessBuilder("sh", guardFile.absolutePath).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try { test(dir, core, guard) }
        finally { guard.destroyForcibly(); core.destroyForcibly(); guard.waitFor(); core.waitFor(); dir.deleteRecursively() }
    }
    private fun waitForEvents(dir: File) {
        repeat(30) { if (File(dir, "events").exists()) return; Thread.sleep(50) }
        fail("Guard did not clean up")
    }
    @Test fun forceStopOfCoreCleansTheRootRuntime() = exercise(true, true) { dir, core, guard ->
        core.destroyForcibly(); core.waitFor()
        assertTrue(guard.waitFor(3, TimeUnit.SECONDS))
        assertTrue(File(dir, "events").readText().contains("full"))
    }
    @Test fun closingUiDisablesOnlyHotspotInRootMode() = exercise(true, false) { dir, _, guard ->
        waitForEvents(dir)
        assertEquals("lan\n", File(dir, "events").readText())
        assertTrue(guard.isAlive)
    }
    @Test fun closingUiStopsTheDedicatedClientHelper() = exercise(false, false) { dir, _, guard ->
        assertTrue(guard.waitFor(3, TimeUnit.SECONDS))
        assertEquals("lan\nfull\n", File(dir, "events").readText())
    }
    @Test fun oldGuardCannotCleanUpNewRuntime() = exercise(true, true) { dir, _, guard ->
        File(dir, "runtime.token").writeText("run-2")
        assertTrue(guard.waitFor(3, TimeUnit.SECONDS))
        assertFalse(File(dir, "events").exists())
    }
}
