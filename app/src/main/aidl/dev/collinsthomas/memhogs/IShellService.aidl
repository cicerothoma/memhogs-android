package dev.collinsthomas.memhogs;

interface IShellService {
    // Transaction code reserved by Shizuku for tearing down user services.
    void destroy() = 16777114;

    void exit() = 1;

    String meminfo() = 3;

    void killBackgroundProcesses(String packageName) = 4;

    String activityProcesses() = 5;
}
