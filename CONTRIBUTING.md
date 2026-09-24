# Contributing to memhogs for Android

Thanks for helping. Read [ARCHITECTURE.md](ARCHITECTURE.md) first. It
explains where code belongs and why the app works the way it does.

## Setup

- JDK 17 or newer. The one bundled with Android Studio works.
- Android SDK 37. Gradle installs it on first build once the SDK licenses are accepted.
- To test the full app, use a device or emulator with
  [Shizuku](https://shizuku.rikka.app/) running. On an emulator you can
  start it with `adb shell <shizuku apk dir>/lib/arm64/libshizuku.so`.

## The check

A change is ready when this passes. CI runs the same command on every pull
request:

```sh
./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug
```

`./gradlew ktlintFormat` fixes most formatting failures on its own.

## Code standards

**Layers.** Put code where [ARCHITECTURE.md](ARCHITECTURE.md#packages) says
it belongs, and keep dependencies pointing inward. `mem/` stays pure Kotlin.
Composables receive state and emit events. Anything that talks to Android
services goes in the ViewModel or `shizuku/`.

**Naming and size.** Use descriptive names and keep functions small, each
doing one thing. Match the idioms in the surrounding code. Formatting is
ktlint's job (see `.editorconfig`).

**The code is the documentation.** Wanting to write a comment is a sign the
code needs refactoring. Give the idea a name instead: extract a function,
name a constant, spell out an abbreviation, or write a test that shows the
case. Reasons that span the whole design go in
[ARCHITECTURE.md](ARCHITECTURE.md). Comments are only for facts code cannot
express: Shizuku's reserved AIDL transaction code, why an R8 keep rule
exists, and notes for translators in `strings.xml`.

**Errors.** Handle every failure explicitly. Catch the narrowest type that
works. Rethrow `CancellationException`. Show failures to the user through
`LoadError`, never through a silent `runCatching`. Unrecoverable
teardown failures get a `Log.w`.

**Privileged shell.** `ShellService` runs as the shell user. Every operation
on it is a typed AIDL method with validated inputs, executed without
`sh -c`. Adding one needs a stated reason in the pull request. New
transaction codes go at the end, and old ones are never reused.

**User-facing text.** All user-facing text lives in
`res/values/strings.xml`, and counts use `<plurals>`. Literal shell commands
(`$ memhogs`) and box-drawing glyphs stay in code. Terminal transcript
strings are typed out line by line, so keep each `\n`-separated line under
42 characters.

**Motion.** Gate every animation on the `motion` flag, which follows the
system's "remove animations" setting.

**Palette.** Colors carry meaning shared with the CLI: amber for memory,
cyan for installed apps, green for system daemons, red for anything past
`HOT_GROUP_SHARE_OF_RAM` or `HOT_DEVICE_USED_FRACTION`. Use `Palette` tokens, never raw colors.

**Dependencies.** AndroidX, Kotlin, and Shizuku are the whole dependency
list. Open an issue before adding a library. The app ships without the
`INTERNET` permission, and that is permanent.

## Tests

Pure logic gets a JVM unit test in `app/src/test`: parsing, grouping,
mapping, formatting, validation. Parser changes come with a fixture copied
from real `dumpsys meminfo` output. Note the Android version it came from in
the test name or fixture. UI and Shizuku changes need a manual check on a
device. Describe what you checked in the pull request.

## Commits and pull requests

- Keep each commit to one logical change. Subject line: imperative and
  under 72 characters. The body explains why.
- Link the issue the pull request addresses. For UI changes, include
  before and after screenshots.
- Bug reports and feature requests use the templates in
  `.github/ISSUE_TEMPLATE`.

## Releases

Maintainers cut releases following [RELEASING.md](RELEASING.md).
