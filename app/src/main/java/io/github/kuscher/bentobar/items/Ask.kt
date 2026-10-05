package io.github.kuscher.bentobar.items

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.RejectedExecutionException

/**
 * One question at a time, asked in the background: a city search, the lookup when a flight is first
 * tracked. A menu or a settings field calls [ask] and draws [state]. A newer question, or [clear],
 * drops the answer to the older one when it arrives, so what shows is never the answer to something
 * else. Pure Kotlin, like [Refresher], whose [Refresher.Wiring] it shares.
 *
 * @param work runs on the background thread. A failure is an answer too (it says what went wrong);
 *   if it throws, the state goes back to [State.Idle].
 */
class Ask<Q : Any, A : Any>(private val wiring: Refresher.Wiring, private val work: (Q) -> A) {
    sealed interface State<out Q, out A> {
        /** Nothing asked, or the last answer was cleared. */
        data object Idle : State<Nothing, Nothing>
        /** [question] is on its way. */
        data class Busy<Q>(val question: Q) : State<Q, Nothing>
        data class Done<Q, A>(val question: Q, val answer: A) : State<Q, A>
    }

    private val current = MutableStateFlow<State<Q, A>>(State.Idle)
    val state: StateFlow<State<Q, A>> get() = current

    /**
     * Counts questions and clears: an answer is shown only if nothing came after its question, and a
     * question that was overtaken while it waited for its turn is not asked at all. Written on the
     * main thread, read on the background one.
     */
    @Volatile private var generation = 0

    /** Asks; the state is [State.Busy] until the answer is in. Main thread. */
    fun ask(question: Q) {
        val mine = ++generation
        current.value = State.Busy(question)
        try {
            wiring.background.execute {
                // Overtaken or cleared while it waited: nobody wants this answer, so nothing is sent for it.
                if (mine != generation) return@execute
                val answer = runCatching { work(question) }.getOrNull()
                wiring.main(Runnable {
                    if (mine != generation) return@Runnable
                    current.value = if (answer == null) State.Idle else State.Done(question, answer)
                    wiring.changed()
                })
            }
        } catch (e: RejectedExecutionException) {
            current.value = State.Idle
        }
    }

    /** Back to [State.Idle]; an answer still on its way is dropped. Main thread. */
    fun clear() {
        generation++
        current.value = State.Idle
    }
}
