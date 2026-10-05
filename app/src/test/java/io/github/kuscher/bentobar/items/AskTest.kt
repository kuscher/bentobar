package io.github.kuscher.bentobar.items

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executor

/** One question at a time: what shows is the answer to the last thing asked, or nothing. */
class AskTest {
    private val waiting = ArrayDeque<Runnable>()
    private var held = false
    private var changes = 0
    private val wiring = Refresher.Wiring(Executor { r -> if (held) waiting.addLast(r) else r.run() }, { it.run() }, { 0L }, { changes++ })
    private fun run() { while (waiting.isNotEmpty()) waiting.removeFirst().run() }

    private val search = Ask<String, List<String>>(wiring) { q -> if (q == "bug") error("a bug") else listOf("$q, Illinois", "$q, Missouri") }

    @Test fun anAnswerArrives() {
        assertEquals(Ask.State.Idle, search.state.value)
        search.ask("Springfield")
        assertEquals(Ask.State.Done("Springfield", listOf("Springfield, Illinois", "Springfield, Missouri")), search.state.value)
        assertEquals(1, changes)
    }

    @Test fun whileItIsOnItsWayTheStateSaysSo() {
        held = true
        search.ask("Springfield")
        assertEquals(Ask.State.Busy("Springfield"), search.state.value)
        run()
        assertEquals("Springfield", (search.state.value as Ask.State.Done).question)
    }

    @Test fun aNewerQuestionDropsTheAnswerToTheOlderOne() {
        held = true
        search.ask("Spring")
        search.ask("Springfield")
        run() // both answers arrive, the older one first
        assertEquals(Ask.State.Done("Springfield", listOf("Springfield, Illinois", "Springfield, Missouri")), search.state.value)
        assertEquals(1, changes)
    }

    @Test fun clearDropsAnAnswerOnItsWay() {
        held = true
        search.ask("Springfield")
        search.clear()
        assertEquals(Ask.State.Idle, search.state.value)
        run()
        assertEquals(Ask.State.Idle, search.state.value)
        assertEquals(0, changes)
    }

    @Test fun workThatThrowsLeavesNothingShowing() {
        search.ask("bug")
        assertEquals(Ask.State.Idle, search.state.value)
    }
}
