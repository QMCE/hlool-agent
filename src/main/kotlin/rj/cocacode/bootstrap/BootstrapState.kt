package rj.cocacode.bootstrap

import java.util.UUID
import java.io.File

sealed class ChannelEntry {
    data class Plugin(
        val name: String,
        val marketplace: String,
        val dev: Boolean = false
    ) : ChannelEntry()
    
    data class Server(
        val name: String,
        val dev: Boolean = false
    ) : ChannelEntry()
}

interface AttributedCounter {
    fun add(value: Int, additionalAttributes: Map<String, Any>? = null)
}

data class TeleportedSessionInfo(
    val isTeleported: Boolean = false,
    val hasLoggedFirstMessage: Boolean = false,
    val sessionId: String? = null
)

data class InvokedSkillInfo(
    val skillName: String,
    val skillPath: String,
    val content: String,
    val invokedAt: Long,
    val agentId: String?
)

data class SlowOperation(
    val operation: String,
    val durationMs: Long,
    val timestamp: Long
)

data class SessionCronTask(
    val id: String,
    val cron: String,
    val prompt: String,
    val createdAt: Long,
    val recurring: Boolean = false,
    val agentId: String? = null
)

data class InMemoryError(
    val error: String,
    val timestamp: String
)

data class ModelUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheReadInputTokens: Int = 0,
    val cacheCreationInputTokens: Int = 0,
    val webSearchRequests: Int = 0
)

data class BootstrapState(
    var originalCwd: String = "",
    var projectRoot: String = "",
    var totalCostUSD: Double = 0.0,
    var totalAPIDuration: Long = 0,
    var totalAPIDurationWithoutRetries: Long = 0,
    var totalToolDuration: Long = 0,
    var turnHookDurationMs: Long = 0,
    var turnToolDurationMs: Long = 0,
    var turnClassifierDurationMs: Long = 0,
    var turnToolCount: Int = 0,
    var turnHookCount: Int = 0,
    var turnClassifierCount: Int = 0,
    var startTime: Long = System.currentTimeMillis(),
    var lastInteractionTime: Long = System.currentTimeMillis(),
    var totalLinesAdded: Int = 0,
    var totalLinesRemoved: Int = 0,
    var hasUnknownModelCost: Boolean = false,
    var cwd: String = "",
    val modelUsage: MutableMap<String, ModelUsage> = mutableMapOf(),
    var mainLoopModelOverride: String? = null,
    var initialMainLoopModel: String? = null,
    var modelStrings: String? = null,
    var isInteractive: Boolean = false,
    var kairosActive: Boolean = false,
    var strictToolResultPairing: Boolean = false,
    var sdkAgentProgressSummariesEnabled: Boolean = false,
    var userMsgOptIn: Boolean = false,
    var clientType: String = "cli",
    var sessionSource: String? = null,
    var questionPreviewFormat: String? = null,
    var flagSettingsPath: String? = null,
    var flagSettingsInline: Map<String, Any>? = null,
    var allowedSettingSources: List<String> = listOf(
        "userSettings",
        "projectSettings", 
        "localSettings",
        "flagSettings",
        "policySettings"
    ),
    var sessionIngressToken: String? = null,
    var oauthTokenFromFd: String? = null,
    var apiKeyFromFd: String? = null,
    var sessionId: String = UUID.randomUUID().toString(),
    var parentSessionId: String? = null,
    var inlinePlugins: List<String> = listOf(),
    var chromeFlagOverride: Boolean? = null,
    var useCoworkPlugins: Boolean = false,
    var sessionBypassPermissionsMode: Boolean = false,
    var scheduledTasksEnabled: Boolean = false,
    val sessionCronTasks: MutableList<SessionCronTask> = mutableListOf(),
    val sessionCreatedTeams: MutableSet<String> = mutableSetOf(),
    var sessionTrustAccepted: Boolean = false,
    var sessionPersistenceDisabled: Boolean = false,
    var hasExitedPlanMode: Boolean = false,
    var needsPlanModeExitAttachment: Boolean = false,
    var needsAutoModeExitAttachment: Boolean = false,
    var lspRecommendationShownThisSession: Boolean = false,
    var initJsonSchema: Map<String, Any>? = null,
    val planSlugCache: MutableMap<String, String> = mutableMapOf(),
    var teleportedSessionInfo: TeleportedSessionInfo? = null,
    val invokedSkills: MutableMap<String, InvokedSkillInfo> = mutableMapOf(),
    val slowOperations: MutableList<SlowOperation> = mutableListOf(),
    var sdkBetas: List<String>? = null,
    var mainThreadAgentType: String? = null,
    var isRemoteMode: Boolean = false,
    var directConnectServerUrl: String? = null,
    val systemPromptSectionCache: MutableMap<String, String?> = mutableMapOf(),
    var lastEmittedDate: String? = null,
    var additionalDirectoriesForClaudeMd: List<String> = listOf(),
    var allowedChannels: List<ChannelEntry> = listOf(),
    var hasDevChannels: Boolean = false,
    var sessionProjectDir: String? = null,
    var promptCache1hAllowlist: List<String>? = null,
    var promptCache1hEligible: Boolean? = null,
    var afkModeHeaderLatched: Boolean? = null,
    var fastModeHeaderLatched: Boolean? = null,
    var cacheEditingHeaderLatched: Boolean? = null,
    var thinkingClearLatched: Boolean? = null,
    var promptId: String? = null,
    var lastMainRequestId: String? = null,
    var lastApiCompletionTimestamp: Long? = null,
    var pendingPostCompaction: Boolean = false,
    val inMemoryErrorLog: MutableList<InMemoryError> = mutableListOf()
)

