# Architecture

memhogs reads `dumpsys meminfo` through a Shizuku shell, rolls processes up
into the apps that own them, and renders the result in Compose. There is one
Gradle module (`app`) and one screen.

## Data flow

```
Shizuku server (shell UID)
  ├─ ShellService.meminfo()            shizuku/   runs `dumpsys meminfo`
  └─ ShellService.activityProcesses()  shizuku/   runs `dumpsys activity lru`
       │  raw text over binder
       ▼
MeminfoParser.parse()                   mem/       text → Snapshot of ProcessSample
ActivityLruParser                       mem/       text → isolated pid → host app uid
groupByOwner()                          mem/       ProcessSample → AppGroup
       │
       ▼
toUiSnapshot()                          ui/        AppGroup → display-ready UiSnapshot
MemhogsViewModel                        root       owns MemhogsUiState (StateFlow)
       │
       ▼
MemhogsApp and its composables          ui/        stateless; render state, emit events
```

Reclaim runs the other way: `MemhogsViewModel.reclaimBackgroundMemory()` →
`ShellService.killBackgroundProcesses()` → `am kill`, once for each selected
package. The ViewModel then measures again and turns the before/after
difference, summed across the packages, into a `ReclaimResult` through
`PendingReclaim`.

## Packages

| Package | Owns | May depend on |
|---|---|---|
| `mem/` | Parsing `dumpsys` output, grouping processes by owner package | Kotlin stdlib only |
| `shizuku/` | The privileged `ShellService`, its in-app fallback `ShizukuProcessShell`, binding them (`ShizukuConnection`), package-name validation | Android, Shizuku |
| `ui/` | UI state types, mapping to them, formatting, composables, palette | `mem/`, `ShizukuAccess` enum, Compose |
| root | `MainActivity` (intents, system settings), `MemhogsViewModel` (orchestration) | everything |

Dependencies point inward. `ui/` takes only the `ShizukuAccess` enum from
`shizuku/` and calls no Android services. `mem/` imports nothing Android. That is what lets `mem/` and the mapping
in `ui/` run as plain JVM unit tests.

## Decisions

**PSS as the metric.** Proportional set size charges a page shared by N
processes 1/N to each, so group sums never double-count. It matches the
memhogs CLI and Android's own low-memory killer.

**Grouping by process name, not process tree.** Every Android app forks from
zygote, so the tree carries no ownership. An app's extra processes are
named `<package>:<suffix>`, or sometimes `<package>.<suffix>` (Play
services). `groupByOwner` strips the colon suffix, then walks dotted
prefixes looking for an installed package. It never accepts a prefix
without a dot, which keeps `android.hardware.*` daemons out of the
`android` framework package.

**Isolated processes follow their host.** An app that embeds a WebView runs
its renderer as an isolated process named after the WebView package. Only
`dumpsys activity lru` records the real owner (`u0a364i961` means isolated
on behalf of app uid 10364). The ViewModel turns that uid into a package,
and `groupByOwner` uses it before looking at the name. A host in another
user, such as HyperOS Second Space, is off limits to the app's
`PackageManager`, so its isolated processes stay grouped by name.

**Shizuku user service.** Normal apps cannot read other apps' memory.
Shizuku runs `ShellService` in its own process with the shell UID. Both
shells share `CommandShell`: typed operations that never accept a raw
command, with package names validated, run by `CommandRunner` without
`sh -c` and stopped after 60 s. AIDL transaction codes are append-only.
Code 2 (the old `run(command)`) is retired and must not be reused.

**Process shell fallback.** On HyperOS 3, Shizuku's user service starter
crashes in `LoadedApk.makeApplicationInner` before any memhogs code runs,
so the user service never connects. When it hasn't connected after 10 s,
`ShizukuConnection` switches to `ShizukuProcessShell`. That shell lives in
the app process and starts each command through Shizuku's private
`Shizuku.newProcess`, which runs it from the Shizuku server as the shell
user and skips the starter. Commands are still the same typed, validated
operations, but with the fallback they start from the app process.
`destroy()` and `exit()` do nothing there, because exiting would close
memhogs itself. Once the fallback has been needed, a saved
`needs_process_shell` flag makes later launches use it at once. The user
service is still bound every time, so if a Shizuku or OS update fixes the
starter, it connects, replaces the fallback and clears the flag.

**Reclaim is `am kill`.** It kills background processes only, which is what
the system does under memory pressure. It never touches foreground apps or
running services. The app never offers it for system daemons or for itself.

**Force stop is separate and confirmed.** `am force-stop` also closes open
apps and turns off their alarms, jobs, and notifications until the user
opens them again. It sits behind a dialog that says so and names the app,
or the count when several are selected. It is never offered for persistent
apps such as System UI, which the system would only restart, and a
selection skips them.

**State lives in the ViewModel.** `MemhogsViewModel` holds a single
`MemhogsUiState` and the Shizuku binding, so both survive rotation. Errors
and reclaim outcomes are typed (`LoadError`, `ReclaimResult`). The UI picks
the wording from `strings.xml`.

**Permissions.** The app needs `QUERY_ALL_PACKAGES` to turn every package
name into an app label, and lint's warning about it is suppressed on
purpose. A Play Store submission will need a policy declaration for it.
Shizuku delivers its binder through `ShizukuProvider` in the manifest.
There is no `INTERNET` permission, so memory data never leaves the device.

## Release and distribution

Signing, versioning, and F-Droid metadata are covered in
[RELEASING.md](RELEASING.md).
