package rj.cocacode

fun main(args: Array<String>) {
    when {
        args.any { it == "-v" || it == "--version" } -> println("Hlool Agent v${rj.cocacode.BuildKonfig.APP_VERSION}")
        else -> {
            println("Hlool Agent - AI Coding Assistant for SHUAI API")
            println("Usage: hlool-agent [command] [options]")
            println("       hlool-agent --version")
        }
    }
}
