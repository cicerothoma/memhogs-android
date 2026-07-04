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

    override fun run(command: String): String {
        val proc = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return output
    }
}
