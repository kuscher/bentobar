package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.WorldCity
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * World clock's rules, apart from its menu so that they are unit-tested on the JVM (`WorldClockTest`):
 * which places the list has and in what order, what a row says now or at a planned moment, the line
 * "Copy times" copies, the list of added cities, and the search for a city to add. Pure Kotlin: the
 * moment, the device's zone, the words and the way a time is written all come from the caller, and
 * nothing here goes anywhere but the zone data the device has.
 */
object WorldClock {
    /** The most cities the list takes. */
    const val MAX_CITIES = 8
    /** The most characters a place's name has. */
    const val NAME_MAX = 30
    /** The most results the search shows. */
    const val RESULTS = 6
    /** The search starts from this many letters. */
    const val MIN_LETTERS = 2

    // ---- zones and names ----

    /** The zone with this id, or null when this device doesn't know it (a layout pasted from a newer one, or typed by hand). */
    fun zone(id: String?): ZoneId? = if (id.isNullOrEmpty()) null else runCatching { ZoneId.of(id) }.getOrNull()

    /** "Tokyo" from "Asia/Tokyo", "Buenos Aires" from "America/Argentina/Buenos_Aires". */
    fun cityOf(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

    /** What a zone's city is in: "Argentina" from "America/Argentina/San_Juan", "Europe" from "Europe/Istanbul". */
    fun regionOf(zoneId: String): String = zoneId.substringBeforeLast('/', "").substringAfterLast('/').replace('_', ' ')

    private val BLANKS = Regex("[\\s\\p{Z}\\p{Cc}]+")

    /** At most [max] characters of [text], and never half of a pair (an emoji is two). */
    private fun cut(text: String, max: Int): String = when {
        text.length <= max -> text
        Character.isHighSurrogate(text[max - 1]) -> text.substring(0, max - 1)
        else -> text.substring(0, max)
    }

    /**
     * [text] as a name is shown: one line, no spaces at its ends or in runs, [NAME_MAX] characters at
     * most. A label or a name can come from a pasted layout, so it is not taken to be short or one line.
     */
    fun clean(text: String): String = cut(cut(text, 10 * NAME_MAX).replace(BLANKS, " ").trim(), NAME_MAX).trimEnd()

    /** What an added city is called: by the name it was given, else by its zone's city. */
    fun nameOf(city: WorldCity): String = clean(city.name).ifEmpty { clean(cityOf(city.zone)) }

    // ---- the places ----

    /** A Clock item of the bar, as the list sees it: its zone and its label ("MUC"). */
    data class Clock(val zone: ZoneId, val label: String = "")

    /**
     * One row of the list: a zone and what it is called. [here]: the device's own zone, the first row.
     * [city]: the city that was added for this zone, if one was (the row can then be removed in the
     * menu; a row can be a Clock item's and an added city's at once).
     */
    data class Place(val zone: ZoneId, val name: String, val here: Boolean = false, val city: WorldCity? = null)

    /**
     * The places of the list at [now]: the device's zone first, then the zones of the Clock items and
     * of the added cities together, one row a zone, west to east by their offsets at this moment and
     * by name where those are equal. A place is named by a Clock item's label where one has one, else
     * by the name given to the added city, else by the zone's city; the device's own row too. A city
     * whose zone this device doesn't know is left out.
     */
    fun places(local: ZoneId, clocks: List<Clock>, cities: List<WorldCity>, now: Long): List<Place> {
        class Found(val zone: ZoneId) { var label = ""; var city: WorldCity? = null }
        val found = LinkedHashMap<String, Found>()
        found[local.id] = Found(local)
        for (c in clocks) found.getOrPut(c.zone.id) { Found(c.zone) }.run { if (label.isEmpty()) label = clean(c.label) }
        for (c in cities) {
            val zone = zone(c.zone) ?: continue
            found.getOrPut(zone.id) { Found(zone) }.run { if (city == null) city = c }
        }
        val at = Instant.ofEpochMilli(now)
        val all = found.values.map { f ->
            val name = f.label.ifEmpty { f.city?.let { clean(it.name) }.orEmpty() }.ifEmpty { cityOf(f.zone.id) }
            Place(f.zone, name, here = f.zone.id == local.id, city = f.city)
        }
        return all.take(1) + all.drop(1).map { it to it.zone.rules.getOffset(at).totalSeconds }
            .sortedWith(compareBy<Pair<Place, Int>> { it.second }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.name }.thenBy { it.first.zone.id })
            .map { it.first }
    }

