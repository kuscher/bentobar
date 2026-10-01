package io.github.kuscher.bentobar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The other Commonwealth English variants are copies of en-GB; this keeps them from drifting. */
class LocaleCopiesTest {
    private val res = File("src/main/res")
    private fun strings(dir: String) = File(res, "$dir/strings.xml").readLines().map { it.trim() }.filter { it.startsWith("<string") }

    @Test fun copiesMatchBritish() {
        val gb = strings("values-en-rGB")
        assertTrue("en-GB has no strings", gb.isNotEmpty())
        for (r in listOf("AU", "NZ", "IE", "IN", "ZA")) assertEquals("values-en-r$r differs from en-GB", gb, strings("values-en-r$r"))
    }

    @Test fun everyBritishKeyExistsInTheDefault() {
        val key = Regex("""name="([^"]+)"""")
        val default = strings("values").mapNotNull { key.find(it)?.groupValues?.get(1) }.toSet()
        strings("values-en-rGB").mapNotNull { key.find(it)?.groupValues?.get(1) }.forEach { assertTrue("$it missing from values/", it in default) }
    }
}
