package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Kept
import io.github.kuscher.bentobar.data.Online
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.net.Host
import io.github.kuscher.bentobar.net.Http
import io.github.kuscher.bentobar.net.Reply
import io.github.kuscher.bentobar.net.Request
import io.github.kuscher.bentobar.net.Why
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor

// Everything of the Weather item that goes online or keeps something: the two requests, what a reply
// or a failure becomes, the last reading on the device. And WeatherSource, the one way from the item
// to all of it, which decides when anything is asked at all. No Android, and nothing here is logged:
// a city and its coordinates pass through this file.

/** What a search came to. Printed, none of them names a place. */
sealed interface Found {
    /** The places the service found, five at most. None: "No place found". */
    class Places(val list: List<City>) : Found
    data object Offline : Found
    data object NoAnswer : Found
    /** Not asked at all (the item is in Off, nothing shows items): nothing is shown for it. */
    data object Unasked : Found
}

/** One press of Search: the text that is sent, and which field asked ([by]), so that an answer shows only where it was asked for. */
class Query(val text: String, val by: String) {
    override fun toString() = "a search"
}

object WeatherLoad {
    private val service = Online.Service.OPEN_METEO

    /**
     * The forecast for a place: its coordinates with two decimals, and which values. Always metric
     * (the service's default) and converted on the device, so no unit is ever sent; the city's time
     * zone is worked out by the service ("auto"), so the device's own isn't sent either.
     */
    fun forecastRequest(place: Place) = Request(Host.OPEN_METEO, "/v1/forecast", listOf(
        "latitude" to place.lat,
        "longitude" to place.lon,
        "current" to "temperature_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m",
        "hourly" to "temperature_2m,precipitation_probability,weather_code,is_day",
        "daily" to "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset",
        "timezone" to "auto",
        "timeformat" to "unixtime",
        "forecast_days" to "7",
        "forecast_hours" to "24",
    ))

    /** The city search: the text of the field and nothing of the user. No language is sent (the consent words name none), so names come in English. */
    fun searchRequest(text: String) = Request(Host.OPEN_METEO_GEOCODING, "/v1/search", listOf("name" to text, "count" to "5", "format" to "json"))

    /**
     * The places a reading is kept for: those of the layout's Weather items that are not turned off.
     * An item in Off keeps its settings and nothing else. Its reading goes while the type still has a
     * live item to look at the layout (the tick that turns it off); kept with an item that is off, it
     * would outlive the item, since nothing runs for a type whose last item is off when that is deleted.
     */
    fun places(items: List<ItemConfig>): Set<Place> =
        items.filter { it.type == "weather" && it.section != Section.OFF }.mapNotNullTo(HashSet()) { WeatherRules.place(it) }

    /**
     * Asks the service about [place]; blocks, so only a background load calls it. A good answer is a
     * new [Reading], kept on the device. A failure is a reading too: [last]'s numbers with what went
     * wrong, so the bar keeps what it shows. Null: it was not asked after all (the switch went off or
     * the bar hid under the load), which is neither. [now] is the wall clock and [up] the time since
     * boot, both of this moment; [places]: the layout's places right now.
     */
    fun load(place: Place, last: Reading?, now: Long, up: Long = 0, places: () -> Set<Place>): Reading? {
        val before = Http.sent(Host.OPEN_METEO)
        val reply = Http.get(forecastRequest(place))
        // Without a network the request helper answers by itself and nothing leaves the device.
        val went = Http.sent(Host.OPEN_METEO) != before
        val good = (reply as? Reply.Ok)?.let { WeatherRules.read(it.text, place, now, up) }
        if (good != null) {
            keep(good, places())
            return good
        }
        val failed = reply as? Reply.Failed
        if (failed?.why == Why.OFF) return null
        val wait = failed?.retryAfterSec ?: 0
        val failure = when {
            failed?.why == Why.OFFLINE -> Failure.OFFLINE
            // 429 is "slow down"; so is any answer that asks for more patience than the usual retry, so that the words and the wait agree.
            failed?.status == 429 || wait * 1000 > WeatherRules.RETRY_MS -> Failure.SLOW_DOWN
            // A status, a timeout, too much, or a reply that is no forecast (a public network's sign-in page).
            else -> Failure.NO_ANSWER
        }
        return failed(place, last, failure, went, wait)
    }

    /**
     * What a failed try leaves: the numbers that were there for [place] (never another place's), with
     * what went wrong. [went]: the request did go out, so it counts as one more miss in a row.
     */
    fun failed(place: Place, last: Reading?, failure: Failure, went: Boolean, waitSec: Long = 0): Reading {
        val known = last?.takeIf { it.place == place.key } ?: Reading(place.key)
        return known.copy(failure = failure, misses = known.misses + if (went) 1 else 0, retryAfterSec = waitSec)
    }

