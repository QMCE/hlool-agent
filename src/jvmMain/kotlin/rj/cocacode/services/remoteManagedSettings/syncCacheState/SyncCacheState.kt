package rj.cocacode.services.remoteManagedSettings.syncCacheState

typealias SettingsJson = Map<String, Any?>

private var sessionCache: SettingsJson? = null
private var eligible: Boolean? = null

fun setSessionCache(value: SettingsJson?) {
    sessionCache = value
}

fun resetSyncCache() {
    sessionCache = null
    eligible = null
}

fun setEligibility(v: Boolean): Boolean {
    eligible = v
    return v
}

fun getSettingsPath(): String {
    return ""
}

fun getRemoteManagedSettingsSyncFromCache(): SettingsJson? {
    if (eligible != true) return null
    return sessionCache
}