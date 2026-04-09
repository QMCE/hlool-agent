package rj.cocacode.keybindings

val IMAGE_PASTE_KEY: String = if (Platform.getPlatform() == "windows") "alt+v" else "ctrl+v"

val DEFAULT_BINDINGS: List<KeybindingBlock> = listOf(
    KeybindingBlock(
        context = KeybindingContextName.Global,
        bindings = mapOf(
            "ctrl+c" to "app:interrupt",
            "ctrl+d" to "app:exit",
            "ctrl+l" to "app:redraw",
            "ctrl+t" to "app:toggleTodos",
            "ctrl+o" to "app:toggleTranscript",
            "ctrl+shift+o" to "app:toggleTeammatePreview",
            "ctrl+r" to "history:search"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Chat,
        bindings = mapOf(
            "escape" to "chat:cancel",
            "ctrl+x ctrl+k" to "chat:killAgents",
            "shift+tab" to "chat:cycleMode",
            "meta+p" to "chat:modelPicker",
            "meta+o" to "chat:fastMode",
            "meta+t" to "chat:thinkingToggle",
            "enter" to "chat:submit",
            "up" to "history:previous",
            "down" to "history:next",
            "ctrl+_" to "chat:undo",
            "ctrl+shift+-" to "chat:undo",
            "ctrl+x ctrl+e" to "chat:externalEditor",
            "ctrl+g" to "chat:externalEditor",
            "ctrl+s" to "chat:stash",
            IMAGE_PASTE_KEY to "chat:imagePaste"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Autocomplete,
        bindings = mapOf(
            "tab" to "autocomplete:accept",
            "escape" to "autocomplete:dismiss",
            "up" to "autocomplete:previous",
            "down" to "autocomplete:next"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Settings,
        bindings = mapOf(
            "escape" to "confirm:no",
            "up" to "select:previous",
            "down" to "select:next",
            "k" to "select:previous",
            "j" to "select:next",
            "ctrl+p" to "select:previous",
            "ctrl+n" to "select:next",
            "space" to "select:accept",
            "enter" to "settings:close",
            "/" to "settings:search",
            "r" to "settings:retry"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Confirmation,
        bindings = mapOf(
            "y" to "confirm:yes",
            "n" to "confirm:no",
            "enter" to "confirm:yes",
            "escape" to "confirm:no",
            "up" to "confirm:previous",
            "down" to "confirm:next",
            "tab" to "confirm:nextField",
            "space" to "confirm:toggle",
            "shift+tab" to "confirm:cycleMode",
            "ctrl+e" to "confirm:toggleExplanation",
            "ctrl+d" to "permission:toggleDebug"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Tabs,
        bindings = mapOf(
            "tab" to "tabs:next",
            "shift+tab" to "tabs:previous",
            "right" to "tabs:next",
            "left" to "tabs:previous"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Transcript,
        bindings = mapOf(
            "ctrl+e" to "transcript:toggleShowAll",
            "ctrl+c" to "transcript:exit",
            "escape" to "transcript:exit",
            "q" to "transcript:exit"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.HistorySearch,
        bindings = mapOf(
            "ctrl+r" to "historySearch:next",
            "escape" to "historySearch:accept",
            "tab" to "historySearch:accept",
            "ctrl+c" to "historySearch:cancel",
            "enter" to "historySearch:execute"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Task,
        bindings = mapOf(
            "ctrl+b" to "task:background"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.ThemePicker,
        bindings = mapOf(
            "ctrl+t" to "theme:toggleSyntaxHighlighting"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Scroll,
        bindings = mapOf(
            "pageup" to "scroll:pageUp",
            "pagedown" to "scroll:pageDown",
            "wheelup" to "scroll:lineUp",
            "wheeldown" to "scroll:lineDown",
            "ctrl+home" to "scroll:top",
            "ctrl+end" to "scroll:bottom",
            "ctrl+shift+c" to "selection:copy",
            "cmd+c" to "selection:copy"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Help,
        bindings = mapOf(
            "escape" to "help:dismiss"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Attachments,
        bindings = mapOf(
            "right" to "attachments:next",
            "left" to "attachments:previous",
            "backspace" to "attachments:remove",
            "delete" to "attachments:remove",
            "down" to "attachments:exit",
            "escape" to "attachments:exit"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Footer,
        bindings = mapOf(
            "up" to "footer:up",
            "ctrl+p" to "footer:up",
            "down" to "footer:down",
            "ctrl+n" to "footer:down",
            "right" to "footer:next",
            "left" to "footer:previous",
            "enter" to "footer:openSelected",
            "escape" to "footer:clearSelection"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.MessageSelector,
        bindings = mapOf(
            "up" to "messageSelector:up",
            "down" to "messageSelector:down",
            "k" to "messageSelector:up",
            "j" to "messageSelector:down",
            "ctrl+p" to "messageSelector:up",
            "ctrl+n" to "messageSelector:down",
            "ctrl+up" to "messageSelector:top",
            "shift+up" to "messageSelector:top",
            "meta+up" to "messageSelector:top",
            "shift+k" to "messageSelector:top",
            "ctrl+down" to "messageSelector:bottom",
            "shift+down" to "messageSelector:bottom",
            "meta+down" to "messageSelector:bottom",
            "shift+j" to "messageSelector:bottom",
            "enter" to "messageSelector:select"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.DiffDialog,
        bindings = mapOf(
            "escape" to "diff:dismiss",
            "left" to "diff:previousSource",
            "right" to "diff:nextSource",
            "up" to "diff:previousFile",
            "down" to "diff:nextFile",
            "enter" to "diff:viewDetails"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.ModelPicker,
        bindings = mapOf(
            "left" to "modelPicker:decreaseEffort",
            "right" to "modelPicker:increaseEffort"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Select,
        bindings = mapOf(
            "up" to "select:previous",
            "down" to "select:next",
            "j" to "select:next",
            "k" to "select:previous",
            "ctrl+n" to "select:next",
            "ctrl+p" to "select:previous",
            "enter" to "select:accept",
            "escape" to "select:cancel"
        )
    ),
    KeybindingBlock(
        context = KeybindingContextName.Plugin,
        bindings = mapOf(
            "space" to "plugin:toggle",
            "i" to "plugin:install"
        )
    )
)