object BootstrapStateHolder {
    private val resolvedCwd: String = try {
        File(".").canonicalPath.normalize()
    } catch (e: Exception) {
        System.getProperty("user.dir")?.normalize() ?: ""
    }

    val STATE: BootstrapState = BootstrapState(
        originalCwd = resolvedCwd,
        projectRoot = resolvedCwd,
        cwd = resolvedCwd,
        startTime = System.currentTimeMillis(),
        lastInteractionTime = System.currentTimeMillis()
    )
}

private var interactionTimeDirty = false
private var scrollDraining = false
private var scrollDrainTimer: Thread? = null
private const val SCROLL_DRAIN_IDLE_MS = 150L

private var outputTokensAtTurnStart = 0
private var currentTurnTokenBudget: Int? = null
private var budgetContinuationCount = 0
private const val MAX_SLOW_OPERATIONS = 10
private const val SLOW_OPERATION_TTL_MS = 10000L

fun getSessionId(): String = BootstrapStateHolder.STATE.sessionId

fun regenerateSessionId(setCurrentAsParent: Boolean = false): String {
    if (setCurrentAsParent) {
        BootstrapStateHolder.STATE.parentSessionId = BootstrapStateHolder.STATE.sessionId
    }
    BootstrapStateHolder.STATE.planSlugCache.remove(BootstrapStateHolder.STATE.sessionId)
    BootstrapStateHolder.STATE.sessionId = UUID.randomUUID().toString()
    BootstrapStateHolder.STATE.sessionProjectDir = null
    return BootstrapStateHolder.STATE.sessionId
}

fun getParentSessionId(): String? = BootstrapStateHolder.STATE.parentSessionId

fun switchSession(sessionId: String, projectDir: String? = null) {
    BootstrapStateHolder.STATE.planSlugCache.remove(BootstrapStateHolder.STATE.sessionId)
    BootstrapStateHolder.STATE.sessionId = sessionId
    BootstrapStateHolder.STATE.sessionProjectDir = projectDir
}

fun getSessionProjectDir(): String? = BootstrapStateHolder.STATE.sessionProjectDir

fun getOriginalCwd(): String = BootstrapStateHolder.STATE.originalCwd

fun getProjectRoot(): String = BootstrapStateHolder.STATE.projectRoot

fun setOriginalCwd(cwd: String) {
    BootstrapStateHolder.STATE.originalCwd = cwd.normalize()
}

fun setProjectRoot(cwd: String) {
    BootstrapStateHolder.STATE.projectRoot = cwd.normalize()
}

fun getCwdState(): String = BootstrapStateHolder.STATE.cwd

fun setCwdState(cwd: String) {
    BootstrapStateHolder.STATE.cwd = cwd.normalize()
}

fun getDirectConnectServerUrl(): String? = BootstrapStateHolder.STATE.directConnectServerUrl

fun setDirectConnectServerUrl(url: String) {
    BootstrapStateHolder.STATE.directConnectServerUrl = url
}

fun addToTotalDurationState(duration: Long, durationWithoutRetries: Long) {
    BootstrapStateHolder.STATE.totalAPIDuration += duration
    BootstrapStateHolder.STATE.totalAPIDurationWithoutRetries += durationWithoutRetries
}