    /**
     * Keeps a good reading for a restart, and removes what belongs to no item any more. A place the
     * layout no longer has (its item was deleted or its city changed while the answer was on its way)
     * is not kept. While the service is off nothing is written at all (see [Kept.fetched]).
     */
    private fun keep(reading: Reading, places: Set<Place>) {
        try {
            val kept = Kept.fetched(service)
            if (places.any { it.key == reading.place }) kept.write(reading.place, WeatherRules.keep(reading), reading.fetchedAt)
            kept.keepOnly(places.mapTo(HashSet()) { it.key })
        } catch (e: Exception) {
            // Nothing kept: the next start asks the service instead.
        }
    }

    /** "Deleted with the item": what is kept for a place that is not among [places] goes. Disk work, for a background thread. */
    fun tidy(places: Set<Place>) {
        try { Kept.fetched(service).keepOnly(places.mapTo(HashSet()) { it.key }) } catch (e: Exception) { /* nothing there to remove */ }
    }

    /**
     * What was kept for [place] and how old it is by the wall clock, so that a restart shows the last
     * reading with its time and asks nobody while it is fresh. What can't be read back is removed.
     */
    fun kept(place: Place, now: Long): Refresher.Restored<Reading>? = try {
        val kept = Kept.fetched(service)
        val entry = kept.read(place.key)
        val reading = entry?.let { WeatherRules.kept(it.text) }?.takeIf { it.place == place.key }
        if (entry != null && reading == null) kept.remove(place.key)
        reading?.let { Refresher.Restored(it, (now - it.fetchedAt).coerceAtLeast(0)) }
    } catch (e: Exception) {
        null
    }

    /** Asks the service for places named [text]; blocks. */
    fun search(text: String): Found = when (val reply = Http.get(searchRequest(text))) {
        is Reply.Ok -> WeatherRules.cities(reply.text)?.let { Found.Places(it) } ?: Found.NoAnswer
        is Reply.Failed -> when (reply.why) {
            Why.OFF -> Found.Unasked
            Why.OFFLINE -> Found.Offline
            else -> Found.NoAnswer
        }
    }
}

/**
 * The Weather item's one way to the service: everything the item, its menu and its settings do that
 * could send something goes through here, and the tests drive exactly this. The rules it holds:
 *
 * Nothing is asked for an item without a city, for one in Off, or while the service is switched off;
 * and it is switched on in two places only, [search] and [turnOn], which are the user's own acts.
 * One reading per place serves every item with that city.
 */
