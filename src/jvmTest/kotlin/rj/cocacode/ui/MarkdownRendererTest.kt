package rj.cocacode.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class MarkdownRendererTest {

    private fun stripAnsi(s: String) = s.replace(Regex("\\[[0-9;]*[A-Za-z]"), "")

    @Test
    fun `bold renders with ansi bold code`() {
        val out = MarkdownRenderer().renderLine("this is **bold** text")
        assertTrue(out.contains("[1m"), "expected bold ANSI code, got: $out")
        assertTrue(stripAnsi(out).contains("bold"))
    }

    @Test
    fun `inline code renders with chip background`() {
        val out = MarkdownRenderer().renderLine("run `gradle build` now")
        assertTrue(out.contains("[48;5;236m"), "expected code background, got: $out")
        assertTrue(stripAnsi(out).contains("gradle build"))
    }

    @Test
    fun `heading renders bold`() {
        val out = MarkdownRenderer().renderLine("## Summary")
        assertTrue(out.contains("[1m"), "heading should be bold")
        assertTrue(stripAnsi(out).contains("Summary"))
    }

    @Test
    fun `list item renders a bullet`() {
        val out = MarkdownRenderer().renderLine("- first item")
        assertTrue(stripAnsi(out).contains("•"), "expected bullet, got: ${stripAnsi(out)}")
        assertTrue(stripAnsi(out).contains("first item"))
    }

    @Test
    fun `code block toggles dim inside fences`() {
        val r = MarkdownRenderer()
        r.renderLine("```kotlin")
        val inside = r.renderLine("val x = 1")
        val end = r.renderLine("```")
        assertTrue(inside.contains("[2m"), "code block content should be dim")
        assertTrue(stripAnsi(inside).contains("val x = 1"))
        assertTrue(stripAnsi(end).contains("```"))
    }

    @Test
    fun `link renders underlined cyan`() {
        val out = MarkdownRenderer().renderLine("see [docs](https://example.com)")
        assertTrue(out.contains("[4m"), "link should be underlined")
        assertTrue(stripAnsi(out).contains("docs"))
    }
}