fun resetTotalDurationStateAndCostForTestsOnly() {
    BootstrapStateHolder.STATE.totalAPIDuration = 0
    BootstrapStateHolder.STATE.totalAPIDurationWithoutRetries = 0
    BootstrapStateHolder.STATE.totalCostUSD = 0.0
}

fun addToTotalCostState(cost: Double, modelUsage: ModelUsage, model: String) {
    BootstrapStateHolder.STATE.modelUsage[model] = modelUsage
    BootstrapStateHolder.STATE.totalCostUSD += cost
}

fun getTotalCostUSD(): Double = BootstrapStateHolder.STATE.totalCostUSD

fun getTotalAPIDuration(): Long = BootstrapStateHolder.STATE.totalAPIDuration

fun getTotalDuration(): Long = System.currentTimeMillis() - BootstrapStateHolder.STATE.startTime

fun getTotalAPIDurationWithoutRetries(): Long = BootstrapStateHolder.STATE.totalAPIDurationWithoutRetries

fun getTotalToolDuration(): Long = BootstrapStateHolder.STATE.totalToolDuration

fun addToToolDuration(duration: Long) {
    BootstrapStateHolder.STATE.totalToolDuration += duration
    BootstrapStateHolder.STATE.turnToolDurationMs += duration
    BootstrapStateHolder.STATE.turnToolCount++
}

fun getTurnHookDurationMs(): Long = BootstrapStateHolder.STATE.turnHookDurationMs

fun addToTurnHookDuration(duration: Long) {
    BootstrapStateHolder.STATE.turnHookDurationMs += duration
    BootstrapStateHolder.STATE.turnHookCount++
}

fun resetTurnHookDuration() {
    BootstrapStateHolder.STATE.turnHookDurationMs = 0
    BootstrapStateHolder.STATE.turnHookCount = 0
}

fun getTurnHookCount(): Int = BootstrapStateHolder.STATE.turnHookCount

fun getTurnToolDurationMs(): Long = BootstrapStateHolder.STATE.turnToolDurationMs

fun resetTurnToolDuration() {
    BootstrapStateHolder.STATE.turnToolDurationMs = 0
    BootstrapStateHolder.STATE.turnToolCount = 0
}

fun getTurnToolCount(): Int = BootstrapStateHolder.STATE.turnToolCount

fun getTurnClassifierDurationMs(): Long = BootstrapStateHolder.STATE.turnClassifierDurationMs

fun addToTurnClassifierDuration(duration: Long) {
    BootstrapStateHolder.STATE.turnClassifierDurationMs += duration
    BootstrapStateHolder.STATE.turnClassifierCount++
}

fun resetTurnClassifierDuration() {
    BootstrapStateHolder.STATE.turnClassifierDurationMs = 0
    BootstrapStateHolder.STATE.turnClassifierCount = 0
}

fun getTurnClassifierCount(): Int = BootstrapStateHolder.STATE.turnClassifierCount

fun addToTotalLinesChanged(added: Int, removed: Int) {
    BootstrapStateHolder.STATE.totalLinesAdded += added
    BootstrapStateHolder.STATE.totalLinesRemoved += removed
}

fun getTotalLinesAdded(): Int = BootstrapStateHolder.STATE.totalLinesAdded

fun getTotalLinesRemoved(): Int = BootstrapStateHolder.STATE.totalLinesRemoved

fun getTotalInputTokens(): Int = BootstrapStateHolder.STATE.modelUsage.values.sumOf { it.inputTokens }

fun getTotalOutputTokens(): Int = BootstrapStateHolder.STATE.modelUsage.values.sumOf { it.outputTokens }

fun getTotalCacheReadInputTokens(): Int = BootstrapStateHolder.STATE.modelUsage.values.sumOf { it.cacheReadInputTokens }

fun getTotalCacheCreationInputTokens(): Int = BootstrapStateHolder.STATE.modelUsage.values.sumOf { it.cacheCreationInputTokens }

fun getTotalWebSearchRequests(): Int = BootstrapStateHolder.STATE.modelUsage.values.sumOf { it.webSearchRequests }

fun getTurnOutputTokens(): Int = getTotalOutputTokens() - outputTokensAtTurnStart

fun getCurrentTurnTokenBudget(): Int? = currentTurnTokenBudget

