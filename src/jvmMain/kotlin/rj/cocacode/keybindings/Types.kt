package rj.cocacode.keybindings

enum class KeybindingContextName {
    Global,
    Chat,
    Autocomplete,
    Confirmation,
    Help,
    Transcript,
    HistorySearch,
    Task,
    ThemePicker,
    Settings,
    Tabs,
    Attachments,
    Footer,
    MessageSelector,
    DiffDialog,
    ModelPicker,
    Select,
    Plugin,
    Scroll,
    MessageActions
}

data class ParsedKeystroke(
    val key: String = "",
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
    val super_: Boolean = false
)

typealias Chord = List<ParsedKeystroke>

data class ParsedBinding(
    val chord: Chord,
    val action: String?,
    val context: KeybindingContextName
)

data class KeybindingBlock(
    val context: KeybindingContextName,
    val bindings: Map<String, String?>
)

sealed class ResolveResult {
    data class Match(val action: String) : ResolveResult()
    data object None : ResolveResult()
    data object Unbound : ResolveResult()
}

sealed class ChordResolveResult {
    data class Match(val action: String) : ChordResolveResult()
    data object None : ChordResolveResult()
    data object Unbound : ChordResolveResult()
    data class ChordStarted(val pending: List<ParsedKeystroke>) : ChordResolveResult()
    data object ChordCancelled : ChordResolveResult()
}
