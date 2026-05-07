package rj.cocacode.tools

import kotlinx.serialization.Serializable

/**
 * Manages undo/redo history for file edits
 * Migrated from Claude Code's EditTool functionality
 */
class UndoManager(private val maxHistorySize: Int = 100) {
    
    private val undoStack = mutableListOf<EditHistoryEntry>()
    private val redoStack = mutableListOf<EditHistoryEntry>()
    
    /**
     * Records an edit operation for undo tracking
     */
    fun recordEdit(
        filePath: String,
        oldContent: String,
        newContent: String,
        undoData: Map<String, Any> = emptyMap()
    ) {
        val entry = EditHistoryEntry(
            filePath = filePath,
            oldContent = oldContent,
            newContent = newContent,
            undoData = undoData,
            timestamp = System.currentTimeMillis()
        )
        
        undoStack.add(entry)
        redoStack.clear()
        
        while (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
    }
    
    /**
     * Undoes the last edit operation
     * Returns the content that should be restored
     */
    fun undo(): EditHistoryEntry? {
        if (undoStack.isEmpty()) return null
        
        val entry = undoStack.removeLast()
        redoStack.add(entry)
        return entry
    }
    
    /**
     * Redoes the previously undone edit
     */
    fun redo(): EditHistoryEntry? {
        if (redoStack.isEmpty()) return null
        
        val entry = redoStack.removeLast()
        undoStack.add(entry)
        return entry
    }
    
    /**
     * Checks if undo is available
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    
    /**
     * Checks if redo is available
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    
    /**
     * Clears all history
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
    
    /**
     * Gets the undo stack size
     */
    fun undoStackSize(): Int = undoStack.size
    
    /**
     * Gets the redo stack size
     */
    fun redoStackSize(): Int = redoStack.size
    
    /**
     * Gets information about the last edit (for display)
     */
    fun getLastEditInfo(): EditInfo? {
        return undoStack.lastOrNull()?.let { entry ->
            EditInfo(
                filePath = entry.filePath,
                description = getEditDescription(entry),
                timestamp = entry.timestamp
            )
        }
    }
    
    private fun getEditDescription(entry: EditHistoryEntry): String {
        val oldLines = entry.oldContent.lines().size
        val newLines = entry.newContent.lines().size
        val diff = newLines - oldLines
        return when {
            diff > 0 -> "+$diff lines"
            diff < 0 -> "$diff lines"
            else -> "modified"
        }
    }
}

@Serializable
data class EditHistoryEntry(
    val filePath: String,
    val oldContent: String,
    val newContent: String,
    val undoData: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

data class EditInfo(
    val filePath: String,
    val description: String,
    val timestamp: Long
)