fun snapshotOutputTokensForTurn(budget: Int?) {
    outputTokensAtTurnStart = getTotalOutputTokens()
    currentTurnTokenBudget = budget
    budgetContinuationCount = 0
}

fun getBudgetContinuationCount(): Int = budgetContinuationCount

fun incrementBudgetContinuationCount() {
    budgetContinuationCount++
}

fun setHasUnknownModelCost() {
    BootstrapStateHolder.STATE.hasUnknownModelCost = true
}

fun hasUnknownModelCost(): Boolean = BootstrapStateHolder.STATE.hasUnknownModelCost

fun getLastMainRequestId(): String? = BootstrapStateHolder.STATE.lastMainRequestId

fun setLastMainRequestId(requestId: String) {
    BootstrapStateHolder.STATE.lastMainRequestId = requestId
}

fun getLastApiCompletionTimestamp(): Long? = BootstrapStateHolder.STATE.lastApiCompletionTimestamp

fun setLastApiCompletionTimestamp(timestamp: Long) {
    BootstrapStateHolder.STATE.lastApiCompletionTimestamp = timestamp
}

fun markPostCompaction() {
    BootstrapStateHolder.STATE.pendingPostCompaction = true
}

fun consumePostCompaction(): Boolean {
    val was = BootstrapStateHolder.STATE.pendingPostCompaction
    BootstrapStateHolder.STATE.pendingPostCompaction = false
    return was
}

fun getLastInteractionTime(): Long = BootstrapStateHolder.STATE.lastInteractionTime

fun updateLastInteractionTime(immediate: Boolean = false) {
    if (immediate) {
        flushInteractionTimeInner()
    } else {
        interactionTimeDirty = true
    }
}

fun flushInteractionTime() {
    if (interactionTimeDirty) {
        flushInteractionTimeInner()
    }
}

private fun flushInteractionTimeInner() {
    BootstrapStateHolder.STATE.lastInteractionTime = System.currentTimeMillis()
    interactionTimeDirty = false
}

fun markScrollActivity() {
    scrollDraining = true
    scrollDrainTimer?.interrupt()
    scrollDrainTimer = Thread {
        Thread.sleep(SCROLL_DRAIN_IDLE_MS)
        scrollDraining = false
    }
    scrollDrainTimer?.start()
}

fun getIsScrollDraining(): Boolean = scrollDraining

suspend fun waitForScrollIdle() {
    while (scrollDraining) {
        try {
            Thread.sleep(SCROLL_DRAIN_IDLE_MS)
        } catch (e: InterruptedException) {
            break
        }
    }
}

fun getModelUsage(): Map<String, ModelUsage> = BootstrapStateHolder.STATE.modelUsage

fun getUsageForModel(model: String): ModelUsage? = BootstrapStateHolder.STATE.modelUsage[model]

fun getMainLoopModelOverride(): String? = BootstrapStateHolder.STATE.mainLoopModelOverride

fun getInitialMainLoopModel(): String? = BootstrapStateHolder.STATE.initialMainLoopModel

fun setMainLoopModelOverride(model: String?) {
    BootstrapStateHolder.STATE.mainLoopModelOverride = model
}

fun setInitialMainLoopModel(model: String) {
    BootstrapStateHolder.STATE.initialMainLoopModel = model
}

fun getSdkBetas(): List<String>? = BootstrapStateHolder.STATE.sdkBetas

fun setSdkBetas(betas: List<String>?) {
    BootstrapStateHolder.STATE.sdkBetas = betas
}

fun resetCostState() {
    BootstrapStateHolder.STATE.totalCostUSD = 0.0
    BootstrapStateHolder.STATE.totalAPIDuration = 0
    BootstrapStateHolder.STATE.totalAPIDurationWithoutRetries = 0
    BootstrapStateHolder.STATE.totalToolDuration = 0
    BootstrapStateHolder.STATE.startTime = System.currentTimeMillis()
    BootstrapStateHolder.STATE.totalLinesAdded = 0
    BootstrapStateHolder.STATE.totalLinesRemoved = 0
    BootstrapStateHolder.STATE.hasUnknownModelCost = false
    BootstrapStateHolder.STATE.modelUsage.clear()
    BootstrapStateHolder.STATE.promptId = null
}

