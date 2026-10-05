package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.Kept
import io.github.kuscher.bentobar.data.Online
import io.github.kuscher.bentobar.items.AirLabs.Failure
import io.github.kuscher.bentobar.items.FlightRules.Tracked
import io.github.kuscher.bentobar.net.Http
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * The Flight item's requests, and what it keeps. Everything here runs on a background thread (a
 * `Refresher`'s load and restore, an `Ask`'s work) except [take] and [stop], which are the user's own
 * act in the menu and touch two small files.
 *
 * What leaves the device: the flight number and the user's own key, to airlabs.co, and nothing else.
 * Never without a key, never while the service is switched off: asked here before any request, though
 * the request code would refuse too.
 *
 * What is kept, by the item's id and outside the layout (so in no backup and no copied settings):
 * what the item follows (the number, and the day that flight leaves) as the feature's own note, and
 * the last answer as [Tracked] with what the service sent. Switching the service off deletes the
 * answers and keeps the notes; removing the key deletes both. The key itself is `Online`'s: it is read
 * here for the one request that needs it and goes into nothing that is kept, shown or said.
 */
object FlightLoad {
    private const val OWN = "flight"
    private val service = Online.Service.AIRLABS
    private val json = Json { ignoreUnknownKeys = true }
    /** The answer for an item and its note are read and written under this, so that a load that comes back late can't put an old flight over a new one. */
    private val lock = Any()

    /** What an item was told to follow: the number as it was entered, closed up, and the day that flight leaves at its own airport. */
    @Serializable
    private class Following(val number: String, val day: String? = null)

    /** One press of Track: which item asks, the number, and the day that was chosen (null: the next flight). */
    data class Question(val item: String, val number: FlightNumber, val day: LocalDate? = null) {
        /** Whose flight it is stays out of anything that prints a value. */
        override fun toString(): String = "Question(a flight)"
    }

    /** What a press of Track came to. */
    sealed interface Outcome {
        /** The flight, as it is kept once the answer is taken. */
        class Found(val tracked: Tracked) : Outcome
        class Failed(val failure: Failure) : Outcome
        /** Nothing was asked (no key, the service switched off, the bar gone under the lookup): there is nothing to say. */
        data object Unasked : Outcome
    }

    /** How many lookups the key has left this month, as the last reply said; null: no reply has yet. It is the key's: another key starts with none. */
    @Volatile var left: Int? = null
        private set

    private fun said(count: Int?) { if (count != null) left = count }

    /** The numbers that were not found, and when: finding that out costs two lookups, so it is remembered for an hour (in memory only). */
    private val missed = ConcurrentHashMap<String, Pair<Failure, Long>>()

    private fun name(q: Question) = "${q.number.code}@${q.day}"

    private fun notFoundLately(q: Question, now: Long): Failure? {
        val (failure, at) = missed[name(q)] ?: return null
        if (now - at in 0 until AirLabs.keep(failure).toMillis()) return failure
        missed.remove(name(q))
        return null
    }

    /** [text] and the chosen [day] as a question for [item], or null: what is not a flight number is never asked. */
    fun question(item: String, text: String, day: LocalDate?): Question? = FlightNumber.read(text)?.let { Question(item, it, day) }

    private fun mayAsk(key: String) = key.isNotEmpty() && Online.on(service)

    /**
     * Finds the flight for a press of Track: one request in the usual case, three at most. A number
     * that was not found in the last hour is not asked for again.
     */
    fun track(q: Question, now: Long): Outcome {
        val key = Online.key(service)
        if (!mayAsk(key)) return Outcome.Unasked
        notFoundLately(q, now)?.let { return Outcome.Failed(it) }
        val a = AirLabs.lookup(q.number, q.day, key, Instant.ofEpochMilli(now)) { Http.get(it) } ?: return Outcome.Unasked
        said(a.left)
        val f = a.flight
        if (f == null) {
            val failure = a.failure ?: Failure.NO_ANSWER
            if (failure == Failure.NOT_FOUND || failure == Failure.NOT_THAT_DAY) {
                if (missed.size >= 32) missed.clear()
                missed[name(q)] = failure to now
            }
            return Outcome.Failed(failure)
        }
        return Outcome.Found(Tracked(q.number.code, day(f), f, askedAt = now, heardAt = now, left = a.left, alertSince = FlightRules.alertSince(f, null, now)))
    }

    /** The day [f] leaves, at its own airport: what an item follows is that day's flight. */
    private fun day(f: Flight): String? = (f.from.planned ?: f.from.time)?.toLocalDate()?.toString()

    /**
     * The answer to a press of Track becomes what [item] follows, in place of what it followed
     * before. Main thread, when the answer is taken, never in the lookup itself: an answer that
     * nobody takes (a newer question overtook it) changes nothing. False if it could not be kept.
     */
    fun take(item: String, found: Tracked, now: Long): Boolean = synchronized(lock) {
        val note = runCatching { json.encodeToString(Following.serializer(), Following(found.number, found.day)) }.getOrNull() ?: return false
        if (!Kept.own(OWN).write(item, note, now)) return false
        store(item, found, now)
        true
    }

    /** [item] follows nothing any more: its note and the answer are deleted. */
    fun stop(item: String) = synchronized(lock) {
        Kept.own(OWN).remove(item)
        Kept.fetched(service).remove(item)
    }

    private fun following(item: String): Following? {
        val entry = Kept.own(OWN).read(item) ?: return null
        val asked = runCatching { json.decodeFromString(Following.serializer(), entry.text) }.getOrNull()?.takeIf { it.number.isNotEmpty() }
        if (asked == null) Kept.own(OWN).remove(item)
        return asked
    }

    private fun Following.isFor(t: Tracked) = number == t.number && day == t.day

