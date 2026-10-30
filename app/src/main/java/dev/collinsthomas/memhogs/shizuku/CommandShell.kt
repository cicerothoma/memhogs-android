package dev.collinsthomas.memhogs.shizuku

import dev.collinsthomas.memhogs.IShellService

abstract class CommandShell(private val commands: CommandRunner) : IShellService.Stub() {

    override fun meminfo(): String = commands.run("dumpsys", "meminfo")

    override fun activityProcesses(): String = commands.run("dumpsys", "activity", "lru")

    override fun killBackgroundProcesses(packageName: String) {
        commands.run("am", "kill", validPackageName(packageName))
    }

    override fun forceStop(packageName: String) {
        commands.run("am", "force-stop", validPackageName(packageName))
    }

    private fun validPackageName(packageName: String): String {
        require(isValidPackageName(packageName)) { "not a package name: $packageName" }
        return packageName
    }
}