    // ---- what a row says ----

    /**
     * The words a row is written with, and how a time, a weekday and a date are written for a moment
     * (milliseconds since 1970) in a zone: "2:10 PM", "Wed", "Wed, Oct 7". The menu fills them from
     * the app's texts and `util/Dates`, a test from the copy deck and `java.time`.
     */
    class Words(
        val local: String,
        val sameTime: String,
        val tomorrow: String,
        val yesterday: String,
        /** "Here (Los Angeles)" from "Los Angeles". */
        val here: (name: String) -> String,
        val time: (moment: Long, zone: ZoneId) -> String,
        val weekday: (moment: Long, zone: ZoneId) -> String,
        val date: (moment: Long, zone: ZoneId) -> String,
    )

    /** A row as it is drawn: "Tokyo" over "Tomorrow · +16h", "6:10 AM" at its end, and whether it is night there. */
    data class Row(val place: Place, val title: String, val time: String, val sub: String, val night: Boolean)

    /**
     * The rows for [moment]: now, or with [planned] the moment that is being planned. Under a name
     * stands the day there, where it isn't the device's ("Tomorrow"), and the difference to the
     * device's time at that moment, so a clock change before a planned day shows. A planned moment
     * names its weekday in every row instead: "Wed · +9h", "Thu · +16h".
     */
    fun rows(places: List<Place>, moment: Long, planned: Boolean, words: Words): List<Row> {
        val local = places.firstOrNull { it.here }?.zone ?: return emptyList()
        val at = Instant.ofEpochMilli(moment)
        val here = ZonedDateTime.ofInstant(at, local)
        return places.map { p ->
            val there = ZonedDateTime.ofInstant(at, p.zone)
            val day = if (planned) words.weekday(moment, p.zone) else when (ChronoUnit.DAYS.between(here.toLocalDate(), there.toLocalDate())) {
                0L -> null
                1L -> words.tomorrow
                -1L -> words.yesterday
                // Two days apart, which only the far ends of the date line are: neither word is true, the weekday is.
                else -> words.weekday(moment, p.zone)
            }
            val ahead = there.offset.totalSeconds - here.offset.totalSeconds
            val difference = when { p.here -> words.local; ahead == 0 -> words.sameTime; else -> offset(ahead) }
            Row(p, if (p.here) words.here(p.name) else p.name, words.time(moment, p.zone), listOfNotNull(day, difference).joinToString(" · "), night(there.hour))
        }
    }

    /** A row for a screen reader: "Tokyo, 6:10 AM, Tomorrow · +16h", and [night] ("Night there") where the glyph shows. */
    fun spoken(row: Row, night: String): String = listOfNotNull(row.title, row.time, row.sub, night.takeIf { row.night }).joinToString(", ")

