package dev.collinsthomas.memhogs.shizuku

import kotlin.system.exitProcess

class ShellService : CommandShell(CommandRunner { ProcessBuilder(*it).start() }) {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }
}
