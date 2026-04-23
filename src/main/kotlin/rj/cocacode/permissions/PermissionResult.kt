package rj.cocacode.permissions

sealed class PermissionResult {
    abstract val behavior: PermissionBehavior
}

data class PermissionAllowDecision(
    val updatedInput: Map<String, Any>? = null,
    val userModified: Boolean = false,
    val decisionReason: PermissionDecisionReason? = null,
    val toolUseID: String? = null,
    val acceptFeedback: String? = null,
    val contentBlocks: List<Map<String, Any>>? = null
) : PermissionResult() {
    override val behavior: PermissionBehavior = PermissionBehavior.ALLOW
}

data class PermissionAskDecision(
    val message: String,
    val updatedInput: Map<String, Any>? = null,
    val decisionReason: PermissionDecisionReason? = null,
    val suggestions: List<PermissionUpdate>? = null,
    val blockedPath: String? = null,
    val metadata: PermissionMetadata? = null,
    val isBashSecurityCheckForMisparsing: Boolean = false,
    val pendingClassifierCheck: PendingClassifierCheck? = null,
    val contentBlocks: List<Map<String, Any>>? = null
) : PermissionResult() {
    override val behavior: PermissionBehavior = PermissionBehavior.ASK
}

data class PermissionDenyDecision(
    val message: String,
    val decisionReason: PermissionDecisionReason,
    val toolUseID: String? = null
) : PermissionResult() {
    override val behavior: PermissionBehavior = PermissionBehavior.DENY
}

data class PermissionPassthroughResult(
    val message: String,
    val decisionReason: PermissionDecisionReason? = null,
    val suggestions: List<PermissionUpdate>? = null,
    val blockedPath: String? = null,
    val pendingClassifierCheck: PendingClassifierCheck? = null
) : PermissionResult() {
    override val behavior: PermissionBehavior = PermissionBehavior.ALLOW
}

data class PermissionMetadata(
    val command: PermissionCommandMetadata
)

data class PermissionCommandMetadata(
    val name: String,
    val description: String? = null,
    val extra: Map<String, Any> = emptyMap()
)

data class PendingClassifierCheck(
    val command: String,
    val cwd: String,
    val descriptions: List<String>
)

sealed class PermissionDecisionReason {
    data class RuleReason(
        val rule: PermissionRule
    ) : PermissionDecisionReason()

    data class ModeReason(
        val mode: PermissionMode
    ) : PermissionDecisionReason()

    data class SubcommandResultsReason(
        val reasons: Map<String, PermissionResult>
    ) : PermissionDecisionReason()

    data class PermissionPromptToolReason(
        val permissionPromptToolName: String,
        val toolResult: Any? = null
    ) : PermissionDecisionReason()

    data class HookReason(
        val hookName: String,
        val hookSource: String? = null,
        val reason: String? = null
    ) : PermissionDecisionReason()

    data class AsyncAgentReason(
        val reason: String
    ) : PermissionDecisionReason()

    data class SandboxOverrideReason(
        val reason: String
    ) : PermissionDecisionReason()

    data class ClassifierReason(
        val classifier: String,
        val reason: String
    ) : PermissionDecisionReason()

    data class WorkingDirReason(
        val reason: String
    ) : PermissionDecisionReason()

    data class SafetyCheckReason(
        val reason: String,
        val classifierApprovable: Boolean = false
    ) : PermissionDecisionReason()

    data class OtherReason(
        val reason: String
    ) : PermissionDecisionReason()
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class PermissionExplanation(
    val riskLevel: RiskLevel,
    val explanation: String,
    val reasoning: String,
    val risk: String
)

fun getRuleBehaviorDescription(behavior: PermissionBehavior): String {
    return when (behavior) {
        PermissionBehavior.ALLOW -> "allowed"
        PermissionBehavior.DENY -> "denied"
        PermissionBehavior.ASK -> "asked for confirmation for"
    }
}

sealed class PermissionUpdate {
    data class AddRules(
        val destination: PermissionRuleSource,
        val rules: List<PermissionRuleValue>,
        val behavior: PermissionBehavior
    ) : PermissionUpdate()

    data class ReplaceRules(
        val destination: PermissionRuleSource,
        val rules: List<PermissionRuleValue>,
        val behavior: PermissionBehavior
    ) : PermissionUpdate()

    data class RemoveRules(
        val destination: PermissionRuleSource,
        val rules: List<PermissionRuleValue>,
        val behavior: PermissionBehavior
    ) : PermissionUpdate()

    data class SetMode(
        val destination: PermissionRuleSource,
        val mode: ExternalPermissionMode
    ) : PermissionUpdate()

    data class AddDirectories(
        val destination: PermissionRuleSource,
        val directories: List<String>
    ) : PermissionUpdate()

    data class RemoveDirectories(
        val destination: PermissionRuleSource,
        val directories: List<String>
    ) : PermissionUpdate()
}

enum class PermissionUpdateDestination {
    USER_SETTINGS,
    PROJECT_SETTINGS,
    LOCAL_SETTINGS,
    SESSION,
    CLI_ARG
}