    /**
     * A difference in time as the list writes it: whole hours as "+9h" and "−3h", others in hours and
     * minutes, "+5:30", "+5:45", "−9:30". The minus is the real sign (U+2212), not a hyphen.
     */
    fun offset(seconds: Int): String {
        val minutes = Math.abs(seconds) / 60
        val sign = if (seconds < 0) "−" else "+"
        return if (minutes % 60 == 0) "$sign${minutes / 60}h" else "$sign${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    }

    /** Night there: from 10 in the evening until 7 in the morning. */
    fun night(hour: Int): Boolean = hour >= 22 || hour < 7

    /** Whether a clock in [zone] shows another time than the device's at this moment: the Clock item's rule. */
    fun away(zone: ZoneId, local: ZoneId, now: Long): Boolean = Instant.ofEpochMilli(now).let { zone.rules.getOffset(it) != local.rules.getOffset(it) }

    /**
     * The line "Copy times" puts on the clipboard for [moment]: the device's date, time and place
     * first, then the other rows in their order, with a weekday only where the date isn't the
     * device's: "Wed, Oct 7 · 9:00 AM San Francisco · 6:00 PM Munich · Thu 1:00 AM Tokyo".
     */
    fun copyLine(places: List<Place>, moment: Long, words: Words): String {
        val here = places.firstOrNull { it.here } ?: return ""
        val at = Instant.ofEpochMilli(moment)
        val today = at.atZone(here.zone).toLocalDate()
        val parts = ArrayList<String>()
        parts += words.date(moment, here.zone)
        parts += "${words.time(moment, here.zone)} ${here.name}"
        for (p in places) if (!p.here) {
            val weekday = if (at.atZone(p.zone).toLocalDate() != today) words.weekday(moment, p.zone) + " " else ""
            parts += "$weekday${words.time(moment, p.zone)} ${p.name}"
        }
        // The system can put a narrow or a no-break space before "AM"; in a mail or a chat a plain one pastes best.
        return parts.joinToString(" · ").replace(' ', ' ').replace(' ', ' ')
    }

    // ---- the added cities ----

    fun full(cities: List<WorldCity>): Boolean = cities.size >= MAX_CITIES

    /** Whether the list has a city of this zone: it is not added a second time. */
    fun added(cities: List<WorldCity>, zone: String): Boolean = cities.any { it.zone == zone }

    /**
     * [cities] with one more at its end; the same list when it is full, has this zone already, or the
     * zone is none this device knows.
     */
    fun add(cities: List<WorldCity>, zone: String, name: String = ""): List<WorldCity> =
        if (full(cities) || added(cities, zone) || zone(zone) == null) cities else cities + WorldCity(zone, clean(name))

    fun remove(cities: List<WorldCity>, zone: String): List<WorldCity> = cities.filterNot { it.zone == zone }

    /**
     * The city of [zone] called [name], kept as it is typed (a space at the end is the start of the
     * next word) up to [NAME_MAX] characters; a row shows it through [nameOf].
     */
    fun rename(cities: List<WorldCity>, zone: String, name: String): List<WorldCity> =
        cities.map { if (it.zone == zone) it.copy(name = cut(name, NAME_MAX)) else it }

    /**
     * The added cities as settings list them: one a zone, in the menu's order (west to east at [now],
     * by name where the offsets are equal). One whose zone this device doesn't know has no row in the
     * menu; here it comes last, so that it can still be removed.
     */
    fun ordered(cities: List<WorldCity>, now: Long): List<WorldCity> {
        val at = Instant.ofEpochMilli(now)
        val placed = cities.distinctBy { it.zone }.map { it to zone(it.zone) }
        val known = placed.mapNotNull { (city, zone) -> zone?.let { Triple(city, it.rules.getOffset(at).totalSeconds, nameOf(city)) } }
            .sortedWith(compareBy<Triple<WorldCity, Int, String>> { it.second }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.third }.thenBy { it.first.zone })
        return known.map { it.first } + placed.filter { it.second == null }.map { it.first }
    }

    // ---- the search ----

    private val MARKS = Regex("\\p{M}+")
    private val NOT_LETTERS = Regex("[^\\p{L}\\p{N}]+")

    /**
     * What the search compares: lower case, without accents, and anything but letters and digits as
     * one space, so that "São Paulo", "sao_paulo" and "SAO PAULO" are one, and "St. Louis" is "st louis".
     */
    fun key(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT).replace(NOT_LETTERS, " ").trim()

    /**
     * Whether a zone is offered by the search: the places ("Asia/Tokyo"), and UTC and GMT. Not the old
     * short names a device also knows ("EST5EDT", "Japan"), and not "Etc/GMT+5", which is five hours
     * behind Greenwich: the opposite of what it seems to say.
     */
    fun searchable(zoneId: String): Boolean =
        zoneId == "UTC" || zoneId == "GMT" || (zoneId.contains('/') && !zoneId.startsWith("Etc/") && !zoneId.startsWith("SystemV/"))

