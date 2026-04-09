package rj.cocacode.buddy

object Sprites {
    private val BODIES: Map<String, List<List<String>>> = mapOf(
        "duck" to listOf(
            listOf("            ", "    __      ", "  <({E} )___  ", "   (  ._>   ", "    `--´    "),
            listOf("            ", "    __      ", "  <({E} )___  ", "   (  ._>   ", "    `--´~   "),
            listOf("            ", "    __      ", "  <({E} )___  ", "   (  .__>  ", "    `--´    ")
        ),
        "goose" to listOf(
            listOf("            ", "     ({E}>    ", "     ||     ", "   _(__)_   ", "    ^^^^    "),
            listOf("            ", "    ({E}>     ", "     ||     ", "   _(__)_   ", "    ^^^^    "),
            listOf("            ", "     ({E}>>   ", "     ||     ", "   _(__)_   ", "    ^^^^    ")
        ),
        "blob" to listOf(
            listOf("            ", "   .----.   ", "  ( {E}  {E} )  ", "  (      )  ", "   `----´   "),
            listOf("            ", "  .------.  ", " (  {E}  {E}  ) ", " (        ) ", "  `------´  "),
            listOf("            ", "    .--.    ", "   ({E}  {E})   ", "   (    )   ", "    `--´    ")
        ),
        "cat" to listOf(
            listOf("            ", "   /\\_/\\    ", "  ( {E}   {E})  ", "  (  ω  )   ", "  (\")_(\"   "),
            listOf("            ", "   /\\_/\\    ", "  ( {E}   {E})  ", "  (  ω  )   ", "  (\")_(\"~  "),
            listOf("            ", "   /\\-/\\    ", "  ( {E}   {E})  ", "  (  ω  )   ", "  (\")_(\"   ")
        ),
        "dragon" to listOf(
            listOf("            ", "  /^\\  /^\\  ", " <  {E}  {E}  > ", " (   ~~   ) ", "  `-vvvv-´  "),
            listOf("            ", "  /^\\  /^\\  ", " <  {E}  {E}  > ", " (        ) ", "  `-vvvv-´  "),
            listOf("   ~    ~   ", "  /^\\  /^\\  ", " <  {E}  {E}  > ", " (   ~~   ) ", "  `-vvvv-´  ")
        ),
        "octopus" to listOf(
            listOf("            ", "   .----.   ", "  ( {E}  {E} )  ", "  (______)  ", "  /\\/\\/\\/\\  "),
            listOf("            ", "   .----.   ", "  ( {E}  {E} )  ", "  (______)  ", "  \\/\\/\\/\\/  "),
            listOf("     o      ", "   .----.   ", "  ( {E}  {E} )  ", "  (______)  ", "  /\\/\\/\\/\\  ")
        ),
        "owl" to listOf(
            listOf("            ", "   /\\  /\\   ", "  (({E})({E}))  ", "  (  ><  )  ", "   `----´   "),
            listOf("            ", "   /\\  /\\   ", "  (({E})({E}))  ", "  (  ><  )  ", "   .----.   "),
            listOf("            ", "   /\\  /\\   ", "  (({E})(-))  ", "  (  ><  )  ", "   `----´   ")
        ),
        "penguin" to listOf(
            listOf("            ", "  .---.     ", "  ({E}>{E})     ", " /(   )\\    ", "  `---´     "),
            listOf("            ", "  .---.     ", "  ({E}>{E})     ", " |(   )|    ", "  `---´     "),
            listOf("  .---.     ", "  ({E}>{E})     ", " /(   )\\    ", "  `---´     ", "   ~ ~      ")
        ),
        "turtle" to listOf(
            listOf("            ", "   _,--._   ", "  ( {E}  {E} )  ", " /[______]\\ ", "  ``    ``  "),
            listOf("            ", "   _,--._   ", "  ( {E}  {E} )  ", " /[______]\\ ", "   ``  ``   "),
            listOf("            ", "   _,--._   ", "  ( {E}  {E} )  ", " /[======]\\ ", "  ``    ``  ")
        ),
        "snail" to listOf(
            listOf("            ", " {E}    .--.  ", "  \\  ( @ )  ", "   \\_`--´   ", "  ~~~~~~~   "),
            listOf("            ", "  {E}   .--.  ", "  |  ( @ )  ", "   \\_`--´   ", "  ~~~~~~~   "),
            listOf("            ", " {E}    .--.  ", "  \\  ( @  ) ", "   \\_`--´   ", "   ~~~~~~   ")
        ),
        "ghost" to listOf(
            listOf("            ", "   .----.   ", "  / {E}  {E} \\  ", "  |      |  ", "  ~`~``~`~  "),
            listOf("            ", "   .----.   ", "  / {E}  {E} \\  ", "  |      |  ", "  `~`~~`~`  "),
            listOf("    ~  ~    ", "   .----.   ", "  / {E}  {E} \\  ", "  |      |  ", "  ~~`~~`~~  ")
        ),
        "axolotl" to listOf(
            listOf("            ", "}~(______)~{", "}~({E} .. {E})~{", "  ( .--. )  ", "  (_/  \\_)  "),
            listOf("            ", "~}(______){~", "~}({E} .. {E}){~", "  ( .--. )  ", "  (_/  \\_)  "),
            listOf("            ", "}~(______)~{", "}~({E} .. {E})~{", "  (  --  )  ", "  ~_/  \\_~  ")
        ),
        "capybara" to listOf(
            listOf("            ", "  n______n  ", " ( {E}    {E} ) ", " (   oo   ) ", "  `------´  "),
            listOf("            ", "  n______n  ", " ( {E}    {E} ) ", " (   Oo   ) ", "  `------´  "),
            listOf("    ~  ~    ", "  u______n  ", " ( {E}    {E} ) ", " (   oo   ) ", "  `------´  ")
        ),
        "cactus" to listOf(
            listOf("            ", " n  ____  n ", " | |{E}  {E}| | ", " |_|    |_| ", "   |    |   "),
            listOf("            ", "    ____    ", " n |{E}  {E}| n ", " |_|    |_| ", "   |    |   "),
            listOf(" n        n ", " |  ____  | ", " | |{E}  {E}| | ", " |_|    |_| ", "   |    |   ")
        ),
        "robot" to listOf(
            listOf("            ", "   .[||].   ", "  [ {E}  {E} ]  ", "  [ ==== ]  ", "  `------´  "),
            listOf("            ", "   .[||].   ", "  [ {E}  {E} ]  ", "  [ -==- ]  ", "  `------´  "),
            listOf("     *      ", "   .[||].   ", "  [ {E}  {E} ]  ", "  [ ==== ]  ", "  `------´  ")
        ),
        "rabbit" to listOf(
            listOf("            ", "   (\\__/)   ", "  ( {E}  {E} )  ", " =(  ..  )= ", "  (\")__(\"  "),
            listOf("            ", "   (|__/)   ", "  ( {E}  {E} )  ", " =(  ..  )= ", "  (\")__(\"  "),
            listOf("            ", "   (\\__/)   ", "  ( {E}  {E} )  ", " =( .  . )= ", "  (\")__(\"  ")
        ),
        "mushroom" to listOf(
            listOf("            ", " .-oOO-o-. ", "(__________)", "   |{E}  {E}|   ", "   |____|   "),
            listOf("            ", " .-O-oo-O-. ", "(__________)", "   |{E}  {E}|   ", "   |____|   "),
            listOf("   . o  .   ", " .-oOO-o-. ", "(__________)", "   |{E}  {E}|   ", "   |____|   ")
        ),
        "chonk" to listOf(
            listOf("            ", "  /\\    /\\  ", " ( {E}    {E} ) ", " (   ..   ) ", "  `------´  "),
            listOf("            ", "  /\\    /|  ", " ( {E}    {E} ) ", " (   ..   ) ", "  `------´  "),
            listOf("            ", "  /\\    /\\  ", " ( {E}    {E} ) ", " (   ..   ) ", "  `------´~ ")
        )
    )

