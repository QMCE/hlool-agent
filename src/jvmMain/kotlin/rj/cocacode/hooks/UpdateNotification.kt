package rj.cocacode.hooks

class UpdateNotificationService(private val initialVersion: String) {
    private var lastNotifiedSemver: String = extractSemver(initialVersion)
    
    private fun extractSemver(version: String): String {
        val parts = version.split(".")
        return when {
            parts.size >= 3 -> "${parts[0]}.${parts[1]}.${parts[2]}"
            parts.size == 2 -> "${parts[0]}.${parts[1]}.0"
            parts.size == 1 -> "${parts[0]}.0.0"
            else -> "0.0.0"
        }
    }
    
    fun checkUpdate(updatedVersion: String): String? {
        if (updatedVersion.isEmpty()) return null
        
        val updatedSemver = extractSemver(updatedVersion)
        if (updatedSemver != lastNotifiedSemver) {
            lastNotifiedSemver = updatedSemver
            return updatedSemver
        }
        return null
    }
    
    fun shouldShowUpdateNotification(updatedVersion: String): Boolean {
        if (updatedVersion.isEmpty()) return false
        val updatedSemver = extractSemver(updatedVersion)
        return updatedSemver != lastNotifiedSemver
    }
}