    /**
     * One result. [region]: what tells it from another result of the same name ("San Juan
     * (Argentina)"), else null. [ahead]: seconds ahead of the device's time at this moment. [added]:
     * the list has its zone already. [named]: found as a city of the table rather than as a zone.
     */
    data class Hit(val zone: String, val name: String, val region: String?, val ahead: Int, val added: Boolean, val named: Boolean) {
        /** What it is called in the list once added: "Munich" for a city of the table; nothing for a zone, which is called by its own city anyway. */
        val nameInList: String get() = if (named) name else ""
    }

    /** What stands beside a result: [added] for one the list has, else its difference to the device's time, [sameTime] for none. */
    fun beside(hit: Hit, added: String, sameTime: String): String = when { hit.added -> added; hit.ahead == 0 -> sameTime; else -> offset(hit.ahead) }

    /** The result Enter takes: the first that isn't in the list already. */
    fun firstFree(hits: List<Hit>): Hit? = hits.firstOrNull { !it.added }

    /**
     * Everything the search can find on a device that knows the zones [available]: those zones by
     * their city, and the [cities] of the table whose zone it knows. Made once (a few hundred short
     * strings), then every keystroke is a pass over it.
     */
    class Index(available: Collection<String>, cities: List<WorldClockCities.City> = WorldClockCities.all) {
        private class Entry(val name: String, val zone: String, val named: Boolean, val region: String) {
            val key = key(name)
            /** A zone is found by its id too ("europe", "america/new"); a city of the table by its name alone. */
            val idKey = if (named) "" else key(zone)
        }

        private val entries: List<Entry> = available.toHashSet().let { known ->
            known.filter { searchable(it) }.map { Entry(cityOf(it), it, false, regionOf(it)) } +
                cities.filter { it.zone in known }.map { Entry(it.name, it.zone, true, cityOf(it.zone)) }
        }.sortedWith(compareBy<Entry> { it.key }.thenBy { it.zone })

        /** Names that begin with the text, then names that contain it, then zones whose id does. */
        private fun rank(e: Entry, q: String): Int? = when {
            e.key.startsWith(q) -> 0
            e.key.contains(q) -> 1
            e.idKey.contains(q) -> 2
            else -> null
        }

        /**
         * At most [limit] results for what was typed, from [MIN_LETTERS] letters on: each group of
         * [rank] by name. Two results with one name get their [Hit.region], unless they are one place
         * under two zone names (Asia/Istanbul and Europe/Istanbul), which is listed once. [cities]: the
         * list as it is, for [Hit.added]; [local] and [now]: for the difference beside each result.
         */
        fun search(text: String, cities: List<WorldCity>, local: ZoneId, now: Long, limit: Int = RESULTS): List<Hit> {
            val q = key(cut(text, 64))
            if (q.length < MIN_LETTERS) return emptyList()
            val listed = cities.mapTo(HashSet()) { it.zone }
            class Pick(var entry: Entry, val zone: ZoneId)
            val picked = ArrayList<Pick>()
            // The entries are in the order of their names, and sorting by rank keeps that order within a rank.
            for ((_, e) in entries.mapNotNull { e -> rank(e, q)?.let { it to e } }.sortedBy { it.first }) {
                val zone = zone(e.zone) ?: continue
                val twin = picked.firstOrNull { it.entry.key == e.key && it.zone.rules == zone.rules }
                if (twin != null) {
                    // If the list has the place under one of its two names, that is the one to show, as added.
                    if (e.zone in listed && twin.entry.zone !in listed) twin.entry = e
                    continue
                }
                if (picked.size >= limit) break
                picked += Pick(e, zone)
            }
            val at = Instant.ofEpochMilli(now)
            val here = local.rules.getOffset(at).totalSeconds
            return picked.map { p ->
                val twice = picked.count { it.entry.key == p.entry.key } > 1
                Hit(p.entry.zone, p.entry.name, p.entry.region.takeIf { twice && it.isNotEmpty() }, p.zone.rules.getOffset(at).totalSeconds - here,
                    added = p.entry.zone in listed, named = p.entry.named)
            }
        }
    }
}
