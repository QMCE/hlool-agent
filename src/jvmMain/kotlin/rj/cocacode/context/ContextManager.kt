package rj.cocacode.context

import rj.cocacode.workspace.Workspace
import rj.cocacode.utils.TokenEstimator

data class Context(
    val workspace: Workspace?,
    val files: List<ContextFile> = emptyList(),
    val commands: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val metadata: Map<String, Any> = emptyMap()
)

data class ContextFile(
    val path: String,
    val content: String,
    val lineCount: Int = content.lines().size,
    val tokenCount: Int = TokenEstimator.estimateTokens(content)
)

object ContextBuilder {
    fun build(options: ContextOptions): Context {
        val files = mutableListOf<ContextFile>()
        
        options.workspace?.let { ws ->
            options.includePatterns.forEach { pattern ->
                ws.findFiles(pattern).forEach { filePath ->
                    ws.readFile(filePath)?.let { content ->
                        if (TokenEstimator.estimateTokens(content) <= options.maxFileTokens) {
                            files.add(ContextFile(filePath, content))
                        }
                    }
                }
            }
            
            options.excludePatterns.forEach { pattern ->
                val toExclude = ws.findFiles(pattern).toSet()
                files.removeAll { it.path in toExclude }
            }
        }
        
        return Context(
            workspace = options.workspace,
            files = files,
            commands = options.commands,
            environment = System.getenv(),
            metadata = mapOf(
                "tokenLimit" to options.maxContextTokens,
                "totalTokens" to files.sumOf { it.tokenCount }
            )
        )
    }
    
    fun estimateTokens(context: Context): Int {
        val filesTokens = context.files.sumOf { it.tokenCount }
        val commandsTokens = context.commands.sumOf { TokenEstimator.estimateTokens(it) }
        return filesTokens + commandsTokens
    }
    
    fun truncateToFit(context: Context, maxTokens: Int): Context {
        var current = context
        while (estimateTokens(current) > maxTokens && current.files.isNotEmpty()) {
            val smallest = current.files.minByOrNull { it.tokenCount }!!
            current = current.copy(files = current.files - smallest)
        }
        return current
    }
}

data class ContextOptions(
    val workspace: Workspace? = null,
    val includePatterns: List<String> = listOf("*.kt", "*.java", "*.ts", "*.js", "*.py", "*.md"),
    val excludePatterns: List<String> = listOf(".*", "node_modules", "target", "build", "*.class", "*.jar"),
    val maxContextTokens: Int = 100000,
    val maxFileTokens: Int = 10000,
    val commands: List<String> = emptyList()
)

class ContextManager {
    private val contextStack = mutableListOf<Context>()
    private var currentContext: Context? = null
    
    fun push(context: Context) {
        contextStack.add(context)
        currentContext = context
    }
    
    fun pop(): Context? {
        return if (contextStack.isNotEmpty()) {
            val removed = contextStack.removeAt(contextStack.size - 1)
            currentContext = contextStack.lastOrNull()
            removed
        } else null
    }
    
    fun getCurrent(): Context? = currentContext
    
    fun clear() {
        contextStack.clear()
        currentContext = null
    }
}