    private val HAT_LINES: Map<String, String> = mapOf(
        "none" to "",
        "crown" to "   \\^^^/    ",
        "tophat" to "   [___]    ",
        "propeller" to "    -+-     ",
        "halo" to "   (   )    ",
        "wizard" to "    /^\\     ",
        "beanie" to "   (___)    ",
        "tinyduck" to "    ,>      "
    )

    fun renderSprite(bones: CompanionBones, frame: Int = 0): List<String> {
        val frames = BODIES[bones.species] ?: return emptyList()
        val body = frames[frame % frames.size].map { it.replace("{E}", bones.eye) }.toMutableList()
        if (bones.hat != "none" && body[0].isBlank()) {
            body[0] = HAT_LINES[bones.hat] ?: ""
        }
        if (body[0].isBlank() && frames.all { it[0].isBlank() }) {
            body.removeAt(0)
        }
        return body
    }

    fun spriteFrameCount(species: String): Int {
        return BODIES[species]?.size ?: 0
    }

    fun renderFace(bones: CompanionBones): String {
        val eye = bones.eye
        return when (bones.species) {
            "duck", "goose" -> "($eye)>"
            "blob" -> "($eye$eye)"
            "cat" -> "=${eye}ω${eye}="
            "dragon" -> "<$eye~$eye>"
            "octopus" -> "~($eye$eye)~"
            "owl" -> "($eye)($eye)"
            "penguin" -> "($eye>)"
            "turtle" -> "[${eye}_$eye]"
            "snail" -> "$eye(@)"
            "ghost" -> "/$eye$eye\\"
            "axolotl" -> "}${eye}.$eye{"
            "capybara" -> "(${eye}o$eye)"
            "cactus" -> "|$eye  $eye|"
            "robot" -> "[$eye$eye]"
            "rabbit" -> "($eye..$eye)"
            "mushroom" -> "|$eye  $eye|"
            "chonk" -> "($eye.$eye)"
            else -> "($eye$eye)"
        }
    }
}