package rj.cocacode.cli

object Exit {
    fun error(msg: String? = null): Nothing {
        msg?.let { System.err.println(it) }
        System.exit(1)
        throw RuntimeException("unreachable")
    }

    fun ok(msg: String? = null): Nothing {
        msg?.let { System.out.println(it) }
        System.exit(0)
        throw RuntimeException("unreachable")
    }
}