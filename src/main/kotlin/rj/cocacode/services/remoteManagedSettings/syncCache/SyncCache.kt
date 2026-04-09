package rj.cocacode.services.remoteManagedSettings.syncCache

import rj.cocacode.services.remoteManagedSettings.syncCacheState.resetSyncCache
import rj.cocacode.services.remoteManagedSettings.syncCacheState.setEligibility

private var cached: Boolean? = null

fun resetSyncCache() {
    cached = null
    resetSyncCache()
}

fun isRemoteManagedSettingsEligible(): Boolean {
    if (cached != null) return cached!!
    
    cached = false
    return cached!!
}