package rj.cocacode.vim

class TransitionContext

data class TransitionResult(val next: CommandState? = null, val execute: (() -> Unit)? = null)

fun transition(state: CommandState, input: String, ctx: TransitionContext): TransitionResult {
  return TransitionResult()
}
