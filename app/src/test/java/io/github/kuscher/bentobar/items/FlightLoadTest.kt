package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.Kept
import io.github.kuscher.bentobar.data.Online
import io.github.kuscher.bentobar.data.Online.Service.AIRLABS
import io.github.kuscher.bentobar.items.AirLabs.Failure
import io.github.kuscher.bentobar.items.FlightLoad.Outcome
import io.github.kuscher.bentobar.items.FlightRules.Tracked
import io.github.kuscher.bentobar.net.FakeHttp
import io.github.kuscher.bentobar.net.Host
import io.github.kuscher.bentobar.net.Http
import io.github.kuscher.bentobar.net.Reply
import io.github.kuscher.bentobar.net.Request
import io.github.kuscher.bentobar.net.Transport
import io.github.kuscher.bentobar.net.Why
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor

/**
 * The Flight item's requests and what it keeps: what is sent and when, that nothing is sent without
 * a key and the switch, that the key is in nothing that is kept, and that a restart, a night with the
 * lid closed and a landed flight cost no lookup. The service is a fake; the key is `test-key`.
 */
class FlightLoadTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "bentobar-flight-${System.nanoTime()}")
    private val key = "test-key"
    private val item = "item1"
    private val ids = setOf(item)
    private fun reply(name: String): String = javaClass.getResource("/airlabs/$name.json")!!.readText()
    private fun at(text: String): Instant = Instant.parse(text)
    private fun ms(text: String) = at(text).toEpochMilli()
    /** When the saved replies were asked for. */
    private val asked = ms("2026-10-02T07:29:00Z")
    private val min = 60_000L

    @Before fun fresh() {
        Online.init(dir)
        Online.saveKey(AIRLABS, key)
        FlightLoad.forget(keyGone = false)
    }

    @After fun gone() {
        dir.deleteRecursively()
        FlightLoad.forget(keyGone = false)
        // Leave nothing switched on for whoever runs next.
        Online.init(File(dir, "empty"))
    }

    /** The service as the test says it is right now: it answers by a request's path, and remembers what it was asked. */
    private class Service : Transport {
        val asked = ArrayList<Request>()
        var answer: (Request) -> Reply = { Reply.Failed(Why.STATUS, 404) }
        override fun get(request: Request): Reply { asked += request; return answer(request) }
        fun says(vararg byPath: Pair<String, String>) { val texts = byPath.toMap(); answer = { r -> texts[r.path]?.let { Reply.Ok(it) } ?: Reply.Failed(Why.STATUS, 404) } }
        fun fails(why: Why, status: Int = 0, retryAfterSec: Long? = null) { answer = { Reply.Failed(why, status, retryAfterSec) } }
        val paths: List<String> get() = asked.map { it.path }
    }

    /** Runs [test] with the gate open (the service switched on, its item in the bar, the bar on screen) and this fake in the network's place. */
    private fun <T> online(test: (Service) -> T): T = FakeHttp.use { Service().let { Http.transport = it; test(it) } }

    private fun question(text: String, day: LocalDate? = null) = FlightLoad.question(item, text, day)!!
    private fun found(o: Outcome): Tracked = (o as Outcome.Found).tracked
    private fun failed(o: Outcome): Failure = (o as Outcome.Failed).failure
    private fun sent() = Http.sent(Host.AIRLABS)

    /** A reply as it really comes: with the object that repeats the request, the key and the caller's address in it. */
    private fun withRequest(reply: String, left: Int = 940): String = reply.replaceFirst("{", """{"request":{"lang":"en","currency":"USD","id":"abc","host":"airlabs.co",
        "key":{"id":7,"api_key":"$key","type":"free","limits_by_hour":2500,"limits_by_minute":250,"limits_by_month":1000,"limits_total":$left},
        "params":{"flight_iata":"LH455","api_key":"$key"},"client":{"ip":"203.0.113.7","geo":{"city":"San Francisco"},"connection":{"isp_name":"Example Net"}}},""")

    /** Everything under the test's directory that holds [text], by file name. */
    private fun filesWith(text: String) = dir.walkTopDown().filter { it.isFile && it.readText().contains(text) }.map { it.name }.toList()

    /** Tracks LH 455 as it was in the air on 2 October, and takes the answer. */
    private fun follow(net: Service, now: Long = asked): Tracked {
        net.says(AirLabs.FLIGHT to withRequest(reply("flight-LH455-in-the-air")))
        return found(FlightLoad.track(question("lh455"), now)).also { assertTrue(FlightLoad.take(item, it, now)) }
    }

    // ---- nothing without a key and the switch

    @Test fun withoutAKeyNothingIsSent() {
        Online.removeKey(AIRLABS)
        online { net ->
            net.says(AirLabs.FLIGHT to reply("flight-LH455-in-the-air"))
            assertEquals(Outcome.Unasked, FlightLoad.track(question("LH455"), asked))
            assertNull(FlightLoad.load(item, null, asked, ids))
            assertEquals(emptyList<Request>(), net.asked)
            assertEquals(0, sent())
        }
    }

    @Test fun withTheSwitchOffNothingIsSentThoughTheKeyIsThere() {
        Online.turnOff(AIRLABS)
        assertTrue(Online.hasKey(AIRLABS))
        online { net ->
            net.says(AirLabs.FLIGHT to reply("flight-LH455-in-the-air"))
            assertEquals(Outcome.Unasked, FlightLoad.track(question("LH455"), asked))
            assertNull(FlightLoad.load(item, null, asked, ids))
            assertEquals(emptyList<Request>(), net.asked)
            assertEquals(0, sent())
        }
    }

    @Test fun whatIsNotAFlightNumberIsNeverAsked() {
        online { net ->
            for (text in listOf("hello", "", "LH", "455", "LH 455 tomorrow")) assertNull(text, FlightLoad.question(item, text, null))
            assertEquals("LH 455", FlightLoad.question(item, "lh455", null)!!.number.shown)
            assertEquals(0, net.asked.size)
            assertEquals(0, sent())
        }
    }

    // ---- what is sent

    @Test fun aPressOfTrackSendsTheNumberAndTheKeyToOneHostAndNothingElse() {
        online { net ->
            net.says(AirLabs.FLIGHT to withRequest(reply("flight-LH455-in-the-air")))
            val t = found(FlightLoad.track(question("lh 455"), asked))
            val r = net.asked.single()
            assertEquals(Host.AIRLABS, r.host)
            assertEquals("/api/v9/flight", r.path)
            assertEquals(listOf("flight_iata" to "LH455", "api_key" to key), r.query)
            assertEquals(1, sent())
            assertEquals(0, Http.sent(Host.OPEN_METEO) + Http.sent(Host.OPEN_METEO_GEOCODING))
            // What came back: the flight, the day it leaves, and how many lookups are left.
            assertEquals("LH455", t.number)
            assertEquals("2026-10-01", t.day)
            assertEquals(FlightState.IN_AIR, t.flight!!.state)
            assertEquals(940, t.left)
            assertEquals(940, FlightLoad.left)
            assertEquals(asked, t.askedAt); assertEquals(asked, t.heardAt)
        }
    }

    @Test fun aCallsignIsAskedForAsOneAndADayTakesTheTimetable() {
        online { net ->
            net.says(AirLabs.FLIGHT to reply("flight-LH455-in-the-air"), AirLabs.ROUTES to reply("routes-LH455"))
            val t = found(FlightLoad.track(question("dlh455", LocalDate.of(2026, 10, 3)), asked))
            assertEquals(listOf("/api/v9/flight", "/api/v9/routes"), net.paths)
            // The callsign for the one flight; for the timetable the ticket's number, which the first reply named.
            assertEquals(listOf("flight_icao" to "DLH455", "api_key" to key), net.asked[0].query)
            assertEquals(listOf("flight_iata" to "LH455", "api_key" to key), net.asked[1].query)
            assertEquals("DLH455", t.number)
            assertEquals("2026-10-03", t.day)
            assertTrue(t.flight!!.timetable)
            assertEquals(2, sent())
        }
    }

    @Test fun aDayChipIsTheDevicesDayAndWhatIsKeptIsTheFlightsOwn() {
        online { net ->
            net.says(AirLabs.FLIGHT to reply("flight-LH454-planned"))
            // The evening of 1 October in Honolulu. The flight leaves Frankfurt in an hour, on the 2nd there.
            val q = FlightLoad.question(item, "LH454", LocalDate.of(2026, 10, 1), ZoneId.of("Pacific/Honolulu"))!!
            val t = found(FlightLoad.track(q, asked))
            assertEquals(1, sent())
            assertEquals("2026-10-02", t.day)
        }
    }

    // ---- the key

    @Test fun whatIsKeptCannotContainTheKey() {
        online { net ->
            val t = follow(net)
            // The model itself, written out, and everything on the disk: the key is in its own file and nowhere else.
            assertFalse(Json.encodeToString(Tracked.serializer(), t).contains(key))
            assertFalse(t.toString().contains(key))
            assertEquals(listOf("online.json"), filesWith(key))
            // Nor is anything else of the object that repeats the request: the caller's address, its provider.
            assertEquals(emptyList<String>(), filesWith("203.0.113.7"))
            assertEquals(emptyList<String>(), filesWith("Example Net"))
            assertEquals(emptyList<String>(), filesWith("api_key"))
            // The same after it was asked about again, after a refused key, and after the service moved on.
            net.says(AirLabs.FLIGHT to withRequest(reply("flight-LH455-landed"), left = 939))
            val landed = FlightLoad.load(item, t, asked + 30 * min, ids)!!
            assertEquals(FlightState.LANDED, landed.flight!!.state)
            assertEquals(939, landed.left)
            net.says(AirLabs.FLIGHT to withRequest(reply("error-unknown-key")))
            assertEquals(Failure.REFUSED, FlightLoad.load(item, landed, asked + 60 * min, ids)!!.failure)
            net.says(AirLabs.FLIGHT to withRequest(reply("flight-LH454-planned")))
            assertTrue(FlightLoad.load(item, landed, asked + 90 * min, ids)!!.ended)
            assertEquals(listOf("online.json"), filesWith(key))
            assertEquals(emptyList<String>(), filesWith("203.0.113.7"))
            // And what is kept is there, and reads as the flight.
            assertTrue(Kept.fetched(AIRLABS).read(item)!!.text.contains("\"LH455\""))
        }
    }

    @Test fun aNewKeyStartsWithNoCountOfLookupsAlsoInWhatIsKept() {
        online { net ->
            // The old key had twelve lookups left: too few to ask unasked, so the flight was left alone.
            net.says(AirLabs.FLIGHT to withRequest(reply("flight-LH455-in-the-air"), left = 12))
            val t = found(FlightLoad.track(question("lh455"), asked)).also { assertTrue(FlightLoad.take(item, it, asked)) }
            assertEquals(12, t.left)
            assertNull(FlightRules.every(t, at("2026-10-02T07:35:00Z")))
            // Replace key.
            Online.saveKey(AIRLABS, "another-test-key")
            FlightLoad.newKey()
            assertNull(FlightLoad.left)
            // What is kept says nothing of the old key's count any more, and is as old as it was: the flight has its turn again,
            // now and after a restart, and no menu says "few lookups left" of a key with a thousand.
            val kept = FlightLoad.kept(item, asked + 5 * min, ids)!!
            assertEquals(t.copy(left = null), kept.value)
            assertEquals(5 * min, kept.ageMs)
            assertNull(FlightLoad.left)
            assertEquals(30 * min, FlightRules.every(kept.value, at("2026-10-02T07:35:00Z")))
            // Nobody was asked for any of this.
            assertEquals(1, sent())
        }
    }

    @Test fun nothingThatPrintsAValueSaysWhoseFlightItIs() {
        online { net ->
            val t = follow(net)
            assertEquals("Tracked(a flight)", t.toString())
            assertEquals("Tracked(nothing)", Tracked().toString())
            assertEquals("Question(a flight)", question("LH455").toString())
            assertEquals("airlabs.co/api/v9/flight", net.asked.single().toString())
        }
    }

    // ---- a number that was not found

    @Test fun aNumberThatWasNotFoundIsRememberedForAnHour() {
        online { net ->
            net.says(AirLabs.FLIGHT to reply("error-not-found"), AirLabs.ROUTES to """{"response":[]}""")
            assertEquals(Failure.NOT_FOUND, failed(FlightLoad.track(question("LH9999"), asked)))
            assertEquals(2, sent())                                   // finding out took two lookups
            // A second try within the hour sends nothing.
            assertEquals(Failure.NOT_FOUND, failed(FlightLoad.track(question("lh 9999"), asked + 5 * min)))
            assertEquals(Failure.NOT_FOUND, failed(FlightLoad.track(question("LH9999"), asked + 59 * min)))
            assertEquals(2, sent())
            // Another number is asked for, and so is the same one on a day of its own.
            FlightLoad.track(question("LH9998"), asked + 5 * min)
            assertEquals(4, sent())
            FlightLoad.track(question("LH9999", LocalDate.of(2026, 10, 3)), asked + 5 * min)
            assertEquals(6, sent())
            // After the hour it is asked for again.
            FlightLoad.track(question("LH9999"), asked + 60 * min)
            assertEquals(8, sent())
        }
    }

    @Test fun aDayTheNumberDoesNotFlyOnIsRememberedTooButWhatMayPassIsNot() {
        online { net ->
            net.says(AirLabs.FLIGHT to reply("flight-LH455-in-the-air"), AirLabs.ROUTES to reply("routes-LH455").replace(Regex("\"days\": \\[[^]]*]"), "\"days\": [\"mon\"]"))
            val saturday = LocalDate.of(2026, 10, 3)
            assertEquals(Failure.NOT_THAT_DAY, failed(FlightLoad.track(question("LH455", saturday), asked)))
            assertEquals(2, sent())
            assertEquals(Failure.NOT_THAT_DAY, failed(FlightLoad.track(question("LH455", saturday), asked + 10 * min)))
            assertEquals(2, sent())
            // No connection: asked again as soon as the user asks again.
            net.fails(Why.OFFLINE)
            assertEquals(Failure.OFFLINE, failed(FlightLoad.track(question("LH454"), asked)))
            assertEquals(Failure.OFFLINE, failed(FlightLoad.track(question("LH454"), asked + 1_000)))
            assertEquals(4, sent())
            // A wrong key costs one request each time it is tried, and nothing more is sent for it.
            net.says(AirLabs.FLIGHT to reply("error-unknown-key"))
            assertEquals(Failure.REFUSED, failed(FlightLoad.track(question("LH454"), asked)))
            assertEquals(5, sent())
            assertEquals(listOf("/api/v9/flight"), net.paths.takeLast(1))
        }
    }

    // ---- what is kept

    @Test fun takingAnAnswerKeepsTheFlightOutsideTheLayoutAndARestartCostsNoLookup() {
        online { net ->
            val t = follow(net)
            assertEquals(setOf(item), Kept.own("flight").names())
            assertEquals(setOf(item), Kept.fetched(AIRLABS).names())
            // A restart: what was kept comes back, as old as it is, and nobody is asked.
            val kept = FlightLoad.kept(item, asked + 5 * min, ids)!!
            assertEquals(t, kept.value)
            assertEquals(5 * min, kept.ageMs)
            assertEquals(1, sent())
            // A clock that was set back makes nothing younger than new.
            assertEquals(0L, FlightLoad.kept(item, asked - 5 * min, ids)!!.ageMs)
            // An item that follows nothing has nothing kept.
            assertNull(FlightLoad.kept("item2", asked, setOf(item, "item2")))
        }
    }

    @Test fun anAnswerThatNobodyTakesChangesNothing() {
        online { net ->
            follow(net)
            // Track another flight: until its answer is taken, the item follows the first one.
            net.says(AirLabs.FLIGHT to reply("flight-LH454-planned"))
            val other = found(FlightLoad.track(question("LH454"), asked + min))
            assertEquals("LH455", FlightLoad.kept(item, asked + min, ids)!!.value.number)
            assertTrue(FlightLoad.take(item, other, asked + min))
            assertEquals("LH454", FlightLoad.kept(item, asked + min, ids)!!.value.number)
        }
    }

    @Test fun aLoadThatComesBackForAFlightTheItemNoLongerFollowsIsDropped() {
        online { net ->
            val old = follow(net)
            net.says(AirLabs.FLIGHT to reply("flight-LH454-planned"))
            val other = found(FlightLoad.track(question("LH454"), asked + min))
            // The refresh of the first flight is on its way while the second is taken: it must not put the first one back.
            net.answer = { FlightLoad.take(item, other, asked + min); Reply.Ok(reply("flight-LH455-landed")) }
            assertNull(FlightLoad.load(item, old, asked + min, ids))
            assertEquals("LH454", FlightLoad.kept(item, asked + min, ids)!!.value.number)
            // The same when tracking stopped under it.
            net.answer = { FlightLoad.stop(item); Reply.Ok(reply("flight-LH454-planned")) }
            assertNull(FlightLoad.load(item, other, asked + 2 * min, ids))
            assertEquals(emptySet<String>(), Kept.fetched(AIRLABS).names())
        }
    }

    @Test fun stopTrackingDeletesTheAnswerAndTheNote() {
        online { net ->
            follow(net)
            FlightLoad.stop(item)
            assertEquals(emptySet<String>(), Kept.own("flight").names())
            assertEquals(emptySet<String>(), Kept.fetched(AIRLABS).names())
            assertNull(FlightLoad.kept(item, asked, ids))
            // What the item holds from then on is "nothing followed", found out without a request.
            assertEquals(Tracked(), FlightLoad.load(item, null, asked, ids))
            assertEquals(1, sent())
        }
    }

    @Test fun removingTheKeyDeletesTheKeyTheFlightAndTheAnswer() {
        online { net ->
            follow(net)
            Online.removeKey(AIRLABS)
            FlightLoad.forget(keyGone = true)                          // what the item does when it is told
            assertEquals(emptySet<String>(), Kept.own("flight").names())
            assertEquals(emptySet<String>(), Kept.fetched(AIRLABS).names())
            assertEquals(emptyList<String>(), filesWith(key))
            assertNull(FlightLoad.left)
            assertNull(FlightLoad.load(item, null, asked, ids))
            assertEquals(1, sent())
        }
    }

    @Test fun switchingTheServiceOffDeletesTheAnswerAndKeepsWhatIsFollowedWhichIsLookedUpAfresh() {
        online { net ->
            follow(net)
            Online.turnOff(AIRLABS)
            FlightLoad.forget(keyGone = false)
            assertEquals(emptySet<String>(), Kept.fetched(AIRLABS).names())
            assertEquals(setOf(item), Kept.own("flight").names())
            // Off: nothing is asked.
            assertNull(FlightLoad.load(item, null, asked + min, ids))
            assertEquals(1, sent())
            // On again: the same number on the same day, with one request (it is still that day's flight).
            assertTrue(Online.turnOn(AIRLABS))
            assertNull(FlightLoad.kept(item, asked + 2 * min, ids))
            val again = FlightLoad.load(item, null, asked + 2 * min, ids)!!
            assertEquals("LH455", again.number); assertEquals("2026-10-01", again.day)
            assertEquals(FlightState.IN_AIR, again.flight!!.state)
            assertEquals(2, sent())
            assertEquals(listOf("flight_iata" to "LH455", "api_key" to key), net.asked.last().query)
            assertEquals(setOf(item), Kept.fetched(AIRLABS).names())
        }
    }

    @Test fun anAnswerThatArrivesAfterTheSwitchWentOffIsNotKept() {
        online { net ->
            val t = follow(net)
            // The switch goes off while the request is on its way.
            net.answer = { Online.turnOff(AIRLABS); Reply.Ok(withRequest(reply("flight-LH455-landed"))) }
            FlightLoad.load(item, t, asked + 30 * min, ids)
            assertEquals(emptySet<String>(), Kept.fetched(AIRLABS).names())
            assertEquals(listOf("online.json"), filesWith(key))
        }
    }

    @Test fun aFlightLookedUpAfreshWithoutAConnectionIsTriedAgain() {
        online { net ->
            follow(net)
            Online.turnOff(AIRLABS); Online.turnOn(AIRLABS)
            net.fails(Why.OFFLINE)
            val first = FlightLoad.load(item, null, asked + min, ids)!!
            assertEquals("LH455", first.number)
            assertNull(first.flight)
            assertEquals(Failure.OFFLINE, first.failure); assertEquals(1, first.failures)
            assertEquals(2 * min, FlightRules.every(first, at("2026-10-02T07:30:00Z")))
            val second = FlightLoad.load(item, first, asked + 3 * min, ids)!!
            assertEquals(2, second.failures)
            // What is kept of it reads back, so a restart knows what it was waiting for.
            assertEquals(second, FlightLoad.kept(item, asked + 3 * min, ids)!!.value)
        }
    }

    @Test fun whatBelongedToAnItemThatIsGoneGoesWithIt() {
        online { net ->
            val t = follow(net)
            assertTrue(FlightLoad.take("item2", t, asked))
            assertEquals(setOf(item, "item2"), Kept.own("flight").names())
            // The layout has only the first item now.
            FlightLoad.kept(item, asked, ids)
            assertEquals(setOf(item), Kept.own("flight").names())
            assertEquals(setOf(item), Kept.fetched(AIRLABS).names())
            // With no Flight item left at all nothing is cleared yet: deleting the last one can still be undone.
            FlightLoad.kept(item, asked, emptySet())
            assertEquals(setOf(item), Kept.own("flight").names())
        }
    }

    @Test fun whatWasKeptForTheLastFlightItemGoesOnceItsDeletionCanNoLongerBeUndone() {
        online { net ->
            follow(net)
            assertEquals(setOf(item), Kept.own("flight").names())
            assertEquals(setOf(item), Kept.fetched(AIRLABS).names())
            // The moment to undo has passed (longer than the Undo's own ten seconds), and the item is back: nothing is touched.
            assertTrue(FlightLoad.UNDO_MS >= 30_000)
            assertFalse(FlightLoad.clearWithout(ids))
            assertEquals(setOf(item), Kept.own("flight").names())
            assertEquals(setOf(item), Kept.fetched(AIRLABS).names())
            // It has passed and there is still no Flight item: its number, its day and its last answer go, and nothing of a flight stays for good.
            assertTrue(FlightLoad.clearWithout(emptySet()))
            assertEquals(emptySet<String>(), Kept.own("flight").names())
            assertEquals(emptySet<String>(), Kept.fetched(AIRLABS).names())
            assertEquals(emptyList<String>(), filesWith("LH455"))
            // The key is not a flight's: it stays where it is.
            assertEquals(listOf("online.json"), filesWith(key))
            assertEquals(1, sent())
        }
    }

    @Test fun whatCannotBeReadIsNotKept() {
        online { net ->
            val t = follow(net)
            Kept.fetched(AIRLABS).write(item, "not what was written here", asked)
            assertNull(FlightLoad.kept(item, asked, ids))
            assertEquals(emptySet<String>(), Kept.fetched(AIRLABS).names())
            // An answer for another flight than the one the item follows is not its answer.
            Kept.fetched(AIRLABS).write(item, Json.encodeToString(Tracked.serializer(), t.copy(number = "LH454")), asked)
            assertNull(FlightLoad.kept(item, asked, ids))
            // A flight with a clock nobody has is no flight.
            val f = t.flight!!
            Kept.fetched(AIRLABS).write(item, Json.encodeToString(Tracked.serializer(), t.copy(flight = f.copy(from = f.from.copy(offset = 99_999)))), asked)
            assertNull(FlightLoad.kept(item, asked, ids))
            assertEquals(emptySet<String>(), Kept.fetched(AIRLABS).names())
        }
    }

    // ---- asking again

    @Test fun askingAgainIsOneRequestAboutTheSameFlight() {
        online { net ->
            val t = follow(net)
            net.says(AirLabs.FLIGHT to reply("flight-LH455-landed"))
            val next = FlightLoad.load(item, t, asked + 30 * min, ids)!!
            assertEquals(listOf("/api/v9/flight", "/api/v9/flight"), net.paths)
            assertEquals(listOf("flight_iata" to "LH455", "api_key" to key), net.asked.last().query)
            assertEquals(FlightState.LANDED, next.flight!!.state)
            assertNull(next.failure); assertEquals(0, next.failures); assertFalse(next.ended)
            assertEquals(asked + 30 * min, next.askedAt); assertEquals(asked + 30 * min, next.heardAt)
            assertEquals(940, next.left)                               // this reply did not say: what was known stands
            // A landed flight is not asked about again.
            assertNull(FlightRules.every(next, at("2026-10-02T08:30:00Z")))
        }
    }

    @Test fun aFailedAskKeepsTheFlightSaysWhyAndCountsUp() {
        online { net ->
            val t = follow(net)
            net.fails(Why.OFFLINE)
            val first = FlightLoad.load(item, t, asked + 30 * min, ids)!!
            assertEquals(t.flight, first.flight)
            assertEquals(Failure.OFFLINE, first.failure); assertEquals(1, first.failures)
            assertEquals(asked + 30 * min, first.askedAt)
            assertEquals(asked, first.heardAt)                         // "updated" stays when it was last heard of
            net.fails(Why.STATUS, 429, retryAfterSec = 900)
            val second = FlightLoad.load(item, first, asked + 32 * min, ids)!!
            assertEquals(Failure.NO_ANSWER, second.failure); assertEquals(2, second.failures)
            assertEquals(900L, second.waitSec)
            assertEquals(15 * min, FlightRules.every(second, at("2026-10-02T08:01:00Z")))
            // The next answer puts all of it right.
            net.says(AirLabs.FLIGHT to reply("flight-LH455-in-the-air"))
            val third = FlightLoad.load(item, second, asked + 47 * min, ids)!!
            assertNull(third.failure); assertEquals(0, third.failures); assertNull(third.waitSec)
            assertEquals(asked + 47 * min, third.heardAt)
        }
    }

    @Test fun aRefusedOrUsedUpKeyStopsTheAskingUntilTheUserActs() {
        online { net ->
            val t = follow(net)
            net.says(AirLabs.FLIGHT to reply("error-unknown-key"))
            val refused = FlightLoad.load(item, t, asked + 30 * min, ids)!!
            assertEquals(Failure.REFUSED, refused.failure)
            assertEquals(t.flight, refused.flight)
            assertFalse(refused.ended)
            assertNull(FlightRules.every(refused, at("2026-10-02T08:00:00Z")))
            net.says(AirLabs.FLIGHT to """{"error":{"message":"x","code":"month_limit_exceeded"}}""")
            val spent = FlightLoad.load(item, t, asked + 30 * min, ids)!!
            assertEquals(Failure.USED_UP, spent.failure)
            assertNull(FlightRules.every(spent, at("2026-10-02T08:00:00Z")))
            // A new key, or Refresh: the next ask with an answer puts it right.
            net.says(AirLabs.FLIGHT to reply("flight-LH455-in-the-air"))
            val fine = FlightLoad.load(item, spent, asked + 40 * min, ids)!!
            assertNull(fine.failure)
            assertEquals(30 * min, FlightRules.every(fine, at("2026-10-02T07:30:00Z")))
        }
    }

    @Test fun whenTheServiceGoesOnToTheNextDaysFlightTheAskingEndsAndTheFlightStays() {
        online { net ->
            val t = follow(net)
            net.says(AirLabs.FLIGHT to Regex("\\d{4}-\\d{2}-\\d{2}").replace(reply("flight-LH455-in-the-air")) { LocalDate.parse(it.value).plusDays(1).toString() })
            val over = FlightLoad.load(item, t, asked + 30 * min, ids)!!
            // An item follows one flight, never the next day's of the same number.
            assertTrue(over.ended)
            assertEquals(t.flight, over.flight)
            assertEquals("2026-10-01", over.day)
            assertNull(over.failure)
            assertEquals(asked, over.heardAt)
            assertNull(FlightRules.every(over, at("2026-10-02T08:00:00Z")))
        }
    }

    @Test fun aRequestThatIsNotSentLeavesEverythingAsItIs() {
        online { net ->
            val t = follow(net)
            val before = Kept.fetched(AIRLABS).read(item)!!.text
            // The bar hid under the load: the request code refuses, which is neither an answer nor a failure.
            Http.allowed = { false }
            assertNull(FlightLoad.load(item, t, asked + 30 * min, ids))
            assertEquals(Outcome.Unasked, FlightLoad.track(question("LH454"), asked + 30 * min))
            assertEquals(1, sent())
            assertEquals(1, net.asked.size)
            assertEquals(before, Kept.fetched(AIRLABS).read(item)!!.text)
        }
    }

    @Test fun aCancellationIsNotedWhenItIsFirstSeenAndKeptThroughLaterAnswers() {
        online { net ->
            net.says(AirLabs.FLIGHT to reply("flight-LH454-planned"))
            val t = found(FlightLoad.track(question("LH454"), asked)).also { FlightLoad.take(item, it, asked) }
            assertNull(t.alertSince)
            net.says(AirLabs.FLIGHT to reply("flight-LH454-planned").replace("\"scheduled\"", "\"cancelled\""))
            val gone = FlightLoad.load(item, t, asked + 30 * min, ids)!!
            assertEquals(FlightState.CANCELED, gone.flight!!.state)
            assertEquals(asked + 30 * min, gone.alertSince)
            // Refresh, an hour and a half later: still the first moment.
            val later = FlightLoad.load(item, gone, asked + 120 * min, ids)!!
            assertEquals(asked + 30 * min, later.alertSince)
            assertTrue(FlightRules.alert(gone, at("2026-10-02T08:58:00Z")))
            assertFalse(FlightRules.alert(later, at("2026-10-02T09:29:00Z")))
            // It is kept: a restart inside the hour still alerts, and one after it does not start over.
            assertEquals(asked + 30 * min, FlightLoad.kept(item, asked + 121 * min, ids)!!.value.alertSince)
            // A flight found canceled at the first lookup alerts from then.
            net.says(AirLabs.FLIGHT to reply("flight-LH1184-cancelled"))
            assertEquals(asked, found(FlightLoad.track(question("LH1184"), asked)).alertSince)
        }
    }

    @Test fun aFlightIsPutAwayADayAfterItLandedWithoutAskingAnybody() {
        online { net ->
            net.says(AirLabs.FLIGHT to reply("flight-LH96-landed"))                            // landed 07:13 UTC
            val t = found(FlightLoad.track(question("LH96"), asked)).also { FlightLoad.take(item, it, asked) }
            val dayLater = ms("2026-10-03T07:13:00Z")
            // Kept until then, and no request for it.
            assertEquals(t, FlightLoad.kept(item, dayLater - min, ids)!!.value)
            assertNull(FlightRules.every(t, Instant.ofEpochMilli(dayLater - min)))
            // Then the load that is due only removes it.
            assertEquals(0L, FlightRules.every(t, Instant.ofEpochMilli(dayLater)))
            assertEquals(Tracked(), FlightLoad.load(item, t, dayLater, ids))
            assertEquals(emptySet<String>(), Kept.own("flight").names())
            assertEquals(emptySet<String>(), Kept.fetched(AIRLABS).names())
            assertEquals(1, sent())
            // The same when it is found after a restart.
            FlightLoad.take(item, t, asked)
            assertEquals(Tracked(), FlightLoad.kept(item, dayLater, ids)!!.value)
            assertEquals(emptySet<String>(), Kept.own("flight").names())
        }
    }

    // ---- the request log, against a clock

    /** Milliseconds since the simulated bar came up; the wall clock runs with it from [start]. */
    private var elapsed = 0L
    private var start = 0L
    private fun wall() = start + elapsed

    /** The loader as the item wires it, on this test's clock and thread. */
    private fun tracker() = Refresher<String, Tracked>(Refresher.Wiring(Executor { it.run() }, { it.run() }, { elapsed }),
        every = { _, t -> FlightRules.every(t, Instant.ofEpochMilli(wall())) },
        restore = { id -> FlightLoad.kept(id, wall(), ids) },
        load = { id, last -> FlightLoad.load(id, last, wall(), ids) })

    /**
     * The bar is on screen until [until]: the item is asked for its state every ten seconds, as the
     * ticker does. The moments at which a request went out, as "day hour:minute" in UTC.
     */
    private fun watch(r: Refresher<String, Tracked>, until: String): List<String> {
        val log = ArrayList<String>()
        while (wall() < ms(until)) {
            elapsed += 10_000
            val before = sent()
            r.want(item)
            if (sent() > before) log += Instant.ofEpochMilli(wall()).toString().substring(8, 16).replace('T', ' ')
        }
        return log
    }

    /** The lid is closed until [until]: time passes and nobody asks the item for anything. */
    private fun sleep(until: String) { elapsed = ms(until) - start }

    /** The service's answer about LH 454 by the clock: Frankfurt 08:25 UTC to San Francisco 19:40 UTC on 2 October. */
    private fun lh454(): String {
        val planned = reply("flight-LH454-planned")
        val gone = planned.replace("\"dep_actual\": null", "\"dep_actual\": \"2026-10-02 10:25\"")
        return when {
            wall() < ms("2026-10-02T08:25:00Z") -> planned
            wall() < ms("2026-10-02T19:40:00Z") -> gone.replace("\"scheduled\"", "\"en-route\"")
            else -> gone.replace("\"scheduled\"", "\"landed\"").replace("\"arr_actual\": null", "\"arr_actual\": \"2026-10-02 12:40\"")
        }
    }

    @Test fun theRequestLogOfAFlightFromTheEveningBeforeUntilItHasLanded() {
        online { net ->
            net.answer = { Reply.Ok(lh454()) }
            start = ms("2026-10-01T18:00:00Z")                         // fourteen and a half hours before it leaves
            FlightLoad.take(item, found(FlightLoad.track(question("LH454"), wall())), wall())
            assertEquals(1, sent())
            val r = tracker()
            // Far out: one request every three hours. (The first look at the item only reads what was kept.)
            assertEquals(listOf("01 21:00", "02 00:00", "02 03:00"), watch(r, "2026-10-02T05:24:50Z"))
            // From three hours before it leaves: one every half hour, through take-off, until the service says it has landed.
            val near = watch(r, "2026-10-02T20:00:00Z")
            assertEquals(listOf("02 05:25", "02 05:55", "02 06:25", "02 06:55", "02 07:25", "02 07:55", "02 08:25", "02 08:55"), near.take(8))
            assertEquals("02 19:55", near.last())
            assertEquals(30, near.size)
            assertEquals(FlightState.LANDED, r.peek(item)!!.flight!!.state)
            // After landing: none, for as long as the bar stays up.
            assertEquals(emptyList<String>(), watch(r, "2026-10-03T19:00:00Z"))
            assertEquals(1 + 3 + 30, sent())
            // Every one of them asked the one question, about the one number.
            assertEquals(setOf("/api/v9/flight"), net.paths.toSet())
            assertEquals(setOf(listOf("flight_iata" to "LH454", "api_key" to key)), net.asked.map { it.query }.toSet())
            // A day after it landed it is put away, which asks nobody.
            assertEquals(emptyList<String>(), watch(r, "2026-10-03T20:00:00Z"))
            assertEquals(Tracked(), r.peek(item))
            assertEquals(emptySet<String>(), Kept.own("flight").names())
            assertEquals(34, sent())
        }
    }

    @Test fun aNightWithTheLidClosedCostsNothingAndWakingCostsOneRequest() {
        online { net ->
            net.answer = { Reply.Ok(lh454()) }
            start = ms("2026-10-01T18:00:00Z")
            FlightLoad.take(item, found(FlightLoad.track(question("LH454"), wall())), wall())
            val r = tracker()
            assertEquals(listOf("01 21:00"), watch(r, "2026-10-01T22:00:00Z"))
            // Closed at ten in the evening, opened at six: nothing meanwhile, and one request, which was due, on waking.
            sleep("2026-10-02T06:00:00Z")
            assertEquals(2, sent())
            assertEquals(listOf("02 06:00", "02 06:30"), watch(r, "2026-10-02T06:59:00Z"))
            // An hour with the screen off in the near phase: the same.
            sleep("2026-10-02T08:00:00Z")
            assertEquals(4, sent())
            assertEquals(listOf("02 08:00"), watch(r, "2026-10-02T08:29:00Z"))
            // Asleep through the rest of the flight and long after: one request on waking, and it says landed.
            sleep("2026-10-03T02:00:00Z")
            assertEquals(listOf("03 02:00"), watch(r, "2026-10-03T06:00:00Z"))
            assertEquals(FlightState.LANDED, r.peek(item)!!.flight!!.state)
            assertEquals(6, sent())
        }
    }

    @Test fun aRestartCostsNoLookupAndGoesOnWhereItWas() {
        online { net ->
            net.answer = { Reply.Ok(lh454()) }
            start = ms("2026-10-02T06:00:00Z")
            FlightLoad.take(item, found(FlightLoad.track(question("LH454"), wall())), wall())
            assertEquals(listOf("02 06:30"), watch(tracker(), "2026-10-02T06:40:00Z"))
            // The process is made anew ten minutes after the last answer: a new loader, with nothing in memory.
            val again = tracker()
            assertEquals(emptyList<String>(), watch(again, "2026-10-02T06:59:50Z"))
            assertEquals(FlightState.PLANNED, again.peek(item)!!.flight!!.state)
            // The next ask comes half an hour after the last one, not after the restart.
            assertEquals(listOf("02 07:00"), watch(again, "2026-10-02T07:10:00Z"))
        }
    }

    @Test fun aPlanFromTheTimetableIsNotAskedAboutUntilTenHoursBeforeItLeaves() {
        // LH 455 on 5 October, found in the timetable three days ahead: it leaves at 21:40 UTC that day.
        fun shifted(days: Long) = Regex("\\d{4}-\\d{2}-\\d{2}").replace(reply("flight-LH455-in-the-air")) { LocalDate.parse(it.value).plusDays(days).toString() }
        online { net ->
            net.answer = { request ->
                when {
                    request.path == AirLabs.ROUTES -> Reply.Ok(reply("routes-LH455"))
                    // Until three hours before, the service still answers with the flight of the day before; then with this one.
                    wall() < ms("2026-10-05T18:40:00Z") -> Reply.Ok(shifted(((wall() - ms("2026-10-01T21:40:00Z")) / 86_400_000L).coerceIn(0L, 3L)))
                    else -> Reply.Ok(shifted(4))
                }
            }
            start = asked
            val t = found(FlightLoad.track(question("LH455", LocalDate.of(2026, 10, 5)), wall()))
            assertTrue(t.flight!!.timetable)
            FlightLoad.take(item, t, wall())
            assertEquals(2, sent())                                    // the one flight, then the timetable
            val r = tracker()
            // More than ten hours off: nothing, for three days.
            assertEquals(emptyList<String>(), watch(r, "2026-10-05T11:39:50Z"))
            // Then every three hours, and the plan stands while the service is not there yet.
            assertEquals(listOf("05 11:40", "05 14:40", "05 17:40"), watch(r, "2026-10-05T18:39:50Z"))
            assertTrue(r.peek(item)!!.flight!!.timetable)
            assertFalse(r.peek(item)!!.ended)
            // From three hours before, every half hour; the service knows the day now, and the plan becomes the flight.
            assertEquals(listOf("05 18:40", "05 19:10", "05 19:40"), watch(r, "2026-10-05T20:00:00Z"))
            assertFalse(r.peek(item)!!.flight!!.timetable)
            assertEquals("G13", r.peek(item)!!.flight!!.from.gate)
        }
    }

    @Test fun withLookupsToSpareTheLastHourBeforeItLeavesAndTheLastHalfHourBeforeItLandsAreAskedEveryTenMinutes() {
        online { net ->
            // The same service, saying with every reply that plenty of lookups are left.
            net.answer = { Reply.Ok(withRequest(lh454(), left = 900)) }
            start = ms("2026-10-02T06:55:00Z")
            FlightLoad.take(item, found(FlightLoad.track(question("LH454"), wall())), wall())
            val r = tracker()
            assertEquals(listOf("02 07:25", "02 07:35", "02 07:45", "02 07:55", "02 08:05", "02 08:15", "02 08:25", "02 08:55", "02 09:25"), watch(r, "2026-10-02T09:30:00Z"))
            sleep("2026-10-02T18:29:50Z")
            assertEquals(listOf("02 18:30", "02 19:00", "02 19:10", "02 19:20", "02 19:30", "02 19:40"), watch(r, "2026-10-02T20:30:00Z"))
            assertEquals(FlightState.LANDED, r.peek(item)!!.flight!!.state)
            // With fewer than a hundred left the usual pace holds; with fewer than twenty nothing is asked unasked.
            net.answer = { Reply.Ok(withRequest(lh454(), left = 99)) }
            start = ms("2026-10-02T06:55:00Z"); elapsed = 0
            FlightLoad.take(item, found(FlightLoad.track(question("LH454"), wall())), wall())
            assertEquals(listOf("02 07:25", "02 07:55", "02 08:25"), watch(tracker(), "2026-10-02T08:30:00Z"))
            net.answer = { Reply.Ok(withRequest(lh454(), left = 19)) }
            start = ms("2026-10-02T06:55:00Z"); elapsed = 0
            FlightLoad.take(item, found(FlightLoad.track(question("LH454"), wall())), wall())
            assertEquals(emptyList<String>(), watch(tracker(), "2026-10-02T12:00:00Z"))
        }
    }

    @Test fun refreshByHandIsHeldToTwoMinutesAndATryThatReachedNobodyToTenSeconds() {
        online { net ->
            start = asked
            val t = follow(net, wall())
            val r = tracker()
            r.want(item)                                               // reads what was kept
            assertEquals(2 * min, FlightRules.byHand(t))
            elapsed = 119_000
            assertFalse(r.refresh(item, floorMs = FlightRules.byHand(r.peek(item)!!)))
            assertEquals(1, sent())
            elapsed = 120_000
            assertTrue(r.refresh(item, floorMs = FlightRules.byHand(r.peek(item)!!)))
            assertEquals(2, sent())
            // No connection: the next try by hand may come ten seconds later, not two minutes.
            net.fails(Why.OFFLINE)
            elapsed += 2 * min
            assertTrue(r.refresh(item, floorMs = FlightRules.byHand(r.peek(item)!!)))
            assertEquals(Failure.OFFLINE, r.peek(item)!!.failure)
            assertEquals(10_000L, FlightRules.byHand(r.peek(item)!!))
            elapsed += 9_000
            assertFalse(r.refresh(item, floorMs = FlightRules.byHand(r.peek(item)!!)))
            elapsed += 1_000
            assertTrue(r.refresh(item, floorMs = FlightRules.byHand(r.peek(item)!!)))
            // Told to slow down, or any other "no answer": two minutes again. Six presses in a minute are no request.
            net.fails(Why.STATUS, 429)
            elapsed += 2 * min
            assertTrue(r.refresh(item, floorMs = FlightRules.byHand(r.peek(item)!!)))
            assertEquals(Failure.NO_ANSWER, r.peek(item)!!.failure)
            val before = sent()
            repeat(6) { elapsed += 10_000; assertFalse(r.refresh(item, floorMs = FlightRules.byHand(r.peek(item)!!))) }
            assertEquals(before, sent())
        }
    }
}
