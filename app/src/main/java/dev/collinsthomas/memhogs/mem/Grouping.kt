package dev.collinsthomas.memhogs.mem

private const val HELPER_PROCESS_SEPARATOR = ':'
private const val PACKAGE_SEGMENT_SEPARATOR = '.'

data class Member(val processName: String, val pid: Int, val pssKb: Long)

data class AppGroup(
    val owner: String,
    val label: String,
    val isApp: Boolean,
    val pssKb: Long,
    val members: List<Member>,
)

fun groupByOwner(
    processes: List<MeminfoParser.ProcessSample>,
    hostPackageOfPid: Map<Int, String> = emptyMap(),
    appLabelOf: (String) -> String?,
): List<AppGroup> {
    val isInstalledApp = { name: String -> appLabelOf(name) != null }
    return processes
        .groupBy { hostPackageOfPid[it.pid] ?: ownerOf(it.name, isInstalledApp) }
        .map { (owner, samples) -> appGroupOf(owner, samples, appLabelOf(owner)) }
        .sortedByDescending { it.pssKb }
}

private fun ownerOf(processName: String, isInstalledApp: (String) -> Boolean): String {
    val mainProcessName = processName.substringBefore(HELPER_PROCESS_SEPARATOR)
    val candidates = sequenceOf(mainProcessName) + enclosingPackagesOf(mainProcessName)
    return candidates.firstOrNull(isInstalledApp) ?: mainProcessName
}

private fun enclosingPackagesOf(name: String): Sequence<String> =
    generateSequence(name.parentPackage()) { it.parentPackage() }
        .takeWhile(::looksLikeAppPackage)

private fun String.parentPackage(): String = substringBeforeLast(PACKAGE_SEGMENT_SEPARATOR, missingDelimiterValue = "")

private fun looksLikeAppPackage(name: String): Boolean = PACKAGE_SEGMENT_SEPARATOR in name

private fun appGroupOf(owner: String, samples: List<MeminfoParser.ProcessSample>, appLabel: String?) = AppGroup(
    owner = owner,
    label = appLabel ?: owner,
    isApp = appLabel != null,
    pssKb = samples.sumOf { it.pssKb },
    members = samples
        .sortedByDescending { it.pssKb }
        .map { Member(it.name, it.pid, it.pssKb) },
)
