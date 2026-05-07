package rj.cocacode.keybindings

fun resolveKey(
    input: String,
    key: InkKey,
    activeContexts: List<KeybindingContextName>,
    bindings: List<ParsedBinding>
): ResolveResult {
    val ctxSet = activeContexts.toSet()
    
    var match: ParsedBinding? = null
    
    for (binding in bindings) {
        if (binding.chord.size != 1) continue
        if (!ctxSet.contains(binding.context)) continue
        
        if (matchesBinding(input, key, binding)) {
            match = binding
        }
    }
    
    if (match == null) {
        return ResolveResult.None
    }
    
    if (match.action == null) {
        return ResolveResult.Unbound
    }
    
    return ResolveResult.Match(match.action)
}

fun getBindingDisplayText(
    action: String,
    context: KeybindingContextName,
    bindings: List<ParsedBinding>
): String? {
    val binding = bindings.findLast { it.action == action && it.context == context }
    return binding?.let { chordToString(it.chord) }
}

fun buildKeystroke(input: String, key: InkKey): ParsedKeystroke? {
    val keyName = getKeyName(input, key) ?: return null
    
    val effectiveMeta = if (key.escape) false else key.meta
    
    return ParsedKeystroke(
        key = keyName,
        ctrl = key.ctrl,
        alt = effectiveMeta,
        shift = key.shift,
        meta = effectiveMeta,
        super_ = key.super_
    )
}

fun keystrokesEqual(a: ParsedKeystroke, b: ParsedKeystroke): Boolean {
    return a.key == b.key &&
            a.ctrl == b.ctrl &&
            a.shift == b.shift &&
            (a.alt || a.meta) == (b.alt || b.meta) &&
            a.super_ == b.super_
}

private fun chordPrefixMatches(prefix: List<ParsedKeystroke>, binding: ParsedBinding): Boolean {
    if (prefix.size >= binding.chord.size) return false
    for (i in prefix.indices) {
        val prefixKey = prefix.getOrNull(i) ?: return false
        val bindingKey = binding.chord.getOrNull(i) ?: return false
        if (!keystrokesEqual(prefixKey, bindingKey)) return false
    }
    return true
}

private fun chordExactlyMatches(chord: List<ParsedKeystroke>, binding: ParsedBinding): Boolean {
    if (chord.size != binding.chord.size) return false
    for (i in chord.indices) {
        val chordKey = chord.getOrNull(i) ?: return false
        val bindingKey = binding.chord.getOrNull(i) ?: return false
        if (!keystrokesEqual(chordKey, bindingKey)) return false
    }
    return true
}

fun resolveKeyWithChordState(
    input: String,
    key: InkKey,
    activeContexts: List<KeybindingContextName>,
    bindings: List<ParsedBinding>,
    pending: List<ParsedKeystroke>?
): ChordResolveResult {
    if (key.escape && pending != null) {
        return ChordResolveResult.ChordCancelled
    }
    
    val currentKeystroke = buildKeystroke(input, key)
    if (currentKeystroke == null) {
        if (pending != null) {
            return ChordResolveResult.ChordCancelled
        }
        return ChordResolveResult.None
    }
    
    val testChord = if (pending != null) pending + currentKeystroke else listOf(currentKeystroke)
    
    val ctxSet = activeContexts.toSet()
    val contextBindings = bindings.filter { ctxSet.contains(it.context) }
    
    val chordWinners = mutableMapOf<String, String?>()
    for (binding in contextBindings) {
        if (binding.chord.size > testChord.size && chordPrefixMatches(testChord, binding)) {
            chordWinners[chordToString(binding.chord)] = binding.action
        }
    }
    
    val hasLongerChords = chordWinners.values.any { it != null }
    
    if (hasLongerChords) {
        return ChordResolveResult.ChordStarted(testChord)
    }
    
    var exactMatch: ParsedBinding? = null
    for (binding in contextBindings) {
        if (chordExactlyMatches(testChord, binding)) {
            exactMatch = binding
        }
    }
    
    if (exactMatch != null) {
        if (exactMatch.action == null) {
            return ChordResolveResult.Unbound
        }
        return ChordResolveResult.Match(exactMatch.action)
    }
    
    if (pending != null) {
        return ChordResolveResult.ChordCancelled
    }
    
    return ChordResolveResult.None
}