fun setCostStateForRestore(
    totalCostUSD: Double,
    totalAPIDuration: Long,
    totalAPIDurationWithoutRetries: Long,
    totalToolDuration: Long,
    totalLinesAdded: Int,
    totalLinesRemoved: Int,
    lastDuration: Long?,
    modelUsage: Map<String, ModelUsage>?
) {
    BootstrapStateHolder.STATE.totalCostUSD = totalCostUSD
    BootstrapStateHolder.STATE.totalAPIDuration = totalAPIDuration
    BootstrapStateHolder.STATE.totalAPIDurationWithoutRetries = totalAPIDurationWithoutRetries
    BootstrapStateHolder.STATE.totalToolDuration = totalToolDuration
    BootstrapStateHolder.STATE.totalLinesAdded = totalLinesAdded
    BootstrapStateHolder.STATE.totalLinesRemoved = totalLinesRemoved
    
    if (modelUsage != null) {
        BootstrapStateHolder.STATE.modelUsage.clear()
        BootstrapStateHolder.STATE.modelUsage.putAll(modelUsage)
    }
    
    if (lastDuration != null) {
        BootstrapStateHolder.STATE.startTime = System.currentTimeMillis() - lastDuration
    }
}

fun getModelStrings(): String? = BootstrapStateHolder.STATE.modelStrings

fun setModelStrings(modelStrings: String) {
    BootstrapStateHolder.STATE.modelStrings = modelStrings
}

fun resetModelStringsForTestingOnly() {
    BootstrapStateHolder.STATE.modelStrings = null
}

fun getIsNonInteractiveSession(): Boolean = !BootstrapStateHolder.STATE.isInteractive

fun getIsInteractive(): Boolean = BootstrapStateHolder.STATE.isInteractive

fun setIsInteractive(value: Boolean) {
    BootstrapStateHolder.STATE.isInteractive = value
}

fun getClientType(): String = BootstrapStateHolder.STATE.clientType

fun setClientType(type: String) {
    BootstrapStateHolder.STATE.clientType = type
}

fun getSdkAgentProgressSummariesEnabled(): Boolean = BootstrapStateHolder.STATE.sdkAgentProgressSummariesEnabled

fun setSdkAgentProgressSummariesEnabled(value: Boolean) {
    BootstrapStateHolder.STATE.sdkAgentProgressSummariesEnabled = value
}

fun getKairosActive(): Boolean = BootstrapStateHolder.STATE.kairosActive

fun setKairosActive(value: Boolean) {
    BootstrapStateHolder.STATE.kairosActive = value
}

fun getStrictToolResultPairing(): Boolean = BootstrapStateHolder.STATE.strictToolResultPairing

fun setStrictToolResultPairing(value: Boolean) {
    BootstrapStateHolder.STATE.strictToolResultPairing = value
}

fun getUserMsgOptIn(): Boolean = BootstrapStateHolder.STATE.userMsgOptIn

fun setUserMsgOptIn(value: Boolean) {
    BootstrapStateHolder.STATE.userMsgOptIn = value
}

fun getSessionSource(): String? = BootstrapStateHolder.STATE.sessionSource

fun setSessionSource(source: String) {
    BootstrapStateHolder.STATE.sessionSource = source
}

fun getQuestionPreviewFormat(): String? = BootstrapStateHolder.STATE.questionPreviewFormat

fun setQuestionPreviewFormat(format: String) {
    BootstrapStateHolder.STATE.questionPreviewFormat = format
}

fun getFlagSettingsPath(): String? = BootstrapStateHolder.STATE.flagSettingsPath

fun setFlagSettingsPath(path: String?) {
    BootstrapStateHolder.STATE.flagSettingsPath = path
}

fun getFlagSettingsInline(): Map<String, Any>? = BootstrapStateHolder.STATE.flagSettingsInline

fun setFlagSettingsInline(settings: Map<String, Any>?) {
    BootstrapStateHolder.STATE.flagSettingsInline = settings
}

fun getSessionIngressToken(): String? = BootstrapStateHolder.STATE.sessionIngressToken

fun setSessionIngressToken(token: String?) {
    BootstrapStateHolder.STATE.sessionIngressToken = token
}

fun getOauthTokenFromFd(): String? = BootstrapStateHolder.STATE.oauthTokenFromFd

fun setOauthTokenFromFd(token: String?) {
    BootstrapStateHolder.STATE.oauthTokenFromFd = token
}

fun getApiKeyFromFd(): String? = BootstrapStateHolder.STATE.apiKeyFromFd

fun setApiKeyFromFd(key: String?) {
    BootstrapStateHolder.STATE.apiKeyFromFd = key
}

