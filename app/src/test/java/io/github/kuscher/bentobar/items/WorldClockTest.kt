package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.BarConfig
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.data.WorldCity
import io.github.kuscher.bentobar.data.keepingLocal
import io.github.kuscher.bentobar.items.WorldClock.Clock
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A text of the app as its resource file has it (`\'` read as an apostrophe), so a test speaks the app's own words. */
internal fun appText(name: String): String {
    val entry = Regex("""<string name="$name"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    val files = File("src/main/res/values").listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty()
    val found = files.mapNotNull { entry.find(it.readText())?.groupValues?.get(1) }
    assertEquals("$name should be in exactly one strings file", 1, found.size)
    return found.single().replace("\\'", "'")
}

/** The words of World clock as the app has them, with times and dates as a US device writes them. */
internal fun worldClockWords(h24: Boolean = false, time: ((Long, ZoneId) -> String)? = null): WorldClock.Words {
    fun format(pattern: String): (Long, ZoneId) -> String = { ms, zone -> DateTimeFormatter.ofPattern(pattern, Locale.US).format(Instant.ofEpochMilli(ms).atZone(zone)) }
    val here = appText("clock_here")
    return WorldClock.Words(local = appText("clock_local"), sameTime = appText("clock_same_time"), tomorrow = appText("clock_tomorrow"),
        yesterday = appText("clock_yesterday"), here = { here.replace("%1\$s", it) },
        time = time ?: format(if (h24) "H:mm" else "h:mm a"), weekday = format("EEE"), date = format("EEE, MMM d"))
}

/** World clock's rules that need no device: the places and their order, what a row says, the cities, the search. */
class WorldClockTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private val berlin = ZoneId.of("Europe/Berlin")
    private val utc = ZoneId.of("UTC")
    private val words = worldClockWords()
    private val everyZone = WorldClock.Index(ZoneId.getAvailableZoneIds())

    private fun at(local: String, zone: ZoneId): Long = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()
    private fun city(zone: String, name: String = "") = WorldCity(zone, name)
    private fun cities(vararg zones: String) = zones.map { WorldCity(it) }

    /** Monday, October 5, 2026, 2:10 PM in Los Angeles: the moment of the design's drawing. */
    private val monday = at("2026-10-05T14:10", la)
    /** Wednesday, October 7, 2026, 9:00 AM in Los Angeles: the moment planned in both specs. */
    private val wednesday = at("2026-10-07T09:00", la)

    // ---- offsets ----

    @Test fun anOffsetReadsInWholeHoursOrInHoursAndMinutes() {
        assertEquals("+9h", WorldClock.offset(9 * 3600))
        assertEquals("−3h", WorldClock.offset(-3 * 3600))
        assertEquals("+5:30", WorldClock.offset(5 * 3600 + 30 * 60))
        assertEquals("+5:45", WorldClock.offset(5 * 3600 + 45 * 60))
        assertEquals("+12:45", WorldClock.offset(12 * 3600 + 45 * 60))
        assertEquals("−9:30", WorldClock.offset(-(9 * 3600 + 30 * 60)))
        assertEquals("+0:30", WorldClock.offset(30 * 60))
        assertEquals("+16h", WorldClock.offset(16 * 3600))
        // The minus is the real sign, not a hyphen.
        assertEquals('−', WorldClock.offset(-3600).first())
    }

    @Test fun withTheDeviceOnUtcKathmanduKolkataAndTokyoReadRight() {
        val now = at("2026-10-05T09:00", utc)
        val rows = WorldClock.rows(WorldClock.places(utc, emptyList(), cities("Asia/Tokyo", "Asia/Kathmandu", "Asia/Kolkata"), now), now, false, words)
        assertEquals(listOf("Here (UTC)", "Kolkata", "Kathmandu", "Tokyo"), rows.map { it.title })
        assertEquals(listOf("Local", "+5:30", "+5:45", "+9h"), rows.map { it.sub })
        assertEquals(listOf("9:00 AM", "2:30 PM", "2:45 PM", "6:00 PM"), rows.map { it.time })
    }

    // ---- the places and their order ----

    @Test fun theListReadsAsTheDesignDrawsIt() {
        val places = WorldClock.places(la, listOf(Clock(berlin, "MUC")), cities("Asia/Tokyo", "America/New_York", "Asia/Kolkata"), monday)
        val rows = WorldClock.rows(places, monday, false, words)
        assertEquals(listOf("Here (Los Angeles)", "New York", "MUC", "Kolkata", "Tokyo"), rows.map { it.title })
        assertEquals(listOf("2:10 PM", "5:10 PM", "11:10 PM", "2:40 AM", "6:10 AM"), rows.map { it.time })
        assertEquals(listOf("Local", "+3h", "+9h", "Tomorrow · +12:30", "Tomorrow · +16h"), rows.map { it.sub })
        assertEquals(listOf(false, false, true, true, true), rows.map { it.night })
    }

    @Test fun placesRunWestToEastAfterHere() {
        val now = at("2026-10-05T12:00", berlin)
        val places = WorldClock.places(berlin, listOf(Clock(ZoneId.of("Europe/London"))), cities("Asia/Tokyo", "Pacific/Honolulu", "America/New_York"), now)
        assertEquals(listOf("Berlin", "Honolulu", "New York", "London", "Tokyo"), places.map { it.name })
        assertEquals(listOf(true, false, false, false, false), places.map { it.here })
    }

    @Test fun twoPlacesWithOneOffsetBothStayInOrderOfTheirNames() {
        val places = WorldClock.places(la, emptyList(), cities("Europe/Rome", "Europe/Paris", "Europe/Amsterdam"), monday)
        assertEquals(listOf("Los Angeles", "Amsterdam", "Paris", "Rome"), places.map { it.name })
    }

    @Test fun theOrderIsThatOfTheOffsetsAtThisMoment() {
        // Phoenix keeps one time all year: an hour behind Denver in summer, level with it in winter, when the names decide.
        val both = cities("America/Denver", "America/Phoenix")
        assertEquals(listOf("Phoenix", "Denver"), WorldClock.places(utc, emptyList(), both, at("2026-07-01T12:00", utc)).drop(1).map { it.name })
        assertEquals(listOf("Denver", "Phoenix"), WorldClock.places(utc, emptyList(), both, at("2026-12-01T12:00", utc)).drop(1).map { it.name })
    }

    @Test fun aRowIsNamedByTheClocksLabelThenTheCitysNameThenTheZonesCity() {
        fun name(clocks: List<Clock>, cities: List<WorldCity>) = WorldClock.places(la, clocks, cities, monday).single { !it.here }.name
        assertEquals("MUC", name(listOf(Clock(berlin, "MUC")), listOf(city("Europe/Berlin", "Munich"))))
        assertEquals("Munich", name(listOf(Clock(berlin, "")), listOf(city("Europe/Berlin", "Munich"))))
        assertEquals("Munich", name(emptyList(), listOf(city("Europe/Berlin", "Munich"))))
        assertEquals("Berlin", name(listOf(Clock(berlin)), emptyList()))
        assertEquals("Berlin", name(emptyList(), listOf(city("Europe/Berlin"))))
        // Two clocks of one zone are one row; the first label there is names it.
        assertEquals("MUC", name(listOf(Clock(berlin, " "), Clock(berlin, "MUC"), Clock(berlin, "BER")), emptyList()))
        assertEquals("Buenos Aires", name(emptyList(), listOf(city("America/Argentina/Buenos_Aires"))))
    }

    @Test fun aZoneHasOneRowWhoeverAskedForIt() {
        val places = WorldClock.places(la, listOf(Clock(berlin, "MUC"), Clock(berlin, "MUC")), listOf(city("Europe/Berlin", "Munich"), city("Asia/Tokyo")), monday)
        assertEquals(listOf("Los Angeles", "MUC", "Tokyo"), places.map { it.name })
        // The row of a clock keeps the city that was added for its zone, so that the city can be removed again.
        assertEquals(listOf(null, city("Europe/Berlin", "Munich"), city("Asia/Tokyo")), places.map { it.city })
    }

    @Test fun theDevicesOwnZoneIsHereAndIsNamedTheSameWay() {
        // A clock that follows the device, and a city added for where the device is: both are the row "Here".
        val places = WorldClock.places(la, listOf(Clock(la)), listOf(city("America/Los_Angeles", "San Francisco")), monday)
        assertEquals(1, places.size)
        assertTrue(places.single().here)
        assertEquals("Here (San Francisco)", WorldClock.rows(places, monday, false, words).single().title)
        assertEquals("Here (SFO)", WorldClock.rows(WorldClock.places(la, listOf(Clock(la, "SFO")), emptyList(), monday), monday, false, words).single().title)
        assertEquals("Here (Los Angeles)", WorldClock.rows(WorldClock.places(la, emptyList(), emptyList(), monday), monday, false, words).single().title)
    }

    @Test fun aZoneThisDeviceDoesNotKnowIsLeftOut() {
        val pasted = listOf(city("Mars/Olympus_Mons"), city("Asia/Tokyo"), city(""), city("not a zone", "Somewhere"), city("asia/tokyo"))
        val places = WorldClock.places(la, emptyList(), pasted, monday)
        assertEquals(listOf("Los Angeles", "Tokyo"), places.map { it.name })
        assertEquals(2, WorldClock.rows(places, monday, false, words).size)
        assertNull(WorldClock.zone("Mars/Olympus_Mons"))
        assertNull(WorldClock.zone(null))
        assertEquals(ZoneId.of("Asia/Tokyo"), WorldClock.zone("Asia/Tokyo"))
    }

    // ---- what a row says ----

    @Test fun anotherPlaceWithNoDifferenceSaysSameTimeNotLocal() {
        val now = at("2026-10-05T12:00", berlin)
        val rows = WorldClock.rows(WorldClock.places(berlin, emptyList(), cities("Europe/Paris"), now), now, false, words)
        assertEquals(listOf("Local", "Same time"), rows.map { it.sub })
    }

    @Test fun theDayThereIsSaidWhenItIsNotTheDevices() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val morning = at("2026-10-06T08:00", tokyo)
        val rows = WorldClock.rows(WorldClock.places(tokyo, emptyList(), cities("America/Los_Angeles", "Asia/Seoul"), morning), morning, false, words)
        assertEquals(listOf("Local", "Yesterday · −16h", "Same time"), rows.map { it.sub })
    }

    @Test fun twoDaysApartIsSaidAsAWeekday() {
        // The far ends of the date line are 25 hours apart: half past midnight on Wednesday there is Monday night here.
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        val now = at("2026-10-07T00:30", kiritimati)
        val rows = WorldClock.rows(WorldClock.places(kiritimati, emptyList(), cities("Pacific/Pago_Pago"), now), now, false, words)
        assertEquals("Mon · −25h", rows[1].sub)
        assertEquals("11:30 PM", rows[1].time)
    }

    @Test fun nightIsFromTenInTheEveningUntilSevenInTheMorning() {
        for (hour in listOf(22, 23, 0, 3, 6)) assertTrue("$hour", WorldClock.night(hour))
        for (hour in listOf(7, 8, 12, 18, 21)) assertFalse("$hour", WorldClock.night(hour))
    }

    @Test fun aRowIsSpokenAsNameTimeAndDifference() {
        val rows = WorldClock.rows(WorldClock.places(la, emptyList(), cities("Asia/Tokyo", "America/New_York"), monday), monday, false, words)
        assertEquals("New York, 5:10 PM, +3h", WorldClock.spoken(rows[1], appText("clock_night")))
        assertEquals("Tokyo, 6:10 AM, Tomorrow · +16h, Night there", WorldClock.spoken(rows[2], appText("clock_night")))
    }

    @Test fun timesFollowTheClockTheyAreAskedIn() {
        val rows = WorldClock.rows(WorldClock.places(la, emptyList(), cities("Asia/Tokyo"), monday), monday, false, worldClockWords(h24 = true))
        assertEquals(listOf("14:10", "6:10"), rows.map { it.time })
    }

    // ---- a planned moment ----

    @Test fun whilePlanningEveryRowLeadsWithItsWeekday() {
        val places = WorldClock.places(la, listOf(Clock(berlin, "MUC")), cities("America/New_York", "Asia/Tokyo"), monday)
        val rows = WorldClock.rows(places, wednesday, true, words)
        assertEquals(listOf("Here (Los Angeles)", "New York", "MUC", "Tokyo"), rows.map { it.title })
        assertEquals(listOf("9:00 AM", "12:00 PM", "6:00 PM", "1:00 AM"), rows.map { it.time })
        assertEquals(listOf("Wed · Local", "Wed · +3h", "Wed · +9h", "Thu · +16h"), rows.map { it.sub })
        assertEquals(listOf(false, false, false, true), rows.map { it.night })
    }

    @Test fun theCopiedLineIsTheOneInTheProductSpec() {
        val places = WorldClock.places(la, emptyList(), listOf(city("Asia/Tokyo"), city("America/Los_Angeles", "San Francisco"), city("Europe/Berlin", "Munich")), monday)
        assertEquals("Wed, Oct 7 · 9:00 AM San Francisco · 6:00 PM Munich · Thu 1:00 AM Tokyo", WorldClock.copyLine(places, wednesday, words))
    }

    @Test fun theCopiedLineIsTheOneInTheDesign() {
        val places = WorldClock.places(la, listOf(Clock(berlin, "MUC")), cities("America/New_York", "Asia/Tokyo"), monday)
        assertEquals("Wed, Oct 7 · 9:00 AM Los Angeles · 12:00 PM New York · 6:00 PM MUC · Thu 1:00 AM Tokyo", WorldClock.copyLine(places, wednesday, words))
    }

    @Test fun theCopiedLineHasPlainSpacesWhateverTheSystemPutsBeforeAmAndPm() {
        // Android writes "9:00 AM" with a narrow no-break space; pasted into a mail or a chat that should be a space like any other.
        val narrow = worldClockWords(time = { ms, zone -> DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(Instant.ofEpochMilli(ms).atZone(zone)) })
        val places = WorldClock.places(la, emptyList(), listOf(city("Europe/Berlin", "Munich West")), monday)
        assertEquals("Wed, Oct 7 · 9:00 AM Los Angeles · 6:00 PM Munich West", WorldClock.copyLine(places, wednesday, narrow))
    }

    @Test fun theCopiedLineNamesAWeekdayOnlyWhereTheDateDiffers() {
        // Late evening in Los Angeles: New York, Berlin and Tokyo are all on Thursday already.
        val places = WorldClock.places(la, emptyList(), cities("Pacific/Honolulu", "America/New_York", "Europe/Berlin", "Asia/Tokyo"), monday)
        assertEquals("Wed, Oct 7 · 10:00 PM Los Angeles · 7:00 PM Honolulu · Thu 1:00 AM New York · Thu 7:00 AM Berlin · Thu 2:00 PM Tokyo",
            WorldClock.copyLine(places, at("2026-10-07T22:00", la), words))
        assertEquals("Wed, Oct 7 · 9:00 Los Angeles · 18:00 Berlin", WorldClock.copyLine(WorldClock.places(la, emptyList(), cities("Europe/Berlin"), monday), wednesday, worldClockWords(h24 = true)))
    }

    @Test fun aHalfHourZoneAndFortyFiveMinuteZonesAtAPlannedMoment() {
        val nine = at("2026-10-07T09:00", berlin)
        val places = WorldClock.places(berlin, emptyList(), cities("Asia/Kolkata", "Asia/Kathmandu", "Australia/Eucla", "Pacific/Chatham"), nine)
        val rows = WorldClock.rows(places, nine, true, words)
        assertEquals(listOf("Here (Berlin)", "Kolkata", "Kathmandu", "Eucla", "Chatham"), rows.map { it.title })
        assertEquals(listOf("9:00 AM", "12:30 PM", "12:45 PM", "3:45 PM", "8:45 PM"), rows.map { it.time })
        assertEquals(listOf("Wed · Local", "Wed · +3:30", "Wed · +3:45", "Wed · +6:45", "Wed · +11:45"), rows.map { it.sub })
        assertEquals("Wed, Oct 7 · 9:00 AM Berlin · 12:30 PM Kolkata · 12:45 PM Kathmandu · 3:45 PM Eucla · 8:45 PM Chatham", WorldClock.copyLine(places, nine, words))
    }

    @Test fun aucklandAgainstLosAngelesTheWeekdayDiffers() {
        val auckland = ZoneId.of("Pacific/Auckland")
        val fromLa = WorldClock.rows(WorldClock.places(la, emptyList(), cities("Pacific/Auckland"), monday), wednesday, true, words)
        assertEquals(listOf("9:00 AM", "5:00 AM"), fromLa.map { it.time })
        assertEquals(listOf("Wed · Local", "Thu · +20h"), fromLa.map { it.sub })
        assertTrue(fromLa[1].night)
        val thursday = at("2026-10-08T09:00", auckland)
        val fromAuckland = WorldClock.rows(WorldClock.places(auckland, emptyList(), cities("America/Los_Angeles"), thursday), thursday, true, words)
        assertEquals(listOf("9:00 AM", "1:00 PM"), fromAuckland.map { it.time })
        assertEquals(listOf("Thu · Local", "Wed · −20h"), fromAuckland.map { it.sub })
        assertEquals("Thu, Oct 8 · 9:00 AM Auckland · Wed 1:00 PM Los Angeles", WorldClock.copyLine(WorldClock.places(auckland, emptyList(), cities("America/Los_Angeles"), thursday), thursday, words))
    }

    @Test fun withAClockChangeBeforeTheChosenDayTheOffsetIsThatDays() {
        // Europe's clocks go back on October 25, America's a week later: in between Berlin is 8 hours ahead, not 9.
        val today = at("2026-10-20T09:00", la)
        val places = WorldClock.places(la, emptyList(), cities("Europe/Berlin"), today)
        assertEquals("+9h", WorldClock.rows(places, today, false, words)[1].sub)
        val chosen = WorldClock.rows(places, at("2026-10-27T09:00", la), true, words)
        assertEquals("5:00 PM", chosen[1].time)
        assertEquals("Tue · +8h", chosen[1].sub)
    }

    // ---- the Clock item's rule ----

    @Test fun aClockIsAwayWhileItsTimeDiffersFromTheDevices() {
        assertTrue(WorldClock.away(berlin, la, monday))
        assertFalse(WorldClock.away(la, la, monday))
        // Another zone with the same time is not away: the rule is about the time, not the place.
        assertFalse(WorldClock.away(ZoneId.of("America/Vancouver"), la, monday))
        // Phoenix has Los Angeles's time in summer and is an hour ahead in winter.
        val phoenix = ZoneId.of("America/Phoenix")
        assertFalse(WorldClock.away(phoenix, la, at("2026-07-01T12:00", la)))
        assertTrue(WorldClock.away(phoenix, la, at("2026-12-01T12:00", la)))
    }

    // ---- the cities ----

    @Test fun aCityIsAddedAtTheEndWithItsName() {
        val one = WorldClock.add(emptyList(), "Asia/Tokyo")
        assertEquals(listOf(city("Asia/Tokyo")), one)
        assertEquals(listOf(city("Asia/Tokyo"), city("Europe/Berlin", "Munich")), WorldClock.add(one, "Europe/Berlin", "Munich"))
    }

    @Test fun theSameZoneIsRefusedTwice() {
        val list = listOf(city("Europe/Berlin", "Munich"))
        assertSame(list, WorldClock.add(list, "Europe/Berlin"))
        assertSame(list, WorldClock.add(list, "Europe/Berlin", "Frankfurt"))
        assertTrue(WorldClock.added(list, "Europe/Berlin"))
        assertFalse(WorldClock.added(list, "Europe/Paris"))
    }

    @Test fun theListHoldsEightCities() {
        val eight = cities("Asia/Tokyo", "Asia/Seoul", "Asia/Kolkata", "Europe/Berlin", "Europe/London", "America/New_York", "America/Chicago", "America/Denver")
        assertEquals(8, WorldClock.MAX_CITIES)
        assertFalse(WorldClock.full(eight.take(7)))
        assertTrue(WorldClock.full(eight))
        assertEquals(eight, eight.take(7).let { WorldClock.add(it, "America/Denver") })
        assertSame(eight, WorldClock.add(eight, "Pacific/Auckland"))
    }

    @Test fun aZoneThisDeviceDoesNotKnowIsNotAdded() {
        assertEquals(emptyList<WorldCity>(), WorldClock.add(emptyList(), "Mars/Olympus_Mons"))
        assertEquals(emptyList<WorldCity>(), WorldClock.add(emptyList(), ""))
    }

    @Test fun aCityIsRemovedByItsZone() {
        val list = listOf(city("Asia/Tokyo"), city("Europe/Berlin", "Munich"))
        assertEquals(listOf(city("Asia/Tokyo")), WorldClock.remove(list, "Europe/Berlin"))
        assertEquals(list, WorldClock.remove(list, "Europe/Paris"))
    }

    @Test fun aCityIsRenamedAsItIsTypedUpToThirtyCharacters() {
        val list = listOf(city("Asia/Tokyo"), city("Europe/Berlin", "Munich"))
        assertEquals(listOf(city("Asia/Tokyo", "Head office"), city("Europe/Berlin", "Munich")), WorldClock.rename(list, "Asia/Tokyo", "Head office"))
        // A space typed at the end stays while the next word is on its way; the row shows the name without it.
        assertEquals("New ", WorldClock.rename(list, "Asia/Tokyo", "New ").first().name)
        assertEquals("x".repeat(30), WorldClock.rename(list, "Asia/Tokyo", "x".repeat(40)).first().name)
        // An emoji is two halves in a string: both stay or both go.
        val cut = WorldClock.rename(list, "Asia/Tokyo", "x".repeat(29) + "🎵").first().name
        assertEquals("x".repeat(29), cut)
        // No name is the zone's own city again.
        assertEquals("Tokyo", WorldClock.nameOf(WorldClock.rename(list, "Asia/Tokyo", "  ").first()))
        assertEquals("Munich", WorldClock.nameOf(list[1]))
    }

    @Test fun aNameFromAPastedLayoutIsShownAsOneShortLine() {
        val places = WorldClock.places(la, listOf(Clock(ZoneId.of("Asia/Tokyo"), "  TYO\n")), listOf(city("Europe/Berlin", " Munich\n\toffice  " + "x".repeat(100))), monday)
        assertEquals("TYO", places.single { it.zone.id == "Asia/Tokyo" }.name)
        assertEquals(("Munich office " + "x".repeat(100)).take(30), places.single { it.zone.id == "Europe/Berlin" }.name)
    }

    @Test fun settingsListTheCitiesInTheMenusOrder() {
        val list = listOf(city("Asia/Tokyo"), city("Mars/Olympus_Mons"), city("Europe/Paris"), city("Europe/Berlin", "Munich"), city("America/New_York"), city("Asia/Tokyo", "Again"))
        // West to east, equal offsets by name; one that this device can't place comes last, so that it can still be removed.
        assertEquals(listOf("New York", "Munich", "Paris", "Tokyo", "Olympus Mons"), WorldClock.ordered(list, monday).map { WorldClock.nameOf(it) })
    }

    @Test fun citiesAddedInTheMenuTravelWithACopiedLayout() {
        val cities = WorldClock.add(WorldClock.add(emptyList(), "Asia/Tokyo"), "Europe/Berlin", "Munich")
        val copied = Json { encodeDefaults = true }.encodeToString(BarConfig.serializer(), BarConfig(cities = cities))
        val pasted = Store.parseLayout(copied)!!.keepingLocal(BarConfig())
        assertEquals(listOf(city("Asia/Tokyo"), city("Europe/Berlin", "Munich")), pasted.cities)
    }

    // ---- the search ----

    /** A handful of zones, so that a list of results is the same whatever zones a machine knows. */
    private val few = WorldClock.Index(listOf("Asia/Tokyo", "America/Toronto", "Europe/Stockholm", "Africa/Porto-Novo", "Europe/Berlin", "UTC", "Etc/UTC", "Etc/GMT+5", "EST5EDT", "Japan"), emptyList())

    private fun names(hits: List<WorldClock.Hit>) = hits.map { if (it.region != null) "${it.name} (${it.region})" else it.name }

    @Test fun tokListsTokyoAtOnce() {
        val hit = everyZone.search("tok", emptyList(), la, monday).first()
        assertEquals("Asia/Tokyo", hit.zone)
        assertEquals("Tokyo", hit.name)
        assertEquals("", hit.nameInList)
        assertFalse(hit.added)
        assertEquals("+16h", WorldClock.offset(hit.ahead))
    }

    @Test fun resultsComeFromTwoLetters() {
        assertEquals(emptyList<WorldClock.Hit>(), everyZone.search("", emptyList(), la, monday))
        assertEquals(emptyList<WorldClock.Hit>(), everyZone.search("t", emptyList(), la, monday))
        assertEquals(emptyList<WorldClock.Hit>(), everyZone.search(" t. ", emptyList(), la, monday))
        assertTrue(everyZone.search("to", emptyList(), la, monday).isNotEmpty())
        assertEquals(2, WorldClock.MIN_LETTERS)
    }

    @Test fun atMostSixResultsShow() {
        assertEquals(6, WorldClock.RESULTS)
        assertEquals(6, everyZone.search("an", emptyList(), la, monday).size)
        assertEquals(3, everyZone.search("an", emptyList(), la, monday, limit = 3).size)
    }

    @Test fun namesThatBeginWithTheTextComeFirstThenNamesThatContainItEachByName() {
        assertEquals(listOf("Tokyo", "Toronto", "Porto-Novo", "Stockholm"), names(few.search("to", emptyList(), la, monday)))
        assertEquals(listOf("Stockholm"), names(few.search("STOCK", emptyList(), la, monday)))
        assertEquals(emptyList<String>(), names(few.search("zz", emptyList(), la, monday)))
    }

    @Test fun aTimeZoneCanBeTypedToo() {
        assertEquals(listOf("Berlin", "Stockholm"), names(few.search("europe", emptyList(), la, monday)))
        assertEquals(listOf("Stockholm"), names(few.search("Europe/St", emptyList(), la, monday)))
        assertEquals(listOf("UTC"), few.search("utc", emptyList(), la, monday).map { it.zone })
    }

    @Test fun onlyPlacesAndUtcAreOffered() {
        // Not the old short names (EST5EDT, Japan), and not Etc/GMT+5, whose sign means the opposite of what it says.
        assertEquals(emptyList<String>(), names(few.search("gmt", emptyList(), la, monday)))
        assertEquals(emptyList<String>(), names(few.search("est", emptyList(), la, monday)))
        assertEquals(emptyList<String>(), names(few.search("japan", emptyList(), la, monday)))
        assertEquals(listOf("UTC"), names(few.search("ut", emptyList(), la, monday)))
        assertEquals(listOf("GMT"), everyZone.search("gmt", emptyList(), la, monday).map { it.zone })
    }

    @Test fun accentsCapitalsAndPunctuationMakeNoDifference() {
        assertEquals("America/Sao_Paulo", everyZone.search("São Paulo", emptyList(), la, monday).first().zone)
        assertEquals("America/Port-au-Prince", everyZone.search("port au", emptyList(), la, monday).first().zone)
        assertEquals("America/New_York", everyZone.search("  new   york ", emptyList(), la, monday).first().zone)
        assertEquals("America/New_York", everyZone.search("NEW_YORK", emptyList(), la, monday).first().zone)
        for (typed in listOf("st louis", "St. Louis", "ST.LOUIS")) assertEquals("St. Louis", everyZone.search(typed, emptyList(), la, monday).first().name)
        assertEquals("sao paulo", WorldClock.key(" São  Paulo! "))
    }

    @Test fun oneAlreadyInTheListIsMarkedAndEnterTakesTheFirstThatIsNot() {
        val hits = few.search("to", cities("Asia/Tokyo"), la, monday)
        assertEquals(listOf(true, false, false, false), hits.map { it.added })
        assertEquals("Toronto", WorldClock.firstFree(hits)!!.name)
        assertNull(WorldClock.firstFree(few.search("tok", cities("Asia/Tokyo"), la, monday)))
        assertNull(WorldClock.firstFree(emptyList()))
    }

    @Test fun theResultsReadAsTheDesignDrawsThem() {
        val hits = everyZone.search("to", cities("Asia/Tokyo"), la, monday, limit = 50).associateBy { it.name }
        fun beside(hit: WorldClock.Hit) = WorldClock.beside(hit, appText("clock_search_added"), appText("clock_same_time"))
        assertEquals("added", beside(hits.getValue("Tokyo")))
        assertEquals("+3h", beside(hits.getValue("Toronto")))
        assertEquals("+3h", beside(hits.getValue("Tortola")))
        assertEquals("+20h", beside(hits.getValue("Tongatapu")))
        // The device's own place has no difference.
        assertEquals("Same time", beside(everyZone.search("los angeles", emptyList(), la, monday).first()))
    }

    @Test fun twoResultsWithOneNameGetTheirRegion() {
        assertEquals(setOf("San Juan (Argentina)", "San Juan (Puerto Rico)"), names(everyZone.search("san juan", emptyList(), la, monday)).toSet())
        assertEquals(listOf("Pacific (Canada)", "Pacific (US)"), names(everyZone.search("pacific", emptyList(), la, monday)).take(2))
        assertEquals("%1\$s (%2\$s)", appText("clock_search_region"))
        // One of a kind has none.
        assertNull(everyZone.search("tokyo", emptyList(), la, monday).single().region)
        assertEquals("Argentina", WorldClock.regionOf("America/Argentina/San_Juan"))
        assertEquals("Europe", WorldClock.regionOf("Europe/Istanbul"))
        assertEquals("", WorldClock.regionOf("UTC"))
    }

    @Test fun onePlaceUnderTwoZoneNamesIsListedOnce() {
        // Asia/Istanbul and Europe/Istanbul are one zone under two names.
        val istanbul = everyZone.search("istanbul", emptyList(), la, monday)
        assertEquals(listOf("Istanbul"), names(istanbul))
        // If the list has it under the other name, that is the one found, and it reads "added".
        for (id in listOf("Asia/Istanbul", "Europe/Istanbul")) {
            val hit = everyZone.search("istanbul", cities(id), la, monday).single()
            assertEquals(id, hit.zone)
            assertTrue(hit.added)
        }
        assertEquals(listOf("Buenos Aires"), names(everyZone.search("buenos", emptyList(), la, monday)))
    }

    // ---- cities that are no zone's name ----

    @Test fun munichFindsBerlinsZoneAndIsAddedAsMunich() {
        val hit = everyZone.search("Munich", emptyList(), la, monday).single()
        assertEquals("Europe/Berlin", hit.zone)
        assertEquals("Munich", hit.name)
        assertEquals("Munich", hit.nameInList)
        val added = WorldClock.add(emptyList(), hit.zone, hit.nameInList)
        assertEquals(listOf(city("Europe/Berlin", "Munich")), added)
        assertEquals("Munich", WorldClock.places(la, emptyList(), added, monday).last().name)
        // Its zone can't be added twice: Berlin and Munich both read "added" now.
        assertTrue(everyZone.search("munich", added, la, monday).single().added)
        assertTrue(everyZone.search("berlin", added, la, monday).first().added)
    }

    @Test fun sanFranciscoAndBengaluruAreFoundToo() {
        assertEquals("America/Los_Angeles", everyZone.search("san fr", emptyList(), la, monday).single().zone)
        assertEquals("San Francisco", everyZone.search("francisco", emptyList(), la, monday).single().nameInList)
        assertEquals("Asia/Kolkata", everyZone.search("bengaluru", emptyList(), la, monday).single().zone)
        assertEquals("Asia/Kolkata", everyZone.search("bangal", emptyList(), la, monday).single().zone)
        assertEquals("+12:30", WorldClock.offset(everyZone.search("mumbai", emptyList(), la, monday).single().ahead))
    }

    @Test fun aCityWhoseZoneThisDeviceLacksIsNotOffered() {
        val index = WorldClock.Index(listOf("Asia/Tokyo"), listOf(WorldClockCities.City("Osaka", "Asia/Tokyo"), WorldClockCities.City("Munich", "Europe/Berlin")))
        assertEquals(listOf("Osaka"), names(index.search("os", emptyList(), la, monday)))
        assertEquals(emptyList<String>(), names(index.search("mu", emptyList(), la, monday)))
    }

    @Test fun everyCityOfTheTableIsInAZoneThisMachineKnows() {
        val known = ZoneId.getAvailableZoneIds()
        assertTrue("about 150 cities, not ${WorldClockCities.all.size}", WorldClockCities.all.size in 120..200)
        for (c in WorldClockCities.all) {
            assertTrue("${c.name} is in ${c.zone}, which is no zone", c.zone in known)
            assertTrue("${c.name} should be plain letters, so that it is found as it is typed", c.name.matches(Regex("[A-Za-z]+([ .]+[A-Za-z]+)*")))
        }
        assertEquals("a city is in the table twice", WorldClockCities.all.size, WorldClockCities.all.map { WorldClock.key(it.name) }.toSet().size)
    }

    @Test fun noCityOfTheTableRepeatsWhatItsZoneIsCalledAnyway() {
        // "Tokyo" needs no entry: the zone is found by that name already. A city may share its name with a place in another zone (San Juan).
        val zones = ZoneId.getAvailableZoneIds().filter { WorldClock.searchable(it) }.groupBy { WorldClock.key(WorldClock.cityOf(it)) }
        for (c in WorldClockCities.all) for (id in zones[WorldClock.key(c.name)].orEmpty())
            assertFalse("${c.name} is what $id is called already", ZoneId.of(id).rules == ZoneId.of(c.zone).rules)
    }

    // ---- the words ----

    @Test fun theWordsAreTheCopyDecks() {
        val deck = mapOf(
            "clock_add_city" to "Add a city", "clock_search_label" to "City or time zone", "clock_search_none" to "No city or zone matches",
            "clock_search_added" to "added", "clock_search_region" to "%1\$s (%2\$s)",
            "clock_full" to "The list holds 8 cities. Remove one to add another.", "clock_edit_cities" to "Edit cities",
            "clock_remove_city" to "Remove %1\$s", "clock_same_time" to "Same time", "clock_night" to "Night there",
            "trigger_clock" to "Show when this clock differs from local time", "trigger_clock_short" to "shows when away",
            "clock_cities_section" to "World clock cities", "clock_cities_help" to "One list for the whole bar. Add cities in the World clock menu.",
            "clock_cities_empty" to "No cities added yet. Add one in the World clock menu.", "clock_city_name" to "Name", "clock_city_remove" to "Remove",
            "clock_plan" to "Plan a time", "clock_plan_now" to "Now", "clock_plan_slider" to "Time of day", "clock_plan_state" to "%1\$s, %2\$s",
            "clock_plan_previous_day" to "Previous day", "clock_plan_next_day" to "Next day", "clock_plan_subtitle" to "%1\$s · %2\$s",
            "clock_plan_copy" to "Copy times", "clock_plan_new_event" to "New event at %1\$s",
            // Words of 0.8 that the new rows use as they are.
            "clock_local" to "Local", "clock_tomorrow" to "Tomorrow", "clock_yesterday" to "Yesterday", "clock_here" to "Here (%1\$s)",
            "clock_zone_find_hint" to "Tokyo, London, New York…",
        )
        for ((name, text) in deck) assertEquals(name, text, appText(name))
        // The message names the limit the code has.
        assertTrue(appText("clock_full").contains(" ${WorldClock.MAX_CITIES} "))
    }
}