class WeatherSource(
    private val readings: Refresher<Place, Reading>,
    private val asking: Ask<Query, Found>,
    private val background: Executor,
    /** The wall clock, and the time since boot: a reading's age is asked of both ([WeatherRules.old]). */
    private val wall: () -> Long,
    private val up: () -> Long,
) {
    private val service = Online.Service.OPEN_METEO

    /** The place [item] may ask about right now, or null: no city, an item in Off, or the switch off. */
    private fun asked(item: ItemConfig): Place? =
        if (item.section == Section.OFF || !Online.on(service)) null else WeatherRules.place(item)

    /**
     * What is known for [item]'s place, loading it first if that is due. This is what the bar's state
     * calls, every time it is computed. Null: nothing yet, no city, or the switch off. Main thread.
     */
    fun reading(item: ItemConfig): Reading? {
        if (!Online.on(service)) return null
        val place = WeatherRules.place(item) ?: return null
        if (item.section != Section.OFF) {
            readings.want(place)
            askIfOld(place)
        }
        return readings.peek(place)
    }

    /**
     * The loader asks again by the time since boot, half an hour after a reading. "Too old to show"
     * is also read off the clock on the wall, and that can be set: two minutes after a reading it is
     * four hours later, the numbers go, and the item says "Loading…" while nothing loads. So a reading
     * that is too old and has no failure to explain it is asked for now (not within a minute of the
     * last try, like Refresh). One whose last try failed keeps to the failure's pace.
     */
    private fun askIfOld(place: Place) {
        val reading = readings.peek(place) ?: return
        if (reading.failure == null && WeatherRules.old(reading, wall(), up()) && !readings.loading(place))
            readings.refresh(place, floorMs = WeatherRules.AGAIN_MS)
    }

    /** What there is for [item]'s place, asking nothing. Any thread. */
    fun peek(item: ItemConfig): Reading? = WeatherRules.place(item)?.let { readings.peek(it) }

    /** The state [item] is in, asking nothing. */
    fun status(item: ItemConfig, now: Long): Status {
        val on = Online.on(service)
        return WeatherRules.status(hasPlace = WeatherRules.place(item) != null, on = on, setUp = Online.setUp(service),
            reading = if (on) peek(item) else null, now = now, up = up())
    }

    fun loading(item: ItemConfig): Boolean = WeatherRules.place(item)?.let { readings.loading(it) } ?: false

    /**
     * The item's menu opened: a good reading older than ten minutes is asked again. One whose last
     * try failed keeps to its own pace, as its words say ("tries again in 15 minutes"). And without
     * a network a good reading of any age is tried at once: nothing goes out for that, and the menu
     * then says "no connection" beside the reading's time instead of passing it off as live.
     * Main thread.
     */
    fun opened(item: ItemConfig) {
        val place = asked(item) ?: return
        val reading = readings.peek(place)
        when {
            reading == null -> readings.want(place)
            reading.failure != null -> {}
            // Too old to show (the clock was set on): the menu would say "Loading…", so it is.
            WeatherRules.old(reading, wall(), up()) -> askIfOld(place)
            else -> readings.refresh(place, floorMs = if (Http.connected()) WeatherRules.MENU_MS else 0)
        }
    }

    /** How soon after the last try Refresh may ask again: a minute, or at once when that try found no network and so cost nothing. */
    private fun floor(reading: Reading?): Long = if (reading?.failure == Failure.OFFLINE && reading.misses == 0) 0 else WeatherRules.AGAIN_MS

    /** Whether Refresh (or Try again) would ask now: the entry is dimmed while it wouldn't. Main thread. */
    fun mayAgain(item: ItemConfig): Boolean {
        val place = asked(item) ?: return false
        return !readings.loading(place) && (readings.age(place) ?: Long.MAX_VALUE) >= floor(readings.peek(place))
    }

    /** Refresh, or Try again: asks now, but once a minute at most. False: nothing was asked. Main thread. */
    fun again(item: ItemConfig): Boolean {
        val place = asked(item) ?: return false
        return !readings.loading(place) && readings.refresh(place, floorMs = floor(readings.peek(place)))
    }

    /** The search's state, for whoever draws it; see [shown]. */
    val searching: StateFlow<Ask.State<Query, Found>> get() = asking.state

    /**
     * Search, or Enter in the field: the one act that sends a name, and with it the Weather switch
     * goes on. Typing never comes here. False, and nothing happens: fewer than two letters, or an
     * item in Off, which may ask nothing. [by] names the field that asked. Main thread.
     */
    fun search(text: String, item: ItemConfig, by: String): Boolean {
        val name = WeatherRules.query(text) ?: return false
        if (item.section == Section.OFF) return false
        Online.turnOn(service)
        asking.ask(Query(name, by))
        return true
    }

    /** The text of the field [by] changed: what was found for the old text goes. Nothing is sent. Main thread. */
    fun typed(by: String) {
        val asker = when (val s = asking.state.value) {
            is Ask.State.Busy -> s.question.by
            is Ask.State.Done -> s.question.by
            Ask.State.Idle -> null
        }
        if (asker == by) asking.clear()
    }

    /** "Turn on weather": for a layout that came with a city, or after the switch was turned off in Setup. */
    fun turnOn(): Boolean = Online.turnOn(service)

    /**
     * The layout's places are now [places]: readings of any other place go, from memory at once and
     * from the device in the background ("deleted with the item"). On an install that never set
     * Weather up nothing was ever fetched, so there is nothing to look for. Main thread.
     */
    fun keepOnly(places: Set<Place>) {
        readings.keepOnly(places)
        if (Online.setUp(service)) background.execute { WeatherLoad.tidy(places) }
    }

    companion object {
        /**
         * The source with its loader and its search wired to [WeatherLoad]: the app and the tests make
         * it here alike and differ only in where work runs and what time it is. [wall]: the wall
         * clock; [up]: the time since boot, which the loader counts by (the app's `Now.elapsed`);
         * [layout]: the layout's items right now (asked from the background thread).
         * [staged]: a debug build's test hook; when it names a failure, the load that asked is not
         * sent and comes back as that failure, so the pace after an error can be watched on a device.
         */
        fun make(
            refresher: (every: (Place, Reading) -> Long?, restore: (Place) -> Refresher.Restored<Reading>?, load: (Place, Reading?) -> Reading?) -> Refresher<Place, Reading>,
            ask: (work: (Query) -> Found) -> Ask<Query, Found>,
            background: Executor,
            wall: () -> Long,
            up: () -> Long,
            layout: () -> List<ItemConfig>,
            staged: () -> Failure? = { null },
        ): WeatherSource = WeatherSource(
            refresher({ _, reading -> WeatherRules.every(reading) }, { place -> WeatherLoad.kept(place, wall()) },
                { place, last ->
                    // "No connection" is what nothing-went-out looks like; the other two stand for a try that did.
                    staged()?.let { WeatherLoad.failed(place, last, it, went = it != Failure.OFFLINE) }
                        ?: WeatherLoad.load(place, last, wall(), up()) { WeatherLoad.places(layout()) }
                }),
            ask { query -> WeatherLoad.search(query.text) },
            background, wall, up,
        )

        /** [state] as the field [by] shows it: an answer to another field's question is none of its business. */
        fun shown(state: Ask.State<Query, Found>, by: String): Ask.State<Query, Found> = when (state) {
            is Ask.State.Busy -> if (state.question.by == by) state else Ask.State.Idle
            is Ask.State.Done -> if (state.question.by == by) state else Ask.State.Idle
            Ask.State.Idle -> state
        }
    }
}