    /** Nothing a file holds is taken on trust either: a flight the rules could trip over is no flight. */
    private fun decode(text: String): Tracked? =
        runCatching { json.decodeFromString(Tracked.serializer(), text) }.getOrNull()?.takeIf { t -> t.flight?.let(FlightRules::sound) != false }

    /** Only the model made here is ever written: what a reply said, field by field, and never its text. Not kept while the service is off. */
    private fun store(item: String, t: Tracked, now: Long) {
        runCatching { json.encodeToString(Tracked.serializer(), t) }.getOrNull()?.let { Kept.fetched(service).write(item, it, now) }
    }

    /** What belonged to an item that is no longer in the layout goes with it. (An empty [items] means the last one is just being deleted, which may still be undone.) */
    private fun tidy(items: Set<String>) {
        if (items.isEmpty()) return
        Kept.own(OWN).keepOnly(items)
        Kept.fetched(service).keepOnly(items)
    }

    /**
     * What was kept for [item] from an earlier run, and how old it is: a restart shows the flight
     * without asking. Null: nothing was (or not for what the item follows now). A flight that is
     * [FlightRules.cleared] is put away here. [items]: the ids of the Flight items in the layout.
     */
    fun kept(item: String, now: Long, items: Set<String>): Refresher.Restored<Tracked>? = synchronized(lock) {
        tidy(items)
        val entry = Kept.fetched(service).read(item) ?: return null
        val t = decode(entry.text)
        if (t == null || following(item)?.isFor(t) != true) {
            Kept.fetched(service).remove(item)
            return null
        }
        if (FlightRules.cleared(t, Instant.ofEpochMilli(now))) {
            stop(item)
            return Refresher.Restored(Tracked(), 0)
        }
        t.left?.let { if (left == null) left = it }
        Refresher.Restored(t, (now - entry.savedAt).coerceAtLeast(0))
    }

    /**
     * Asks again about what [item] follows: one request about the one flight ([AirLabs.again]). With
     * nothing heard of it yet (the service was switched off and on again, which deletes the answer and
     * keeps the note) it is the lookup for that number on its day. [last]: what was known. The answer
     * is a [Tracked] whatever came of it: a failed ask keeps the flight and says why.
     *
     * Null: nothing to say. The request was not sent (no key, switched off, the bar gone), or the item
     * follows something else by now.
     */
    fun load(item: String, last: Tracked?, now: Long, items: Set<String>): Tracked? {
        val key = Online.key(service)
        if (!mayAsk(key)) return null
        val asked = synchronized(lock) { tidy(items); following(item) } ?: return Tracked()
        val was = last?.takeIf { asked.isFor(it) }
        val number = FlightNumber.read(asked.number)
        if (number == null || (was != null && FlightRules.cleared(was, Instant.ofEpochMilli(now)))) {
            stop(item)
            return Tracked()
        }
        val flight = was?.flight
        val next = (if (was == null || flight == null) lookUp(asked, number, was, key, now) else again(number, was, flight, key, now)) ?: return null
        return synchronized(lock) {
            if (following(item)?.isFor(next) == true) next.also { store(item, it, now) } else null
        }
    }

    private fun lookUp(asked: Following, number: FlightNumber, was: Tracked?, key: String, now: Long): Tracked? {
        val day = asked.day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val a = AirLabs.lookup(number, day, key, Instant.ofEpochMilli(now)) { Http.get(it) } ?: return null
        said(a.left)
        val left = a.left ?: was?.left
        val f = a.flight ?: return Tracked(asked.number, asked.day, failure = a.failure ?: Failure.NO_ANSWER, failures = (was?.failures ?: 0) + 1, askedAt = now, left = left)
        return Tracked(asked.number, asked.day, f, askedAt = now, heardAt = now, left = left, alertSince = FlightRules.alertSince(f, null, now))
    }

    private fun again(number: FlightNumber, was: Tracked, flight: Flight, key: String, now: Long): Tracked? {
        val r = AirLabs.again(number, key, flight) { Http.get(it) } ?: return null
        said(r.left)
        val left = r.left ?: was.left
        return when (val a = r.again) {
            is AirLabs.Again.Is -> was.copy(flight = a.flight, failure = null, failures = 0, askedAt = now, heardAt = now, left = left,
                alertSince = FlightRules.alertSince(a.flight, was.alertSince, now), ended = false, waitSec = null)
            // The service is not there yet: the plan stands, as of now.
            AirLabs.Again.NotYet -> was.copy(failure = null, failures = 0, askedAt = now, heardAt = now, left = left, waitSec = null)
            // A key that is refused or used up may be put right, and the flight is still the one that is followed; anything else is the end of it.
            AirLabs.Again.Gone -> (r.failure == Failure.REFUSED || r.failure == Failure.USED_UP).let { ofTheKey ->
                was.copy(failure = r.failure.takeIf { ofTheKey }, failures = 0, askedAt = now, left = left, ended = !ofTheKey, waitSec = null)
            }
            AirLabs.Again.Failed -> was.copy(failure = r.failure ?: Failure.NO_ANSWER, failures = was.failures + 1, askedAt = now, left = left, waitSec = r.retryAfterSec)
        }
    }

    /**
     * The service was switched off, or ([keyGone]) its key removed: what it said is forgotten here too
     * (`Online` has emptied the answers already). Without the key, what the items followed goes as
     * well: Remove key deletes the key, the flight and the answer.
     */
    fun forget(keyGone: Boolean) {
        left = null
        missed.clear()
        if (keyGone) synchronized(lock) { Kept.own(OWN).clear() }
    }

    /** A new key was saved: how many lookups the old one had left says nothing about it. */
    fun newKey() { left = null }
}