fun getAllowedSettingSources(): List<String> = BootstrapStateHolder.STATE.allowedSettingSources

fun setAllowedSettingSources(sources: List<String>) {
    BootstrapStateHolder.STATE.allowedSettingSources = sources
}

fun preferThirdPartyAuthentication(): Boolean {
    return getIsNonInteractiveSession() && BootstrapStateHolder.STATE.clientType != "claude-vscode"
}

fun setInlinePlugins(plugins: List<String>) {
    BootstrapStateHolder.STATE.inlinePlugins = plugins
}

fun getInlinePlugins(): List<String> = BootstrapStateHolder.STATE.inlinePlugins

fun setChromeFlagOverride(value: Boolean?) {
    BootstrapStateHolder.STATE.chromeFlagOverride = value
}

fun getChromeFlagOverride(): Boolean? = BootstrapStateHolder.STATE.chromeFlagOverride

fun setUseCoworkPlugins(value: Boolean) {
    BootstrapStateHolder.STATE.useCoworkPlugins = value
}

fun getUseCoworkPlugins(): Boolean = BootstrapStateHolder.STATE.useCoworkPlugins

fun setSessionBypassPermissionsMode(enabled: Boolean) {
    BootstrapStateHolder.STATE.sessionBypassPermissionsMode = enabled
}

fun getSessionBypassPermissionsMode(): Boolean = BootstrapStateHolder.STATE.sessionBypassPermissionsMode

fun setScheduledTasksEnabled(enabled: Boolean) {
    BootstrapStateHolder.STATE.scheduledTasksEnabled = enabled
}

fun getScheduledTasksEnabled(): Boolean = BootstrapStateHolder.STATE.scheduledTasksEnabled

fun getSessionCronTasks(): List<SessionCronTask> = BootstrapStateHolder.STATE.sessionCronTasks

fun addSessionCronTask(task: SessionCronTask) {
    BootstrapStateHolder.STATE.sessionCronTasks.add(task)
}

fun removeSessionCronTasks(ids: List<String>): Int {
    if (ids.isEmpty()) return 0
    val idSet = ids.toSet()
    val remaining = BootstrapStateHolder.STATE.sessionCronTasks.filter { it.id !in idSet }
    val removed = BootstrapStateHolder.STATE.sessionCronTasks.size - remaining.size
    if (removed == 0) return 0
    BootstrapStateHolder.STATE.sessionCronTasks.clear()
    BootstrapStateHolder.STATE.sessionCronTasks.addAll(remaining)
    return removed
}

fun setSessionTrustAccepted(accepted: Boolean) {
    BootstrapStateHolder.STATE.sessionTrustAccepted = accepted
}

fun getSessionTrustAccepted(): Boolean = BootstrapStateHolder.STATE.sessionTrustAccepted

fun setSessionPersistenceDisabled(disabled: Boolean) {
    BootstrapStateHolder.STATE.sessionPersistenceDisabled = disabled
}

fun isSessionPersistenceDisabled(): Boolean = BootstrapStateHolder.STATE.sessionPersistenceDisabled

fun hasExitedPlanModeInSession(): Boolean = BootstrapStateHolder.STATE.hasExitedPlanMode

fun setHasExitedPlanMode(value: Boolean) {
    BootstrapStateHolder.STATE.hasExitedPlanMode = value
}

fun needsPlanModeExitAttachment(): Boolean = BootstrapStateHolder.STATE.needsPlanModeExitAttachment

fun setNeedsPlanModeExitAttachment(value: Boolean) {
    BootstrapStateHolder.STATE.needsPlanModeExitAttachment = value
}

fun handlePlanModeTransition(fromMode: String, toMode: String) {
    if (toMode == "plan" && fromMode != "plan") {
        BootstrapStateHolder.STATE.needsPlanModeExitAttachment = false
    }
    if (fromMode == "plan" && toMode != "plan") {
        BootstrapStateHolder.STATE.needsPlanModeExitAttachment = true
    }
}

fun needsAutoModeExitAttachment(): Boolean = BootstrapStateHolder.STATE.needsAutoModeExitAttachment

fun setNeedsAutoModeExitAttachment(value: Boolean) {
    BootstrapStateHolder.STATE.needsAutoModeExitAttachment = value
}

