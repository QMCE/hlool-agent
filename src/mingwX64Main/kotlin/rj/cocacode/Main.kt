package rj.cocacode

/**
 * Minimal entry point for the mingwX64 (Windows) target.
 *
 * The full CLI (Clikt-based) and REPL/engine logic live in `jvmMain` and are not
 * visible to this source set, so the native binary only handles version/help.
 * Real Windows support requires moving the core loop into `commonMain`.
 */
fun main(args: Array<String>) {
    when {
        args.any { it == "-v" || it == "--version" } -> println("CocaCode v${rj.cocacode.BuildKonfig.APP_VERSION}")
        else -> {
            println("CocaCode - AI Coding Assistant")
            println("Usage: cocacode [command] [options]")
            println("       cocacode --version")
        }
    }
}