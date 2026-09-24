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
`ShellService.killBackgroundProcesses()` → `am kill`. The ViewModel then
measures again and turns the before/after difference into a `ReclaimResult`
through `PendingReclaim`.

## Packages

| Package | Owns | May depend on |
|---|---|---|
| `mem/` | Parsing `dumpsys` output, grouping processes by owner package | Kotlin stdlib only |
| `shizuku/` | The privileged `ShellService`, binding it (`ShizukuConnection`), package-name validation | Android, Shizuku |
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
and `groupByOwner` uses it before looking at the name.

**Shizuku user service.** Normal apps cannot read other apps' memory.
Shizuku runs `ShellService` in its own process with the shell UID. That
service is the only code that runs with elevated privilege. It exposes
typed operations and never accepts a raw command. AIDL transaction codes
are append-only. Code 2 (the old `run(command)`) is retired and must not be
reused.

**Reclaim is `am kill`.** It kills background processes only, which is what
the system does under memory pressure. It never touches foreground apps or
running services. The app never offers it for system daemons or for itself.

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
