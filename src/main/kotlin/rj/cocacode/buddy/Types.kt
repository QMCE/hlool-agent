package rj.cocacode.buddy

object BuddyTypes {
    val RARITIES = listOf("common", "uncommon", "rare", "epic", "legendary")
    typealias Rarity = String

    private fun c(vararg codes: Int): String = codes.map { it.toChar() }.joinToString("")

    val duck = c(0x64, 0x75, 0x63, 0x6b)
    val goose = c(0x67, 0x6f, 0x6f, 0x73, 0x65)
    val blob = c(0x62, 0x6c, 0x6f, 0x62)
    val cat = c(0x63, 0x61, 0x74)
    val dragon = c(0x64, 0x72, 0x61, 0x67, 0x6f, 0x6e)
    val octopus = c(0x6f, 0x63, 0x74, 0x6f, 0x70, 0x75, 0x73)
    val owl = c(0x6f, 0x77, 0x6c)
    val penguin = c(0x70, 0x65, 0x6e, 0x67, 0x75, 0x69, 0x6e)
    val turtle = c(0x74, 0x75, 0x72, 0x74, 0x6c, 0x65)
    val snail = c(0x73, 0x6e, 0x61, 0x69, 0x6c)
    val ghost = c(0x67, 0x68, 0x6f, 0x73, 0x74)
    val axolotl = c(0x61, 0x78, 0x6f, 0x6c, 0x6f, 0x74, 0x6c)
    val capybara = c(0x63, 0x61, 0x70, 0x79, 0x62, 0x61, 0x72, 0x61)
    val cactus = c(0x63, 0x61, 0x63, 0x74, 0x75, 0x73)
    val robot = c(0x72, 0x6f, 0x62, 0x6f, 0x74)
    val rabbit = c(0x72, 0x61, 0x62, 0x62, 0x69, 0x74)
    val mushroom = c(0x6d, 0x75, 0x73, 0x68, 0x72, 0x6f, 0x6f, 0x6d)
    val chonk = c(0x63, 0x68, 0x6f, 0x6e, 0x6b)

    val SPECIES = listOf(
        duck, goose, blob, cat, dragon, octopus, owl, penguin,
        turtle, snail, ghost, axolotl, capybara, cactus, robot,
        rabbit, mushroom, chonk
    )
    typealias Species = String

    val EYES = listOf("·", "✦", "×", "◉", "@", "°")
    typealias Eye = String

    val HATS = listOf("none", "crown", "tophat", "propeller", "halo", "wizard", "beanie", "tinyduck")
    typealias Hat = String

    val STAT_NAMES = listOf("DEBUGGING", "PATIENCE", "CHAOS", "WISDOM", "SNARK")
    typealias StatName = String

    val RARITY_WEIGHTS = mapOf(
        "common" to 60,
        "uncommon" to 25,
        "rare" to 10,
        "epic" to 4,
        "legendary" to 1
    )

    val RARITY_STARS = mapOf(
        "common" to "★",
        "uncommon" to "★★",
        "rare" to "★★★",
        "epic" to "★★★★",
        "legendary" to "★★★★★"
    )

    val RARITY_COLORS = mapOf(
        "common" to "inactive",
        "uncommon" to "success",
        "rare" to "permission",
        "epic" to "autoAccept",
        "legendary" to "warning"
    )
}

data class CompanionBones(
    val rarity: BuddyTypes.Rarity,
    val species: BuddyTypes.Species,
    val eye: BuddyTypes.Eye,
    val hat: BuddyTypes.Hat,
    val shiny: Boolean,
    val stats: Map<BuddyTypes.StatName, Int>
)

data class CompanionSoul(
    val name: String,
    val personality: String
)

data class CompanionData(
    val rarity: BuddyTypes.Rarity,
    val species: BuddyTypes.Species,
    val eye: BuddyTypes.Eye,
    val hat: BuddyTypes.Hat,
    val shiny: Boolean,
    val stats: Map<BuddyTypes.StatName, Int>,
    val name: String,
    val personality: String,
    val hatchedAt: Long
)

data class StoredCompanion(
    val name: String,
    val personality: String,
    val hatchedAt: Long
)