package rj.cocacode.tasks

object PillLabel {
    private const val DIAMOND_OPEN = "◇"
    private const val DIAMOND_FILLED = "◆"
    
    fun getPillLabel(tasks: List<BackgroundTaskState>): String {
        val n = tasks.size
        if (n == 0) return ""
        
        val allSameType = tasks.all { it.type == tasks[0].type }
        
        if (allSameType) {
            return when (tasks[0].type) {
                "local_bash" -> {
                    val monitors = tasks.count { it is LocalShellTaskState && it.command.contains("monitor") }
                    val shells = n - monitors
                    buildString {
                        if (shells > 0) append(if (shells == 1) "1 shell" else "$shells shells")
                        if (monitors > 0) {
                            if (shells > 0) append(", ")
                            append(if (monitors == 1) "1 monitor" else "$monitors monitors")
                        }
                    }
                }
                "in_process_teammate" -> {
                    val teamCount = tasks.filterIsInstance<InProcessTeammateTaskState>()
                        .map { it.teamName }.distinct().size
                    if (teamCount == 1) "1 team" else "$teamCount teams"
                }
                "local_agent" -> if (n == 1) "1 local agent" else "$n local agents"
                "remote_agent" -> {
                    if (n == 1) {
                        val first = tasks[0] as? RemoteAgentTaskState
                        if (first?.isUltraplan == true) {
                            return when (first.ultraplanPhase) {
                                "plan_ready" -> "$DIAMOND_FILLED ultraplan ready"
                                "needs_input" -> "$DIAMOND_OPEN ultraplan needs your input"
                                else -> "$DIAMOND_OPEN ultraplan"
                            }
                        }
                        return "$DIAMOND_OPEN 1 cloud session"
                    }
                    "$DIAMOND_OPEN $n cloud sessions"
                }
                "local_workflow" -> if (n == 1) "1 background workflow" else "$n background workflows"
                "monitor_mcp" -> if (n == 1) "1 monitor" else "$n monitors"
                "dream" -> "dreaming"
                else -> "$n background ${if (n == 1) "task" else "tasks"}"
            }
        }
        
        return "$n background ${if (n == 1) "task" else "tasks"}"
    }
    
    fun pillNeedsCta(tasks: List<BackgroundTaskState>): Boolean {
        if (tasks.size != 1) return false
        val t = tasks[0]
        return t is RemoteAgentTaskState && t.isUltraplan && t.ultraplanPhase != null
    }
}