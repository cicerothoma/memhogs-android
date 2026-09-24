package dev.collinsthomas.memhogs.mem

object ActivityLruParser {

    private const val PER_USER_UID_RANGE = 100_000
    private const val FIRST_APP_UID = 10_000

    private val isolatedProcess = Regex("""\s(\d+):\S+/u(\d+)a(\d+)i\d+""")

    fun hostUidsOfIsolatedProcesses(text: String): Map<Int, Int> = isolatedProcess.findAll(text).associate { match ->
        val (pid, userId, appId) = match.destructured
        pid.toInt() to hostUid(userId.toInt(), appId.toInt())
    }

    private fun hostUid(userId: Int, appId: Int): Int = userId * PER_USER_UID_RANGE + FIRST_APP_UID + appId
}
