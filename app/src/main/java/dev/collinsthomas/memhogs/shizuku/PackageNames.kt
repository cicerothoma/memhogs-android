package dev.collinsthomas.memhogs.shizuku

private val packageName = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

fun isValidPackageName(name: String): Boolean = packageName.matches(name)
