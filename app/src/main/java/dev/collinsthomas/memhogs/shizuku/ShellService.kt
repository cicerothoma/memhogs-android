package dev.collinsthomas.memhogs.shizuku

import dev.collinsthomas.memhogs.IShellService
import kotlin.system.exitProcess

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
