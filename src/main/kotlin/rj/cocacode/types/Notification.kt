package rj.cocacode.types

/**
 * Notification for user-facing messages.
 */
data class Notification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class NotificationType {
    INFO,
    WARNING,
    ERROR,
    SUCCESS
}
