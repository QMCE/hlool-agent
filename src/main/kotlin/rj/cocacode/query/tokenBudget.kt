package rj.cocacode.query

import kotlin.math.round

private const val COMPLETION_THRESHOLD = 0.9
private const val DIMINISHING_THRESHOLD = 500

data class BudgetTracker(
  var continuationCount: Int,
  var lastDeltaTokens: Int,
  var lastGlobalTurnTokens: Int,
  var startedAt: Long,
)

fun createBudgetTracker(): BudgetTracker {
  return BudgetTracker(
    continuationCount = 0,
    lastDeltaTokens = 0,
    lastGlobalTurnTokens = 0,
    startedAt = System.currentTimeMillis(),
  )
}

sealed class TokenBudgetDecision
data class ContinueDecision(
  val action: String = "continue",
  val nudgeMessage: String,
  val continuationCount: Int,
  val pct: Int,
  val turnTokens: Int,
  val budget: Int,
) : TokenBudgetDecision()
data class StopDecision(
  val action: String = "stop",
  val completionEvent: CompletionEvent?,
) : TokenBudgetDecision()

data class CompletionEvent(
  val continuationCount: Int,
  val pct: Int,
  val turnTokens: Int,
  val budget: Int,
  val diminishingReturns: Boolean,
  val durationMs: Long,
)

fun getBudgetContinuationMessage(pct: Int, turnTokens: Int, budget: Int): String {
  return "Budget continuation: ${pct}% | ${turnTokens}/${budget} tokens used"
}

fun checkTokenBudget(
  tracker: BudgetTracker,
  agentId: String?,
  budget: Int?,
  globalTurnTokens: Int,
): TokenBudgetDecision {
  if (agentId != null || budget == null || budget <= 0) {
    return StopDecision("stop", null)
  }

  val turnTokens = globalTurnTokens
  val pct = round((turnTokens.toDouble() / budget.toDouble()) * 100.0).toInt()
  val deltaSinceLastCheck = globalTurnTokens - tracker.lastGlobalTurnTokens

  val isDiminishing =
    tracker.continuationCount >= 3 &&
      deltaSinceLastCheck < DIMINISHING_THRESHOLD &&
      tracker.lastDeltaTokens < DIMINISHING_THRESHOLD

  if (!isDiminishing && turnTokens < budget * COMPLETION_THRESHOLD) {
    tracker.continuationCount++
    tracker.lastDeltaTokens = deltaSinceLastCheck
    tracker.lastGlobalTurnTokens = globalTurnTokens
    return ContinueDecision(
      nudgeMessage = getBudgetContinuationMessage(pct, turnTokens, budget),
      continuationCount = tracker.continuationCount,
      pct = pct,
      turnTokens = turnTokens,
      budget = budget,
    )
  }

  if (isDiminishing || tracker.continuationCount > 0) {
    val duration = System.currentTimeMillis() - tracker.startedAt
    return StopDecision(
      completionEvent = CompletionEvent(
        continuationCount = tracker.continuationCount,
        pct = pct,
        turnTokens = turnTokens,
        budget = budget,
        diminishingReturns = isDiminishing,
        durationMs = duration,
      ),
    )
  }

  return StopDecision("stop", null)
}
