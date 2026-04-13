package rj.cocacode.buddy

object Companion {
    private var rollCache: Pair<String, Roll>? = null

    private fun mulberry32(seed: Int): () -> Double {
        var a = seed.toLong()
        return {
            a = a and 0xFFFFFFFFL
            a = (a + 0x6d2b79f5L) and 0xFFFFFFFFL
val t = ((a xor (a shr 15)) * (1 or a.toInt())) and 0xFFFFFFFFL
            val t2 = ((t + ((t xor (t shr 7)) * (61 or t.toInt()))) xor t) and 0xFFFFFFFFL
            ((t2 xor (t2 shr 14)) and 0xFFFFFFFFL).toDouble() / 4294967296.0
        }
    }

    private fun hashString(s: String): Int {
        var h = 2166136261L
        for (i in s.indices) {
            h = h xor s[i].code.toLong()
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return h.toInt()
    }

    private fun <T> pick(rng: () -> Double, arr: List<T>): T {
        return arr[(rng() * arr.size).toInt()]
    }

    private fun rollRarity(rng: () -> Double): String {
        val total = BuddyTypes.RARITY_WEIGHTS.values.sum()
        var roll = rng() * total
        for (rarity in BuddyTypes.RARITIES) {
            roll -= BuddyTypes.RARITY_WEIGHTS[rarity] ?: 0
            if (roll < 0) return rarity
        }
        return "common"
    }

    private val RARITY_FLOOR = mapOf(
        "common" to 5,
        "uncommon" to 15,
        "rare" to 25,
        "epic" to 35,
        "legendary" to 50
    )

    private fun rollStats(rng: () -> Double, rarity: String): Map<String, Int> {
        val floor = RARITY_FLOOR[rarity] ?: 5
        val peak = pick(rng, BuddyTypes.STAT_NAMES)
        var dump = pick(rng, BuddyTypes.STAT_NAMES)
        while (dump == peak) dump = pick(rng, BuddyTypes.STAT_NAMES)

        val stats = mutableMapOf<String, Int>()
        for (name in BuddyTypes.STAT_NAMES) {
            stats[name] = when (name) {
                peak -> minOf(100, floor + 50 + (rng() * 30).toInt())
                dump -> maxOf(1, floor - 10 + (rng() * 15).toInt())
                else -> floor + (rng() * 40).toInt()
            }
        }
        return stats
    }

    private const val SALT = "friend-2026-401"

    data class Roll(
        val bones: CompanionBones,
        val inspirationSeed: Int
    )

    private fun rollFrom(rng: () -> Double): Roll {
        val rarity = rollRarity(rng)
        val bones = CompanionBones(
            rarity = rarity,
            species = pick(rng, BuddyTypes.SPECIES),
            eye = pick(rng, BuddyTypes.EYES),
            hat = if (rarity == "none") "none" else pick(rng, BuddyTypes.HATS),
            shiny = rng() < 0.01,
            stats = rollStats(rng, rarity)
        )
        return Roll(bones, (rng() * 1e9).toInt())
    }

    fun roll(userId: String): Roll {
        val key = userId + SALT
        if (rollCache?.first == key) return rollCache!!.second
        val value = rollFrom(mulberry32(hashString(key)))
        rollCache = key to value
        return value
    }

    fun rollWithSeed(seed: String): Roll {
        return rollFrom(mulberry32(hashString(seed)))
    }

    fun companionUserId(): String {
        return "anon"
    }

    fun getCompanion(stored: StoredCompanion?): CompanionData? {
        if (stored == null) return null
        val bones = roll(companionUserId()).bones
        return CompanionData(
            rarity = bones.rarity,
            species = bones.species,
            eye = bones.eye,
            hat = bones.hat,
            shiny = bones.shiny,
            stats = bones.stats,
            name = stored.name,
            personality = stored.personality,
            hatchedAt = stored.hatchedAt
        )
    }
}