package com.umuieme.videowebbrowser.browser.scroll

import com.umuieme.videowebbrowser.browser.PageScripts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollTargetTest {

    @Test
    fun `parses each kind`() {
        assertEquals(
            ScrollTarget(ScrollTarget.Kind.Point, 0.25f, 0.5f),
            ScrollTarget.parse("""{"kind":"point","fx":0.25,"fy":0.5}"""),
        )
        assertEquals(ScrollTarget.Kind.Modal, ScrollTarget.parse("""{"kind":"modal","fx":0.5,"fy":0.4}""")?.kind)
        assertEquals(ScrollTarget.Kind.Page, ScrollTarget.parse("""{"kind":"page","fx":0,"fy":1}""")?.kind)
    }

    @Test
    fun `clamps coordinates a hair outside the viewport`() {
        assertEquals(
            ScrollTarget(ScrollTarget.Kind.Point, 1f, 0f),
            ScrollTarget.parse("""{"kind":"point","fx":1.0004,"fy":-0.001}"""),
        )
    }

    @Test
    fun `rejects anything unexpected`() {
        assertNull(ScrollTarget.parse(null))
        assertNull(ScrollTarget.parse("null"))
        assertNull(ScrollTarget.parse("{}"))
        assertNull(ScrollTarget.parse("""{"kind":"elsewhere","fx":0.5,"fy":0.5}"""))
        assertNull(ScrollTarget.parse("""{"kind":"point","fy":0.5}"""))
        assertNull(ScrollTarget.parse("not json"))
    }

    @Test
    fun `page scripts wrap helpers and entry in one private function call`() {
        val code = PageScripts.wrap(
            helpers = "function helper() {}",
            script = "function entry(a) { return a; }",
            entry = "entry",
            args = JSONObject().put("stuckKey", "it's \"quoted\""),
        )
        assertTrue(code.startsWith("(function () {"))
        assertTrue(code.trimEnd().endsWith("})();"))
        // Arguments are a JSON literal, so quotes inside values are escaped, not spliced in.
        assertTrue(code.contains("""return entry({"stuckKey":"it's \"quoted\""});"""))
    }
}
