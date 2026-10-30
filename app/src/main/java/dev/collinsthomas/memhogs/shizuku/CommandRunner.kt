package dev.collinsthomas.memhogs.shizuku

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import rikka.shizuku.ShizukuRemoteProcess

private const val COMMAND_TIMEOUT_SECONDS = 60L

class CommandRunner(private val startProcess: (Array<out String>) -> Process) {

    fun run(vararg command: String): String {
        val commandLine = command.joinToString(" ")
        val process = startProcess(command)
        val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        val errors = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }
        if (!waitForExit(process)) {
            process.destroyForcibly()
            error("$commandLine timed out after $COMMAND_TIMEOUT_SECONDS s")
        }
        val exitCode = process.exitValue()
        check(exitCode == 0) {
            val printed = listOf(output.join(), errors.join()).filter(String::isNotBlank)
            "$commandLine exited with $exitCode:\n${printed.joinToString("\n")}"
        }
        return output.join()
    }

    private fun waitForExit(process: Process): Boolean = if (process is ShizukuRemoteProcess) {
        process.waitForTimeout(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } else {
        process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }
}
