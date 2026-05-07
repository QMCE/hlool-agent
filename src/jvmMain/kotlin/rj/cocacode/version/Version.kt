package rj.cocacode.version

import java.io.File

object VersionInfo {
    data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String? = null
    ) {
        override fun toString(): String {
            val base = "$major.$minor.$patch"
            return if (preRelease != null) "$base-$preRelease" else base
        }
        
        fun compareTo(other: Version): Int {
            val majorDiff = major - other.major
            if (majorDiff != 0) return majorDiff
            
            val minorDiff = minor - other.minor
            if (minorDiff != 0) return minorDiff
            
            return patch - other.patch
        }
    }
    
    fun parse(versionString: String): Version? {
        val regex = Regex("(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9]+))?")
        val match = regex.find(versionString) ?: return null
        
        return Version(
            major = match.groupValues[1].toInt(),
            minor = match.groupValues[2].toInt(),
            patch = match.groupValues[3].toInt(),
            preRelease = match.groupValues[4].takeIf { it.isNotEmpty() }
        )
    }
    
    fun current(): Version = Version(1, 0, 0)
    
    fun isCompatible(current: Version, required: Version): Boolean {
        return current.major == required.major &&
               current.minor >= required.minor
    }
}

object SemanticVersion {
    private val buildMetadata = mapOf(
        "timestamp" to System.currentTimeMillis(),
        "java" to System.getProperty("java.version"),
        "kotlin" to "1.9.22"
    )
    
    val buildInfo: Map<String, Any> = buildMetadata
    
    fun generateVersionCode(major: Int, minor: Int, patch: Int): Int {
        return major * 10000 + minor * 100 + patch
    }
    
    fun parseVersionCode(code: Int): VersionInfo.Version {
        val major = code / 10000
        val minor = (code % 10000) / 100
        val patch = code % 100
        return VersionInfo.Version(major, minor, patch)
    }
}

object Changelog {
    data class Release(
        val version: String,
        val date: String,
        val changes: List<Change>
    )
    
    data class Change(
        val type: ChangeType,
        val description: String
    )
    
    enum class ChangeType {
        ADDED, CHANGED, DEPRECATED, REMOVED, FIXED, SECURITY
    }
    
    fun parseChangelog(content: String): List<Release> {
        val releases = mutableListOf<Release>()
        var currentRelease: Release? = null
        var currentChanges = mutableListOf<Change>()
        
        content.lines().forEach { line ->
            when {
                line.startsWith("## [") -> {
                    currentRelease?.let {
                        releases.add(it.copy(changes = currentChanges.toList()))
                    }
                    currentChanges = mutableListOf()
                    
                    val version = line.substringAfter("## [").substringBefore("]")
                    val date = line.substringAfter("] - ").takeIf { it != line }
                    currentRelease = Release(version, date ?: "", emptyList())
                }
                line.startsWith("### ") -> {
                    val type = when {
                        line.contains("Added") -> ChangeType.ADDED
                        line.contains("Changed") -> ChangeType.CHANGED
                        line.contains("Deprecated") -> ChangeType.DEPRECATED
                        line.contains("Removed") -> ChangeType.REMOVED
                        line.contains("Fixed") -> ChangeType.FIXED
                        line.contains("Security") -> ChangeType.SECURITY
                        else -> ChangeType.CHANGED
                    }
                    val description = line.substringAfter("### ").trim()
                    currentChanges.add(Change(type, description))
                }
            }
        }
        
        currentRelease?.let {
            releases.add(it.copy(changes = currentChanges.toList()))
        }
        
        return releases
    }
}