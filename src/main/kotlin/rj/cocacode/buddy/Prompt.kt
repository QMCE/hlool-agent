package rj.cocacode.buddy

object Prompt {
    fun companionIntroText(name: String, species: String): String {
        return """
# Companion

A small $species named $name sits beside the user's input box and occasionally comments in a speech bubble. You're not $name — it's a separate watcher.

When the user addresses $name directly (by name), its bubble will answer. Your job in that moment is to stay out of the way: respond in ONE line or less, or just answer any part of the message meant for you. Don't explain that you're not $name — they know. Don't narrate what $name might say — the bubble handles that.
        """.trimIndent()
    }

    data class CompanionIntroAttachment(
        val type: String = "companion_intro",
        val name: String,
        val species: String
    )

    fun getCompanionIntroAttachment(
        messages: List<Any>?,
        companion: CompanionData?
    ): List<CompanionIntroAttachment> {
        if (companion == null) return emptyList()

        messages?.forEach { msg ->
            if (msg is Map<*, *>) {
                val attachment = msg["attachment"]
                if (attachment is Map<*, *>) {
                    if (attachment["type"] == "companion_intro" && attachment["name"] == companion.name) {
                        return emptyList()
                    }
                }
            }
        }

        return listOf(CompanionIntroAttachment(name = companion.name, species = companion.species))
    }
}