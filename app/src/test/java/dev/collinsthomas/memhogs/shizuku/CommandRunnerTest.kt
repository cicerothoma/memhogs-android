package dev.collinsthomas.memhogs.shizuku

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRunnerTest {

    @Test
    fun returnsStdoutOfACommandThatExitsWithZero() {
        var started: List<String>? = null
        val runner = CommandRunner { command ->
            started = command.toList()
            FakeProcess(stdout = "Total RAM: 7,654,321K\n")
        }

        assertEquals("Total RAM: 7,654,321K\n", runner.run("dumpsys", "meminfo"))
        assertEquals(listOf("dumpsys", "meminfo"), started)
    }

    @Test
    fun failsWithStdoutAndStderrOnANonZeroExit() {
        val runner = CommandRunner {
            FakeProcess(stdout = "partial output", stderr = "Unknown package: com.example", exitCode = 255)
        }

        val error = runCatchingFailure { runner.run("am", "force-stop", "com.example") }

        assertEquals(
            "am force-stop com.example exited with 255:\npartial output\nUnknown package: com.example",
            error.message,
        )
    }

    @Test
    fun destroysAndFailsACommandThatDoesNotExitInTime() {
        val process = FakeProcess(exits = false)
        val runner = CommandRunner { process }

        val error = runCatchingFailure { runner.run("dumpsys", "meminfo") }

        assertEquals("dumpsys meminfo timed out after 60 s", error.message)
        assertTrue(process.destroyed)
    }

    private fun runCatchingFailure(block: () -> Unit): IllegalStateException = try {
        block()
        throw AssertionError("expected the command to fail")
    } catch (e: IllegalStateException) {
        e
    }

    private class FakeProcess(
        stdout: String = "",
        stderr: String = "",
        private val exitCode: Int = 0,
        private val exits: Boolean = true,
    ) : Process() {
        private val stdout = ByteArrayInputStream(stdout.toByteArray())
        private val stderr = ByteArrayInputStream(stderr.toByteArray())
        var destroyed = false
            private set

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = stderr
        override fun waitFor(): Int = exitCode
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = exits
        override fun exitValue(): Int = exitCode
        override fun destroy() {
            destroyed = true
        }
    }
}
