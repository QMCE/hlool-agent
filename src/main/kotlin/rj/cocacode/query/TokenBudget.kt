package rj.cocacode.query

import kotlin.math.round

private const val COMPLETION_THRESHOLD = 0.9
private const val DIMINISHING_THRESHOLD = 500

private val SHORTHAND_START_RE = Regex("""^\s*\+(\d+(?:\.\d+)?)\s*(k|m|b)\b""", RegexOption.IGNORE_CASE)
private val SHORTHAND_END_RE = Regex("""\s\+(\d+(?:\.\d+)?)\s*(k|m|b)\s*[.!?]?\s*$""", RegexOption.IGNORE_CASE)
private val VERBOSE_RE = Regex("""\b(?:use|spend)\s+(\d+(?:\.\d+)?)\s*(k|m|b)\s*tokens?\b""", RegexOption.IGNORE_CASE)

private val MULTIPLIERS = mapOf(
    "k" to 1_000,
    "m" to 1_000_000,
    "b" to 1_000_000_000,
)

private fun parseBudgetMatch(value: String, suffix: String): Long {
    val num = value.toDoubleOrNull() ?: return 0L
    val multiplier = MULTIPLIERS[suffix.lowercase()] ?: return 0L
    return (num * multiplier).toLong()
}

fun parseTokenBudget(text: String): Long? {
    val startMatch = SHORTHAND_START_RE.find(text)
    if (startMatch != null) {
        return parseBudgetMatch(startMatch.groupValues[1], startMatch.groupValues[2])
    }
    val endMatch = SHORTHAND_END_RE.find(text)
    if (endMatch != null) {
        return parseBudgetMatch(endMatch.groupValues[1], endMatch.groupValues[2])
    }
    val verboseMatch = VERBOSE_RE.find(text)
    if (verboseMatch != null) {
        return parseBudgetMatch(verboseMatch.groupValues[1], verboseMatch.groupValues[2])
    }
    return null
}

data class TokenBudgetPosition(val start: Int, val end: Int)

fun findTokenBudgetPositions(text: String): List<TokenBudgetPosition> {
    val positions = mutableListOf<TokenBudgetPosition>()
    
    val startMatch = SHORTHAND_START_RE.find(text)
    if (startMatch != null) {
        val offset = startMatch.range.first + startMatch.value.length - startMatch.value.trimStart().length
        positions.add(TokenBudgetPosition(offset, startMatch.range.last + 1))
    }
    
    val endMatch = SHORTHAND_END_RE.find(text)
    if (endMatch != null) {
        val endStart = endMatch.range.first + 1
        val alreadyCovered = positions.any { p -> endStart >= p.start && endStart < p.end }
        if (!alreadyCovered) {
            positions.add(TokenBudgetPosition(endStart, endMatch.range.last + 1))
        }
    }
    
    VERBOSE_RE.findAll(text).forEach { match ->
        positions.add(TokenBudgetPosition(match.range.first, match.range.last + 1))
    }
    
    return positions
}

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
    val fmt = java.text.NumberFormat.getNumberInstance(java.util.Locale.US)
    return "Stopped at ${pct}% of token target (${fmt.format(turnTokens)} / ${fmt.format(budget)}). Keep working — do not summarize."
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