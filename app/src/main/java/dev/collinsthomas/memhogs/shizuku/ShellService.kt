package dev.collinsthomas.memhogs.shizuku

import dev.collinsthomas.memhogs.IShellService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private const val COMMAND_TIMEOUT_SECONDS = 60L

class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

    override fun meminfo(): String = execute("dumpsys", "meminfo")

    override fun activityProcesses(): String = execute("dumpsys", "activity", "lru")

    override fun killBackgroundProcesses(packageName: String) {
        execute("am", "kill", validPackageName(packageName))
    }

    override fun forceStop(packageName: String) {
        execute("am", "force-stop", validPackageName(packageName))
    }

    private fun validPackageName(packageName: String): String {
        require(isValidPackageName(packageName)) { "not a package name: $packageName" }
        return packageName
    }

    private fun execute(vararg command: String): String {
        val commandLine = command.joinToString(" ")
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("$commandLine timed out after $COMMAND_TIMEOUT_SECONDS s")
        }
        val exitCode = process.exitValue()
        check(exitCode == 0) { "$commandLine exited with $exitCode: ${output.join()}" }
        return output.join()
    }
}
