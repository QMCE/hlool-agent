package rj.cocacode.diff

import kotlin.test.Test
import kotlin.test.assertTrue
import rj.cocacode.ui.tools.ToolResultRenderer

class DiffEngineTest {

    @Test
    fun compute_doesNotThrowOnDivergentTexts() {
        val old = (1..40).joinToString("\n") { "old line $it shared" }
        val new = (1..40).joinToString("\n") { "new line $it shared" }
        val result = DiffEngine.compute(old, new)
        assertTrue(result.hunks.isNotEmpty())
    }

    @Test
    fun compute_handlesPartialOverlapWithoutNegativeIndex() {
        // Previously: lcs[lcs.size - 1 - (oldLines.size - 1 - oldIdx)] → Index -1
        val old = (1..30).joinToString("\n") { "line $it" }
        val new = listOf("inserted") + (10..35).map { "line $it" }
        val result = DiffEngine.compute(old, new.joinToString("\n"))
        assertTrue(result.hunks.isNotEmpty() || result.oldContent.isNotEmpty())
    }

    @Test
    fun formatInlineDiff_neverThrows() {
        val old = (1..50).joinToString("\n") { "aaaaaaaaaaaaaaaaaaaa $it" }
        val new = (1..50).joinToString("\n") { "bbbbbbbbbbbbbbbbbbbb $it" }
        val rendered = ToolResultRenderer.formatInlineDiff(old, new)
        assertTrue(rendered.isNotBlank())
    }
}
