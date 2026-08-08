package rj.cocacode.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellTest {

    @Test
    fun `simple command returns stdout`() {
        val result = Shell.exec("echo hello", options = Shell.ExecOptions(timeout = 5000, shell = true))
        assertEquals("hello", result.stdout.trim())
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `large output does not deadlock on pipe buffer`() {
        // >64KB output exceeds the OS pipe buffer; the old implementation
        // blocked waiting for the child to exit. It must return promptly.
        val result = runBlocking {
            withTimeoutOrNull(15_000) {
                Shell.execSuspend(
                    "head -c 200000 /dev/zero | tr '\\0' 'x'",
                    options = Shell.ExecOptions(timeout = 10_000, shell = true)
                )
            }
        }
        assertTrue(result != null, "large-output command hung")
        assertEquals(200000, result!!.stdout.length)
        assertTrue(!result.timedOut)
    }

    @Test
    fun `hanging command times out instead of hanging forever`() {
        val result = runBlocking {
            withTimeoutOrNull(10_000) {
                Shell.execSuspend(
                    "sleep 100",
                    options = Shell.ExecOptions(timeout = 1000, shell = true)
                )
            }
        }
        assertTrue(result != null, "hanging command did not return")
        assertTrue(result!!.timedOut, "expected timedOut=true but was false")
    }

    @Test
    fun `cancelled coroutine does not leak the process`() {
        runBlocking {
            val job = launch {
                Shell.execSuspend("sleep 30", options = Shell.ExecOptions(timeout = 60_000, shell = true))
            }
            delay(500) // let the process start
            job.cancel()
            // A cancelled execSuspend must destroy the process and return
            // control promptly (join completes well before the 60s timeout).
            withTimeout(5000) { job.join() }
        }
    }
}
