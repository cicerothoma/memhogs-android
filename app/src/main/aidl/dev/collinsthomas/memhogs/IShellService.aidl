package dev.collinsthomas.memhogs;

interface IShellService {
    // Transaction code reserved by Shizuku for tearing down user services.
    void destroy() = 16777114;

    void exit() = 1;

    String run(String command) = 2;
}
