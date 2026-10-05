package io.github.kuscher.bentobar.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Small texts kept on the device: written whole, read back with their time, and gone when asked. */
class KeptTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "bentobar-kept-${System.nanoTime()}")
    private val kept = Kept(dir)

    @After fun gone() { dir.deleteRecursively() }

    @Test fun whatIsWrittenComesBackWithItsTime() {
        assertNull(kept.read("zurich"))
        assertTrue(kept.write("zurich", "{\"temperature\":15.7}\nsecond line", 1_791_184_500_000))
        val e = kept.read("zurich")!!
        assertEquals("{\"temperature\":15.7}\nsecond line", e.text)
        assertEquals(1_791_184_500_000, e.savedAt)
    }

    @Test fun aSecondWriteTakesTheFirstsPlace() {
        kept.write("zurich", "old", 1)
        kept.write("zurich", "new", 2)
        assertEquals("new", kept.read("zurich")!!.text)
        assertEquals(setOf("zurich"), kept.names())
        assertEquals(1, dir.listFiles()!!.size) // nothing half-written is left beside it
    }

    @Test fun aNameBecomesASafeFileName() {
        assertEquals("47.37_8.55", Kept.safe("47.37,8.55"))
        assertEquals("_.._etc_passwd", Kept.safe("/../etc/passwd"))
        assertEquals("_", Kept.safe(""))
        assertEquals("_", Kept.safe(".."))
        assertEquals(80, Kept.safe("x".repeat(200)).length)
        kept.write("47.37,8.55", "a", 1)
        kept.write("../outside", "b", 1)
        assertEquals("a", kept.read("47.37,8.55")!!.text)
        // Whatever the name, the file is inside the directory.
        assertTrue(dir.listFiles()!!.all { it.parentFile == dir })
        assertFalse(File(dir.parentFile, "outside.txt").exists())
    }

    @Test fun whatCannotBeReadIsRemoved() {
        dir.mkdirs()
        File(dir, "broken.txt").writeText("no time on the first line")
        assertNull(kept.read("broken"))
        assertFalse(File(dir, "broken.txt").exists())
    }

    @Test fun removeKeepOnlyAndClear() {
        for (n in listOf("a", "b", "c", "d")) kept.write(n, n, 1)
        kept.remove("a")
        assertEquals(setOf("b", "c", "d"), kept.names())
        kept.keepOnly(setOf("b", "c", "x"))
        assertEquals(setOf("b", "c"), kept.names())
        kept.clear()
        assertEquals(emptySet<String>(), kept.names())
        kept.clear() // a directory that isn't there is empty too
        kept.keepOnly(setOf("a"))
    }

    @Test fun textThatIsTooLongIsNotKept() {
        val small = Kept(dir, maxChars = 10)
        assertFalse(small.write("big", "x".repeat(11), 1))
        assertNull(small.read("big"))
        assertTrue(small.write("fits", "x".repeat(10), 1))
    }

    @Test fun withTooManyTheOldestGo() {
        val three = Kept(dir, maxEntries = 3)
        for ((i, n) in listOf("a", "b", "c").withIndex()) {
            three.write(n, n, 1)
            File(dir, "$n.txt").setLastModified(1_000_000L * (i + 1))
        }
        three.write("d", "d", 1)
        assertEquals(setOf("b", "c", "d"), three.names())
    }

    @Test fun whatAServiceSentAndWhatAFeatureKeepsLiveApart() {
        Online.init(dir)
        try {
            Online.turnOn(Online.Service.OPEN_METEO)
            Online.saveKey(Online.Service.AIRLABS, "test-key")
            assertTrue(Kept.fetched(Online.Service.OPEN_METEO).write("zurich", "a reading", 1))
            assertTrue(Kept.fetched(Online.Service.AIRLABS).write("item1", "an answer", 1))
            assertTrue(Kept.own("flight").write("item1", "LH455", 1))
            Kept.fetched(Online.Service.OPEN_METEO).clear()
            assertNull(Kept.fetched(Online.Service.OPEN_METEO).read("zurich"))
            assertEquals("an answer", Kept.fetched(Online.Service.AIRLABS).read("item1")!!.text)
            assertEquals("LH455", Kept.own("flight").read("item1")!!.text)
        } finally {
            Online.init(File(dir, "empty"))
        }
    }

    @Test fun nothingAServiceSentIsKeptWhileItIsOff() {
        // A load that was on its way when the switch went off comes back with an answer: it is not written.
        Online.init(dir)
        try {
            val fetched = Kept.fetched(Online.Service.OPEN_METEO)
            assertFalse(fetched.write("zurich", "a reading", 1))
            Online.turnOn(Online.Service.OPEN_METEO)
            assertTrue(fetched.write("zurich", "a reading", 1))
            Online.turnOff(Online.Service.OPEN_METEO)
            assertFalse(fetched.write("zurich", "the answer that was on its way", 2))
            assertEquals(emptySet<String>(), fetched.names())
            // What a feature keeps for itself has no such switch.
            assertTrue(Kept.own("flight").write("item1", "LH455", 1))
        } finally {
            Online.init(File(dir, "empty"))
        }
    }

    @Test fun oneStoreForOneDirectory() {
        // The same object each time, so a write and a clear of one directory can never cross.
        Kept.init(dir)
        assertTrue(Kept.fetched(Online.Service.AIRLABS) === Kept.fetched(Online.Service.AIRLABS))
        assertTrue(Kept.own("flight") === Kept.own("flight"))
        assertFalse(Kept.own("flight") === Kept.own("weather"))
    }
}
