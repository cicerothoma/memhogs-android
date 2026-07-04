package dev.collinsthomas.memhogs.mem

/**
 * Rolls processes up into the app that owns them, mirroring the CLI's
 * grouping. On Android the process tree is unhelpful (everything forks from
 * zygote), but process names carry the ownership instead: an app's extra
 * processes are named "<package>:<suffix>", so Chrome's renderers
 * ("com.android.chrome:sandboxed_process0...") group under Chrome the same
 * way Electron helpers group under their app on the desktop.
 */

data class Member(val processName: String, val pid: Int, val pssKb: Long)

data class AppGroup(
    /** Base package name, or the raw process name for native/system daemons. */
    val packageName: String,
    /** Human-readable label when the package resolves to an installed app. */
    val label: String,
    /** True when the package resolves to an installed app (cyan in the CLI). */
    val isApp: Boolean,
    val pssKb: Long,
    val members: List<Member>,
)

/**
 * Groups samples by base package name, summing PSS. [labelOf] resolves a
 * package to its app label, returning null for anything that is not an
 * installed app (system daemons, native services); those keep their raw
 * name, matching the CLI's standalone groups.
 */
fun groupByPackage(
    procs: List<MeminfoParser.ProcSample>,
    labelOf: (String) -> String?,
): List<AppGroup> {
    // Most helper processes are named "<package>:<suffix>", but apps can
    // declare any process name, and Google Play services uses dot suffixes
    // ("com.google.android.gms.persistent"). After stripping a colon suffix,
    // fall back to the longest name prefix that is an installed package.
    // The walk never accepts a dotless prefix: every real app package has a
    // dot, and stopping there keeps native daemons ("android.hardware.*")
    // from being absorbed into the framework package "android".
    fun ownerOf(processName: String): String {
        val base = processName.substringBefore(':')
        if (labelOf(base) != null) return base
        var candidate = base
        while (candidate.contains('.')) {
            candidate = candidate.substringBeforeLast('.')
            if (candidate.contains('.') && labelOf(candidate) != null) return candidate
        }
        return base
    }
    return procs.groupBy { ownerOf(it.name) }
        .map { (pkg, samples) ->
            val label = labelOf(pkg)
            AppGroup(
                packageName = pkg,
                label = label ?: pkg,
                isApp = label != null,
                pssKb = samples.sumOf { it.pssKb },
                members = samples
                    .sortedByDescending { it.pssKb }
                    .map { Member(it.name, it.pid, it.pssKb) },
            )
        }
        .sortedByDescending { it.pssKb }
}

/** Formats a KiB count in binary units with one decimal, like the CLI. */
fun humanKb(kb: Long): String {
    val b = kb * 1024
    if (b < 1024) return "$b B"
    var div = 1024L
    var exp = 0
    var n = b / 1024
    while (n >= 1024) {
        div *= 1024
        exp++
        n /= 1024
    }
    return String.format("%.1f %ciB", b.toDouble() / div, "KMGTPE"[exp])
}