fun handleAutoModeTransition(fromMode: String, toMode: String) {
    if ((fromMode == "auto" && toMode == "plan") || (fromMode == "plan" && toMode == "auto")) {
        return
    }
    val fromIsAuto = fromMode == "auto"
    val toIsAuto = toMode == "auto"
    
    if (toIsAuto && !fromIsAuto) {
        BootstrapStateHolder.STATE.needsAutoModeExitAttachment = false
    }
    if (fromIsAuto && !toIsAuto) {
        BootstrapStateHolder.STATE.needsAutoModeExitAttachment = true
    }
}

fun hasShownLspRecommendationThisSession(): Boolean = BootstrapStateHolder.STATE.lspRecommendationShownThisSession

fun setLspRecommendationShownThisSession(value: Boolean) {
    BootstrapStateHolder.STATE.lspRecommendationShownThisSession = value
}

fun setInitJsonSchema(schema: Map<String, Any>) {
    BootstrapStateHolder.STATE.initJsonSchema = schema
}

fun getInitJsonSchema(): Map<String, Any>? = BootstrapStateHolder.STATE.initJsonSchema

fun getPlanSlugCache(): Map<String, String> = BootstrapStateHolder.STATE.planSlugCache

fun getSessionCreatedTeams(): Set<String> = BootstrapStateHolder.STATE.sessionCreatedTeams

fun setTeleportedSessionInfo(sessionId: String?) {
    BootstrapStateHolder.STATE.teleportedSessionInfo = TeleportedSessionInfo(
        isTeleported = true,
        hasLoggedFirstMessage = false,
        sessionId = sessionId
    )
}

fun getTeleportedSessionInfo(): TeleportedSessionInfo? = BootstrapStateHolder.STATE.teleportedSessionInfo

fun markFirstTeleportMessageLogged() {
    BootstrapStateHolder.STATE.teleportedSessionInfo?.let {
        BootstrapStateHolder.STATE.teleportedSessionInfo = it.copy(hasLoggedFirstMessage = true)
    }
}

fun addInvokedSkill(
    skillName: String,
    skillPath: String,
    content: String,
    agentId: String? = null
) {
    val key = "${agentId ?: ""}:$skillName"
    BootstrapStateHolder.STATE.invokedSkills[key] = InvokedSkillInfo(
        skillName = skillName,
        skillPath = skillPath,
        content = content,
        invokedAt = System.currentTimeMillis(),
        agentId = agentId
    )
}

fun getInvokedSkills(): Map<String, InvokedSkillInfo> = BootstrapStateHolder.STATE.invokedSkills

fun getInvokedSkillsForAgent(agentId: String?): Map<String, InvokedSkillInfo> {
    val normalizedId = agentId
    return BootstrapStateHolder.STATE.invokedSkills.filter { it.value.agentId == normalizedId }
}

fun clearInvokedSkills(preservedAgentIds: Set<String>? = null) {
    if (preservedAgentIds == null || preservedAgentIds.isEmpty()) {
        BootstrapStateHolder.STATE.invokedSkills.clear()
        return
    }
    BootstrapStateHolder.STATE.invokedSkills.entries.removeIf { (_, skill) ->
        skill.agentId == null || skill.agentId !in preservedAgentIds
    }
}

fun clearInvokedSkillsForAgent(agentId: String) {
    BootstrapStateHolder.STATE.invokedSkills.entries.removeIf { (_, skill) ->
        skill.agentId == agentId
    }
}

fun addSlowOperation(operation: String, durationMs: Long) {
    val now = System.currentTimeMillis()
    BootstrapStateHolder.STATE.slowOperations.removeAll { op ->
        now - op.timestamp >= SLOW_OPERATION_TTL_MS
    }
    BootstrapStateHolder.STATE.slowOperations.add(
        SlowOperation(operation, durationMs, now)
    )
    while (BootstrapStateHolder.STATE.slowOperations.size > MAX_SLOW_OPERATIONS) {
        BootstrapStateHolder.STATE.slowOperations.removeAt(0)
    }
}

fun getSlowOperations(): List<SlowOperation> {
    if (BootstrapStateHolder.STATE.slowOperations.isEmpty()) {
        return emptyList()
    }
    val now = System.currentTimeMillis()
    BootstrapStateHolder.STATE.slowOperations.removeAll { op ->
        now - op.timestamp >= SLOW_OPERATION_TTL_MS
    }
    return BootstrapStateHolder.STATE.slowOperations.toList()
}

fun getMainThreadAgentType(): String? = BootstrapStateHolder.STATE.mainThreadAgentType

