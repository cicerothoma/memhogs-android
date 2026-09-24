package dev.collinsthomas.memhogs.shizuku

import dev.collinsthomas.memhogs.IShellService
import kotlin.system.exitProcess

/**
 * Runs inside the Shizuku server process with the shell UID, so commands
 * here see what `adb shell` sees. That is what makes `dumpsys meminfo`
 * readable at all: normal apps have no access to other apps' memory.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

    override fun meminfo(): String = execute("dumpsys", "meminfo")

    override fun killBackgroundProcesses(packageName: String) {
        require(isValidPackageName(packageName)) { "not a package name: $packageName" }
        execute("am", "kill", packageName)
    }

    private fun execute(vararg command: String): String {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "${command.joinToString(" ")} exited with $exitCode: $output" }
        return output
    }
}
