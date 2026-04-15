package rj.cocacode.permissions

fun createPermissionRequestMessage(
    toolName: String,
    decisionReason: PermissionDecisionReason? = null
): String {
    return when (decisionReason) {
        is PermissionDecisionReason.RuleReason -> {
            val ruleString = decisionReason.rule.ruleValue.toDisplayString()
            val sourceString = PermissionRuleSource.toDisplayString(decisionReason.rule.source)
            "Permission rule '$ruleString' from $sourceString requires approval for this $toolName command"
        }
        is PermissionDecisionReason.ModeReason -> {
            val modeTitle = permissionModeTitle(decisionReason.mode)
            "Current permission mode ($modeTitle) requires approval for this $toolName command"
        }
        is PermissionDecisionReason.ClassifierReason -> {
            "Classifier '${decisionReason.classifier}' requires approval for this $toolName command: ${decisionReason.reason}"
        }
        is PermissionDecisionReason.HookReason -> {
            if (decisionReason.reason != null) {
                "Hook '${decisionReason.hookName}' blocked this action: ${decisionReason.reason}"
            } else {
                "Hook '${decisionReason.hookName}' requires approval for this $toolName command"
            }
        }
        is PermissionDecisionReason.SubcommandResultsReason -> {
            val needsApproval = decisionReason.reasons.keys.toList()
            if (needsApproval.isNotEmpty()) {
                val n = needsApproval.size
                val parts = if (n == 1) "part requires" else "parts require"
                "This $toolName command contains multiple operations. The following $n $parts approval: ${needsApproval.joinToString(", ")}"
            } else {
                "This $toolName command contains multiple operations that require approval"
            }
        }
        is PermissionDecisionReason.PermissionPromptToolReason -> {
            "Tool '${decisionReason.permissionPromptToolName}' requires approval for this $toolName command"
        }
        is PermissionDecisionReason.SandboxOverrideReason -> {
            "Run outside of the sandbox"
        }
        is PermissionDecisionReason.WorkingDirReason -> {
            decisionReason.reason
        }
        is PermissionDecisionReason.SafetyCheckReason,
        is PermissionDecisionReason.OtherReason -> {
            decisionReason.reason
        }
        is PermissionDecisionReason.AsyncAgentReason -> {
            decisionReason.reason
        }
        null -> {
            "Claude requested permissions to use $toolName, but you haven't granted it yet."
        }
    }
}

fun permissionRuleSourceDisplayString(source: PermissionRuleSource): String {
    return PermissionRuleSource.toDisplayString(source)
}

private fun toolMatchesRule(
    toolName: String,
    rule: PermissionRule
): Boolean {
    if (rule.ruleValue.ruleContent != null) {
        return false
    }
    return rule.ruleValue.toolName == toolName
}

fun getRuleByContentsForTool(
    context: ToolPermissionContext,
    toolName: String,
    behavior: PermissionBehavior
): Map<String, PermissionRule> {
    val ruleByContents = mutableMapOf<String, PermissionRule>()
    val rules = when (behavior) {
        PermissionBehavior.ALLOW -> context.getAllowRules()
        PermissionBehavior.DENY -> context.getDenyRules()
        PermissionBehavior.ASK -> context.getAskRules()
    }
    for (rule in rules) {
        if (rule.ruleValue.toolName == toolName &&
            rule.ruleValue.ruleContent != null &&
            rule.ruleBehavior == behavior
        ) {
            ruleByContents[rule.ruleValue.ruleContent!!] = rule
        }
    }
    return ruleByContents
}

fun getDenyRuleForAgent(
    context: ToolPermissionContext,
    agentToolName: String,
    agentType: String
): PermissionRule? {
    return context.getDenyRules().find { rule ->
        rule.ruleValue.toolName == agentToolName &&
        rule.ruleValue.ruleContent == agentType
    }
}

