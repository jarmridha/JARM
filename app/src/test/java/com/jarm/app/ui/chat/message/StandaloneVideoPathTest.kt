package com.jarm.app.ui.chat.message

import org.junit.Assert.assertEquals
import org.junit.Test

class StandaloneVideoPathTest {
    @Test
    fun standaloneVideoPathBecomesAnEmbeddedCard() {
        assertEquals(
            "![](/home/jarm/clips/a.mp4)",
            "/home/jarm/clips/a.mp4".embedStandaloneVideoPaths(),
        )
        assertEquals(
            "![](shell://Local%20Sandbox/home/jarm/clips/a.mp4)",
            "shell://Local%20Sandbox/home/jarm/clips/a.mp4".embedStandaloneVideoPaths(),
        )
        assertEquals(
            "![](/storage/emulated/0/Download/a.mp4)",
            "/storage/emulated/0/Download/a.mp4".embedStandaloneVideoPaths(),
        )
    }

    @Test
    fun surroundingProseKeepsThePathAsText() {
        val text = "打开 /home/jarm/clips/a.mp4 看看"
        assertEquals(text, text.embedStandaloneVideoPaths())
    }

    @Test
    fun fencedAndIndentedCodeStayVerbatim() {
        val fenced = "```\n/home/jarm/clips/a.mp4\n```"
        assertEquals(fenced, fenced.embedStandaloneVideoPaths())
        val indented = "    /home/jarm/clips/a.mp4"
        assertEquals(indented, indented.embedStandaloneVideoPaths())
    }

    @Test
    fun inlineCodeAndExistingImageNodesStayVerbatim() {
        val inline = "`/home/jarm/clips/a.mp4`"
        assertEquals(inline, inline.embedStandaloneVideoPaths())
        val existingImage = "![](/home/jarm/clips/a.mp4)"
        assertEquals(existingImage, existingImage.embedStandaloneVideoPaths())
    }

    @Test
    fun standaloneMarkdownLinkBecomesTheSameCard() {
        assertEquals(
            "![](/home/jarm/clips/a.mp4)",
            "[clip](/home/jarm/clips/a.mp4)".embedStandaloneVideoPaths(),
        )
        assertEquals(
            "![](shell://Local%20Sandbox/home/jarm/clips/a.mp4)",
            "[clip](shell://Local%20Sandbox/home/jarm/clips/a.mp4)".embedStandaloneVideoPaths(),
        )
        val prose = "看 [clip](/home/jarm/clips/a.mp4) 这个"
        assertEquals(prose, prose.embedStandaloneVideoPaths())
    }

    @Test
    fun htmlVideoElementBecomesTheSameCard() {
        val element = "<video controls width=\"360\"><source " +
            "src=\"shell://Local%20Sandbox/home/jarm/clips/a.mp4\" type=\"video/mp4\"></video>"
        assertEquals(
            "![](shell://Local%20Sandbox/home/jarm/clips/a.mp4)",
            element.embedHtmlVideoTags(),
        )
        assertEquals(
            "![](/home/jarm/clips/a.mp4)",
            "<video src=\"/home/jarm/clips/a.mp4\"></video>".embedHtmlVideoTags(),
        )
        assertEquals(
            "![](/home/jarm/clips/a.mp4)",
            "<video src=\"/home/jarm/clips/a.mp4\" />".embedHtmlVideoTags(),
        )
        val notVideo = "<video src=\"/home/jarm/clips/a.png\"></video>"
        assertEquals(notVideo, notVideo.embedHtmlVideoTags())
    }

    @Test
    fun fencedHtmlVideoStaysVerbatim() {
        val fenced = "```html\n<video src=\"/home/jarm/clips/a.mp4\"></video>\n```"
        assertEquals(fenced, fenced.embedHtmlVideoTags())
    }

    @Test
    fun renderableMarkdownEmbedsHtmlVideo() {
        assertEquals(
            "![](/home/jarm/clips/a.mp4)",
            "<video src=\"/home/jarm/clips/a.mp4\"></video>".toRenderableMarkdownText(),
        )
    }

    @Test
    fun nonVideoPathsKeepTheirText() {
        val text = "/home/jarm/clips/a.png"
        assertEquals(text, text.embedStandaloneVideoPaths())
        val unsupported = "/etc/a.mp4"
        assertEquals(unsupported, unsupported.embedStandaloneVideoPaths())
    }

    @Test
    fun renderableMarkdownEmbedsTheStandalonePath() {
        assertEquals(
            "![](/home/jarm/clips/a.mp4)",
            "/home/jarm/clips/a.mp4".toRenderableMarkdownText(),
        )
    }
}
