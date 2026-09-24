# memhogs for Android

Kotlin + Compose app that ranks Android apps by memory (PSS) using a Shizuku shell.

- Before moving code or adding a file, read `ARCHITECTURE.md`. It covers packages, dependency direction, and the reasons behind the design.
- Before writing code, read `CONTRIBUTING.md#code-standards`. It covers comments, errors, the privileged shell, strings, motion, palette, and dependencies.
- Done means this passes: `./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug`. Run `./gradlew ktlintFormat` first.
- Gradle needs JDK 17 or 21. If the build fails with a bare Java version string such as `25.0.2`, set `JAVA_HOME` to a 21 install.
- New pure logic gets a JVM unit test in `app/src/test`, written before the implementation.
- `keystore.properties` and `*.jks` hold release signing secrets. Read `RELEASING.md` before touching signing or versions.
