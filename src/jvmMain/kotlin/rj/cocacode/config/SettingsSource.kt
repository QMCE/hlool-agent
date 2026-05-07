package rj.cocacode.config

/**
 * 配置来源类型 - 与 Claude Code 兼容的多层配置系统
 * 优先级从低到高: user < project < local < flag < policy
 */
enum class SettingsSource(val displayName: String, val displayNameLowercase: String) {
    USER("User", "user settings"),
    PROJECT("Project", "shared project settings"),
    LOCAL("Local", "project local settings"),
    FLAG("Flag", "command line arguments"),
    POLICY("Managed", "enterprise managed settings");

    companion object {
        /**
         * 获取所有启用的配置源 (按优先级顺序)
         */
        fun getEnabledSources(): List<SettingsSource> = entries

        /**
         * 检查指定来源是否启用
         */
        fun isEnabled(source: SettingsSource): Boolean = true

        /**
         * 获取优先级顺序 (低到高)
         */
        fun getPriorityOrder(): List<SettingsSource> = listOf(
            USER,      // 最低优先级
            PROJECT,
            LOCAL,
            FLAG,
            POLICY    // 最高优先级
        )
    }
}

/**
 * 可编辑的配置来源 (policy/flag 为只读)
 */
val editableSources = listOf(SettingsSource.USER, SettingsSource.PROJECT, SettingsSource.LOCAL)