fun setMainThreadAgentType(agentType: String?) {
    BootstrapStateHolder.STATE.mainThreadAgentType = agentType
}

fun getIsRemoteMode(): Boolean = BootstrapStateHolder.STATE.isRemoteMode

fun setIsRemoteMode(value: Boolean) {
    BootstrapStateHolder.STATE.isRemoteMode = value
}

fun getSystemPromptSectionCache(): Map<String, String?> = BootstrapStateHolder.STATE.systemPromptSectionCache

fun setSystemPromptSectionCacheEntry(name: String, value: String?) {
    BootstrapStateHolder.STATE.systemPromptSectionCache[name] = value
}

fun clearSystemPromptSectionState() {
    BootstrapStateHolder.STATE.systemPromptSectionCache.clear()
}

fun getLastEmittedDate(): String? = BootstrapStateHolder.STATE.lastEmittedDate

fun setLastEmittedDate(date: String?) {
    BootstrapStateHolder.STATE.lastEmittedDate = date
}

fun getAdditionalDirectoriesForClaudeMd(): List<String> = BootstrapStateHolder.STATE.additionalDirectoriesForClaudeMd

fun setAdditionalDirectoriesForClaudeMd(directories: List<String>) {
    BootstrapStateHolder.STATE.additionalDirectoriesForClaudeMd = directories
}

fun getAllowedChannels(): List<ChannelEntry> = BootstrapStateHolder.STATE.allowedChannels

fun setAllowedChannels(entries: List<ChannelEntry>) {
    BootstrapStateHolder.STATE.allowedChannels = entries
}

fun getHasDevChannels(): Boolean = BootstrapStateHolder.STATE.hasDevChannels

fun setHasDevChannels(value: Boolean) {
    BootstrapStateHolder.STATE.hasDevChannels = value
}

fun getPromptCache1hAllowlist(): List<String>? = BootstrapStateHolder.STATE.promptCache1hAllowlist

fun setPromptCache1hAllowlist(allowlist: List<String>?) {
    BootstrapStateHolder.STATE.promptCache1hAllowlist = allowlist
}

fun getPromptCache1hEligible(): Boolean? = BootstrapStateHolder.STATE.promptCache1hEligible

fun setPromptCache1hEligible(eligible: Boolean?) {
    BootstrapStateHolder.STATE.promptCache1hEligible = eligible
}

fun getAfkModeHeaderLatched(): Boolean? = BootstrapStateHolder.STATE.afkModeHeaderLatched

fun setAfkModeHeaderLatched(v: Boolean) {
    BootstrapStateHolder.STATE.afkModeHeaderLatched = v
}

fun getFastModeHeaderLatched(): Boolean? = BootstrapStateHolder.STATE.fastModeHeaderLatched

fun setFastModeHeaderLatched(v: Boolean) {
    BootstrapStateHolder.STATE.fastModeHeaderLatched = v
}

fun getCacheEditingHeaderLatched(): Boolean? = BootstrapStateHolder.STATE.cacheEditingHeaderLatched

fun setCacheEditingHeaderLatched(v: Boolean) {
    BootstrapStateHolder.STATE.cacheEditingHeaderLatched = v
}

fun getThinkingClearLatched(): Boolean? = BootstrapStateHolder.STATE.thinkingClearLatched

fun setThinkingClearLatched(v: Boolean) {
    BootstrapStateHolder.STATE.thinkingClearLatched = v
}

fun clearBetaHeaderLatches() {
    BootstrapStateHolder.STATE.afkModeHeaderLatched = null
    BootstrapStateHolder.STATE.fastModeHeaderLatched = null
    BootstrapStateHolder.STATE.cacheEditingHeaderLatched = null
    BootstrapStateHolder.STATE.thinkingClearLatched = null
}

fun getPromptId(): String? = BootstrapStateHolder.STATE.promptId

fun setPromptId(id: String?) {
    BootstrapStateHolder.STATE.promptId = id
}

fun addToInMemoryErrorLog(errorInfo: InMemoryError) {
    val maxInMemoryErrors = 100
    if (BootstrapStateHolder.STATE.inMemoryErrorLog.size >= maxInMemoryErrors) {
        BootstrapStateHolder.STATE.inMemoryErrorLog.removeAt(0)
    }
    BootstrapStateHolder.STATE.inMemoryErrorLog.add(errorInfo)
}

private fun String.normalize(): String {
    return this.normalize()
}
