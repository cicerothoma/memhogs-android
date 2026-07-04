# Shizuku instantiates the user service by class name in its own process,
# and the AIDL stub is resolved reflectively across the binder; neither
# reference is visible to R8.
-keep class dev.collinsthomas.memhogs.shizuku.ShellService { *; }
-keep class dev.collinsthomas.memhogs.IShellService { *; }
-keep class dev.collinsthomas.memhogs.IShellService$Stub { *; }