fun <T : Any> filterDeniedAgents(
    agents: List<T>,
    context: ToolPermissionContext,
    agentToolName: String,
    getAgentType: (T) -> String
): List<T> {
    val deniedAgentTypes = mutableSetOf<String>()
    for (rule in context.getDenyRules()) {
        if (rule.ruleValue.toolName == agentToolName &&
            rule.ruleValue.ruleContent != null
        ) {
            deniedAgentTypes.add(rule.ruleValue.ruleContent!!)
        }
    }
    return agents.filter { agent -> !deniedAgentTypes.contains(getAgentType(agent)) }
}

class PermissionChecker(
    private val context: ToolPermissionContext
) {
    fun hasPermissionsToUseTool(toolName: String): PermissionResult {
        val denyRule = context.getDenyRuleForTool(toolName)
        if (denyRule != null) {
            return PermissionDenyDecision(
                message = "Permission to use $toolName has been denied.",
                decisionReason = PermissionDecisionReason.RuleReason(denyRule)
            )
        }

        val askRule = context.getAskRuleForTool(toolName)
        if (askRule != null) {
            return PermissionAskDecision(
                message = createPermissionRequestMessage(toolName),
                decisionReason = PermissionDecisionReason.RuleReason(askRule)
            )
        }

        val alwaysAllowedRule = context.getToolAlwaysAllowedRule(toolName)
        if (alwaysAllowedRule != null) {
            return PermissionAllowDecision(
                decisionReason = PermissionDecisionReason.RuleReason(alwaysAllowedRule)
            )
        }

        return PermissionPassthroughResult(
            message = createPermissionRequestMessage(toolName)
        )
    }

    fun checkRuleBasedPermissions(toolName: String): PermissionResult? {
        val denyRule = context.getDenyRuleForTool(toolName)
        if (denyRule != null) {
            return PermissionDenyDecision(
                message = "Permission to use $toolName has been denied.",
                decisionReason = PermissionDecisionReason.RuleReason(denyRule)
            )
        }

        val askRule = context.getAskRuleForTool(toolName)
        if (askRule != null) {
            return PermissionAskDecision(
                message = createPermissionRequestMessage(toolName),
                decisionReason = PermissionDecisionReason.RuleReason(askRule)
            )
        }

        return null
    }

    fun shouldBypassPermissions(): Boolean {
        return context.mode == PermissionMode.BYPASS_PERMISSIONS ||
               (context.mode == PermissionMode.PLAN && context.isBypassPermissionsModeAvailable)
    }
}

fun applyPermissionUpdate(
    context: ToolPermissionContext,
    update: PermissionUpdate
): ToolPermissionContext {
    return when (update) {
        is PermissionUpdate.AddRules -> {
            val currentRules = when (update.behavior) {
                PermissionBehavior.ALLOW -> context.alwaysAllowRules
                PermissionBehavior.DENY -> context.alwaysDenyRules
                PermissionBehavior.ASK -> context.alwaysAskRules
            }.toMutableMap()
            
            val existingRules = currentRules[update.destination] ?: emptyList()
            val newRules = existingRules + update.rules.map { it.toDisplayString() }
            currentRules[update.destination] = newRules
            
            when (update.behavior) {
                PermissionBehavior.ALLOW -> context.copy(alwaysAllowRules = currentRules)
                PermissionBehavior.DENY -> context.copy(alwaysDenyRules = currentRules)
                PermissionBehavior.ASK -> context.copy(alwaysAskRules = currentRules)
            }
        }
        
        is PermissionUpdate.ReplaceRules -> {
            val newRules = update.rules.map { it.toDisplayString() }
            val currentRules = when (update.behavior) {
                PermissionBehavior.ALLOW -> context.alwaysAllowRules.toMutableMap()
                PermissionBehavior.DENY -> context.alwaysDenyRules.toMutableMap()
                PermissionBehavior.ASK -> context.alwaysAskRules.toMutableMap()
            }
            currentRules[update.destination] = newRules
            
            when (update.behavior) {
                PermissionBehavior.ALLOW -> context.copy(alwaysAllowRules = currentRules)
                PermissionBehavior.DENY -> context.copy(alwaysDenyRules = currentRules)
                PermissionBehavior.ASK -> context.copy(alwaysAskRules = currentRules)
            }
        }
        
        is PermissionUpdate.RemoveRules -> {
            val currentRules = when (update.behavior) {
                PermissionBehavior.ALLOW -> context.alwaysAllowRules.toMutableMap()
                PermissionBehavior.DENY -> context.alwaysDenyRules.toMutableMap()
                PermissionBehavior.ASK -> context.alwaysAskRules.toMutableMap()
            }
            val existingRules = currentRules[update.destination] ?: emptyList()
            val rulesToRemove = update.rules.map { it.toDisplayString() }.toSet()
            currentRules[update.destination] = existingRules.filter { it !in rulesToRemove }
            
            when (update.behavior) {
                PermissionBehavior.ALLOW -> context.copy(alwaysAllowRules = currentRules)
                PermissionBehavior.DENY -> context.copy(alwaysDenyRules = currentRules)
                PermissionBehavior.ASK -> context.copy(alwaysAskRules = currentRules)
            }
        }
        
        is PermissionUpdate.SetMode -> {
            context.copy(mode = PermissionMode.fromString(update.mode.name))
        }
        
        is PermissionUpdate.AddDirectories -> {
            val currentDirs = context.additionalWorkingDirectories.toMutableMap()
            for (dir in update.directories) {
                currentDirs[dir] = AdditionalWorkingDirectory(dir, update.destination)
            }
            context.copy(additionalWorkingDirectories = currentDirs)
        }
        
        is PermissionUpdate.RemoveDirectories -> {
            val currentDirs = context.additionalWorkingDirectories.toMutableMap()
            for (dir in update.directories) {
                currentDirs.remove(dir)
            }
            context.copy(additionalWorkingDirectories = currentDirs)
        }
    }
}

