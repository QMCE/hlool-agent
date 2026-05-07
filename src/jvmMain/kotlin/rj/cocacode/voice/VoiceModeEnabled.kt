package rj.cocacode.voice

fun isVoiceGrowthBookEnabled(): Boolean {
    return false
}

fun hasVoiceAuth(): Boolean {
    return false
}

fun isVoiceModeEnabled(): Boolean {
    return hasVoiceAuth() && isVoiceGrowthBookEnabled()
}