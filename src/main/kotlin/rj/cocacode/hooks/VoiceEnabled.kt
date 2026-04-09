package rj.cocacode.hooks

object VoiceEnabled {
    private var authVersion: Int = 0
    
    fun setAuthVersion(version: Int) {
        authVersion = version
    }
    
    fun isEnabled(userIntentEnabled: Boolean): Boolean {
        return userIntentEnabled && hasVoiceAuth() && isVoiceGrowthBookEnabled()
    }
    
    private fun hasVoiceAuth(): Boolean {
        return true
    }
    
    private fun isVoiceGrowthBookEnabled(): Boolean {
        return true
    }
}