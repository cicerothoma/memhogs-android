package dev.collinsthomas.memhogs.shizuku

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import rikka.shizuku.Shizuku

class ShizukuProcessShell(newProcess: Method) :
    CommandShell(CommandRunner { command -> startShizukuProcess(newProcess, command) }) {

    override fun destroy() = Unit

    override fun exit() = Unit
}

fun findShizukuNewProcess(): Method = Shizuku::class.java.getDeclaredMethod(
    "newProcess",
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java,
).apply { isAccessible = true }

private fun startShizukuProcess(newProcess: Method, command: Array<out String>): Process = try {
    newProcess.invoke(null, command, null, null) as Process
} catch (e: InvocationTargetException) {
    throw e.targetException
}