fun applyPermissionUpdates(
    context: ToolPermissionContext,
    updates: List<PermissionUpdate>
): ToolPermissionContext {
    return updates.fold(context) { ctx, update ->
        applyPermissionUpdate(ctx, update)
    }
}

fun convertRulesToUpdates(
    rules: List<PermissionRule>,
    updateType: String
): List<PermissionUpdate> {
    val grouped = mutableMapOf<String, MutableList<PermissionRuleValue>>()
    
    for (rule in rules) {
        val key = "${rule.source.name}:${rule.ruleBehavior.name}"
        grouped.getOrPut(key) { mutableListOf() }.add(rule.ruleValue)
    }
    
    return grouped.map { (key, ruleValues) ->
        val parts = key.split(":")
        val source = PermissionRuleSource.valueOf(parts[0])
        val behavior = PermissionBehavior.valueOf(parts[1])
        
        when (updateType) {
            "addRules" -> PermissionUpdate.AddRules(source, ruleValues, behavior)
            "replaceRules" -> PermissionUpdate.ReplaceRules(source, ruleValues, behavior)
            else -> PermissionUpdate.AddRules(source, ruleValues, behavior)
        }
    }
}

fun applyPermissionRulesToPermissionContext(
    toolPermissionContext: ToolPermissionContext,
    rules: List<PermissionRule>
): ToolPermissionContext {
    val updates = convertRulesToUpdates(rules, "addRules")
    return applyPermissionUpdates(toolPermissionContext, updates)
}

fun syncPermissionRulesFromDisk(
    toolPermissionContext: ToolPermissionContext,
    rules: List<PermissionRule>
): ToolPermissionContext {
    var context = toolPermissionContext
    
    val diskSources = listOf(
        PermissionUpdateDestination.USER_SETTINGS,
        PermissionUpdateDestination.PROJECT_SETTINGS,
        PermissionUpdateDestination.LOCAL_SETTINGS
    )
    val behaviors = listOf(
        PermissionBehavior.ALLOW,
        PermissionBehavior.DENY,
        PermissionBehavior.ASK
    )
    
    for (source in diskSources) {
        for (behavior in behaviors) {
            context = applyPermissionUpdate(context, PermissionUpdate.ReplaceRules(source, emptyList(), behavior))
        }
    }
    
    val updates = convertRulesToUpdates(rules, "replaceRules")
    return applyPermissionUpdates(context, updates)
}
