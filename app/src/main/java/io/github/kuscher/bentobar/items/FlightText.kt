package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.items.AirLabs.Failure
import io.github.kuscher.bentobar.items.FlightRules.DayForm
import io.github.kuscher.bentobar.items.FlightRules.Heading
import io.github.kuscher.bentobar.items.FlightRules.Kind
import io.github.kuscher.bentobar.items.FlightRules.Phase
import io.github.kuscher.bentobar.items.FlightRules.Saying
import io.github.kuscher.bentobar.items.FlightRules.Tracked
import io.github.kuscher.bentobar.items.FlightRules.Verdict
import io.github.kuscher.bentobar.util.Fmt
import io.github.kuscher.bentobar.util.Sym
import java.text.BreakIterator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * What the Flight item says: its text in the bar and the route line in front of it, the sentence a
 * screen reader gets, and every line of its menu's card. Pure: the words come in as [Voice] (the
 * app's resources in the item, the same files read as text in the tests), and so does the way a time
 * is written. What a flight is doing is [FlightRules]' to say; this file only puts it into words, and
 * into 20 characters where it is the bar.
 */
object FlightText {
    /** The most characters the bar's text has. What is longer loses its least important part, in the order each phase gives. */
    const val LIMIT = 20

    /** A string of the app's resources, by its name there in capitals: `FLIGHT_BAR_GATE` is `flight_bar_gate`. */
    enum class Word {
        COMMON_NO_CONNECTION, COMMON_UPDATED, COMMON_UPDATED_OFFLINE, COMMON_UPDATED_NO_ANSWER, COMMON_DURATION_MIN, COMMON_DURATION_H_MIN,
        FLIGHT_BAR_LOOKING, FLIGHT_BAR_PLAN, FLIGHT_BAR_GATE, FLIGHT_BAR_LANDED_BELT, FLIGHT_BAR_LANDED_AT, FLIGHT_BAR_CANCELED, FLIGHT_BAR_DIVERTED,
        FLIGHT_BAR_NO_UPDATE, FLIGHT_BAR_NOT_LIVE, FLIGHT_BAR_LATE, FLIGHT_BAR_EARLY, FLIGHT_TOOLTIP, FLIGHT_NONE, FLIGHT_LOOKING_UP, FLIGHT_ANOTHER_SUBTITLE,
        FLIGHT_HEADER, FLIGHT_ROUTE, FLIGHT_LEAVES_AT, FLIGHT_LEAVES_IN, FLIGHT_LANDS_IN, FLIGHT_IN_AIR, FLIGHT_JUST_LANDED, FLIGHT_LANDED_AGO, FLIGHT_LANDED,
        FLIGHT_CANCELED, FLIGHT_DIVERTED, FLIGHT_TIMETABLE, FLIGHT_NO_UPDATE, FLIGHT_BADGE_PLANNED, FLIGHT_BADGE_ON_TIME, FLIGHT_BADGE_DELAYED, FLIGHT_BADGE_LATE, FLIGHT_BADGE_EARLY,
        FLIGHT_TERMINAL, FLIGHT_BELT, FLIGHT_OPERATED_AS, FLIGHT_NOTE, FLIGHT_STATUS_REFUSED, FLIGHT_STATUS_USED_UP, FLIGHT_STATUS_FEW,
        FLIGHT_COPY_HEAD, FLIGHT_COPY_LEFT, FLIGHT_COPY_LANDS,
        FLIGHT_ERR_NOT_FOUND, FLIGHT_ERR_NOT_THAT_DAY, FLIGHT_ERR_REFUSED, FLIGHT_ERR_USED_UP, FLIGHT_ERR_NO_ANSWER,
        FLIGHT_CHOICE_DAY, FLIGHT_CHOICE_NEXT, FLIGHT_CHOICE_SPAN, FLIGHT_DESC_CHOICE, FLIGHT_DESC_CHOICE_LEAVES, FLIGHT_DESC_CHOICE_LANDS,
        FLIGHT_DESC_LEAVES_AT, FLIGHT_DESC_LEAVES_IN, FLIGHT_DESC_LANDS_IN, FLIGHT_DESC_LANDED, FLIGHT_DESC_LATE, FLIGHT_DESC_EARLY, FLIGHT_DESC_ON_TIME, FLIGHT_DESC_GATE, FLIGHT_DESC_BELT,
        FLIGHT_DESC_CANCELED, FLIGHT_DESC_DIVERTED, FLIGHT_DESC_NO_UPDATE, FLIGHT_DESC_NOT_LIVE, FLIGHT_DESC_LOOKING, FLIGHT_DESC_HEADLINE_BADGE,
        FLIGHT_ROUTE_FROM, FLIGHT_ROUTE_TO, FLIGHT_ROUTE_FROM_CODE, FLIGHT_ROUTE_TO_CODE, FLIGHT_TIME_WAS,
    }

    /** A plural of the app's resources, named the same way. */
    enum class Count { COMMON_HOURS, COMMON_MINUTES, FLIGHT_LOOKUPS_LEFT, FLIGHT_CHOICE_TIMES, FLIGHT_CHOICE_FLIGHTS }

    /** How a time is written: "2:40 PM", "Fri", "Fri 2:40 PM", "Dec 24", "Dec 24, 2:40 PM", "Sat, Oct 10". With the system's 12 or 24 hours. */
    enum class TimeForm { TIME, DAY, DAY_TIME, DATE, DATE_TIME, DAY_DATE }

    /**
     * The words and the clock of whoever reads this: [word] gives a resource's text as it is written
     * there (with its `%1$s`), [count] a plural for a number, [clock] writes a wall-clock time in one
     * of the [TimeForm]s. [zone] is the device's: it decides what "today" is, and how the moment of
     * the last answer reads. An airport's own time never passes through it.
     */
    class Voice(val locale: Locale, val zone: ZoneId, private val word: (Word) -> String, private val plural: (Count, Int) -> String,
                private val clock: (LocalDateTime, TimeForm) -> String) {
        fun say(w: Word, vararg args: Any): String = if (args.isEmpty()) word(w) else String.format(locale, word(w), *args)
        fun count(c: Count, n: Int): String = plural(c, n)
        fun time(t: LocalDateTime, form: TimeForm): String = clock(t, form)
    }

    /**
     * What the item shows in the bar. [text] null: the glyph alone. [route]: the line the bar draws in
     * the glyph's place, with the flight's plane on it; null: none, and the glyph is drawn. The glyph
     * stays what a menu lists the item by.
     */
    data class Bar(val icon: String, val text: String? = null, val tone: Tone = Tone.NORMAL, val active: Boolean = false, val desc: String, val tooltip: String? = null,
                   val route: BarRoute? = null)

    /** One end of the route line: the airport, its time ([struck]: it will not happen), the small words under it, and the end as a sentence. */
    data class End(val code: String, val time: String, val struck: Boolean, val words: String, val spoken: String)

    /**
     * The menu's card for a followed flight. [gone]: the headline says the flight will not arrive as
     * planned (canceled, diverted). [spoken]: headline and badge as the one sentence a screen reader
     * gets. [share]: where the plane stands, 0 to 1; null: no plane, and a faint line. [plane] names the
     * flight the plane belongs to, so that it is only ever drawn further along. [changeKey]: the key
     * was refused, and the way to the key comes first. [loose]: a plan whose times may be an hour off.
     */
    data class Card(
        val title: String, val route: String, val headline: String, val gone: Boolean, val badge: String?, val kind: Kind, val spoken: String,
        val share: Double?, val from: End, val to: End, val operatedAs: String?, val loose: Boolean, val note: String,
        val copy: String, val callsign: String?, val changeKey: Boolean, val plane: String,
    )

    /**
     * One of several flights of a number to choose from, as an entry of the menu. [title]: its route, with
     * the cities where the service named them. [detail]: when it leaves and when it lands, each in its
     * airport's own time, with the day in front where it does not leave on the device's today. [spoken]:
     * both as the one sentence a screen reader gets.
     */
    data class Choice(val title: String, val detail: String, val spoken: String)

    /** How many characters [text] has as a reader counts them: a letter with its accent, or an emoji, is one. */
    fun length(text: String): Int {
        val chars = BreakIterator.getCharacterInstance()
        chars.setText(text)
        var n = 0
        while (chars.next() != BreakIterator.DONE) n++
        return n
    }

    /** The first of [tries] that fits the bar; the last if none does (it is the shortest there is to say). */
    private fun fit(vararg tries: String): String = tries.firstOrNull { length(it) <= LIMIT } ?: tries.last()

    /**
     * A flight at one moment, with everything the bar and the card both say of it. [line]: the bar
     * draws the flight's route line; [shown]: where its plane was last drawn on one, null: nowhere yet.
     */
    private class Look(val t: Tracked, heard: Flight, val now: Instant, val v: Voice, private val line: Boolean = false, val shown: Double? = null) {
        val f = FlightRules.shown(heard, now, t.ended)
        val number = FlightNumber.shown(f.number)
        val today: LocalDate = LocalDate.ofInstant(now, v.zone)
        val row = FlightRules.row(f, now)
        val said = FlightRules.said(f, now)
        val where = FlightRules.where(f)
        /** Within three hours of leaving or in the air, an answer over an hour old is not live: what it says of late, early and the gate may be wrong by now. */
        val stale = FlightRules.stale(t, now)
        /** Still called planned, its time to leave passed, and no answer for over an hour: nobody knows what became of it. */
        val silent = FlightRules.silent(t, now)
        val tooltip = v.say(Word.FLIGHT_TOOLTIP, number, f.from.place, f.to.place)

        /** Two parts as the bar joins them: "a · b". */
        fun join(a: String, b: String) = v.say(Word.FLIGHT_BAR_PLAN, a, b)

        /** A time at its airport, with its day when that is not the device's today: "2:40 PM", "Fri 2:40 PM", "Dec 24, 2:40 PM". */
        fun at(time: LocalDateTime) = at(time, today, v)

        /** When the service last answered about this flight, on the device's clock. */
        val updated: String get() = at(LocalDateTime.ofInstant(Instant.ofEpochMilli(t.heardAt), v.zone))

        /**
         * The route line with the plane at [share] of its way, in the color of how the flight stands
         * (an answer that is not live claims nothing); null where the bar draws no line. Only a flight
         * that will not arrive as planned has a line of one color from end to end. [struck]: its plane
         * is the crossed-out one.
         */
        fun route(share: Double, struck: Boolean = false): BarRoute? {
            if (!line) return null
            val stands = FlightRules.stands(f, now, stale)
            return BarRoute(share.toFloat(), stands, struck, whole = stands == Stands.WILL_NOT_ARRIVE)
        }

        /** The line of a flight that goes its way: its plane where the times put it ([share]), and never behind where it was last drawn. */
        fun flying(share: Double): BarRoute? = route(FlightRules.forward(shown, share) ?: share)
    }

    /** [time], an airport's own, as it is written for someone whose day is [today]: with its weekday within six days of that, with its date further off. */
    private fun at(time: LocalDateTime, today: LocalDate, v: Voice): String =
        v.time(time, when (FlightRules.dayForm(time, today)) { DayForm.NONE -> TimeForm.TIME; DayForm.WEEKDAY -> TimeForm.DAY_TIME; DayForm.DATE -> TimeForm.DATE_TIME })

    /** A length of time in the bar: "45m", "1h 37m", "2h 05m". */
    private fun figure(minutes: Long): String = Fmt.duration(minutes * 60_000L)

    /** In a headline or a badge: "45 min", "1 h 37 min", "2 h 05 min" (two places for the minutes from an hour on, so nothing shifts as it counts). */
    private fun written(minutes: Long, v: Voice): String =
        if (minutes < 60) v.say(Word.COMMON_DURATION_MIN, minutes) else v.say(Word.COMMON_DURATION_H_MIN, minutes / 60, minutes % 60)

    /** As it is spoken: "45 minutes", "2 hours", "1 hour 37 minutes". */
    private fun spoken(minutes: Long, v: Voice): String {
        val h = (minutes / 60).toInt()
        val m = (minutes % 60).toInt()
        return listOfNotNull(if (h > 0) v.count(Count.COMMON_HOURS, h) else null, if (m > 0 || h == 0) v.count(Count.COMMON_MINUTES, m) else null).joinToString(" ")
    }

    private fun span(minutes: Long, v: Voice, aloud: Boolean) = if (aloud) spoken(minutes, v) else written(minutes, v)

    /**
     * The item in the bar at [now]. [t]: what the item follows, as last heard; [looking]: the number
     * being looked up, as it is shown, while nothing is followed yet. [beforeHours]: the rule's hours,
     * from which the item counts as having something to say.
     *
     * [line]: the item draws a route line in its glyph's place (it is shown as an icon, or as icon and
     * text). The bar then has a [Bar.route] from the countdown, three hours before the flight leaves,
     * until an hour after it landed, and for as long as a cancellation or a diversion is shown. Its
     * words are what they are without the line, in the bar's own color: how the flight stands is the
     * line's to say, and only the alert stays. Without [line], which is the item shown as text alone,
     * all is as it was before there was a line. [shown]: where this flight's plane was last drawn, in
     * the bar or on the menu's line ([plane] names it): it only goes forward.
     */
    fun bar(t: Tracked?, looking: String?, now: Instant, beforeHours: Int, v: Voice, line: Boolean = false, shown: Double? = null): Bar {
        val heard = if (t == null || FlightRules.cleared(t, now)) null else t.flight
        if (t == null || heard == null) return when {
            looking != null -> Bar(Sym.FLIGHT, v.say(Word.FLIGHT_BAR_LOOKING, looking), desc = v.say(Word.FLIGHT_DESC_LOOKING, looking))
            // Followed, and nothing heard of it yet (it was asked for and the ask came to nothing): the plane alone, and the menu says why.
            t != null && t.following && t.flight == null -> Bar(Sym.FLIGHT, desc = v.say(Word.FLIGHT_DESC_NO_UPDATE, FlightNumber.shown(t.number)))
            else -> Bar(Sym.FLIGHT, desc = v.say(Word.FLIGHT_NONE))
        }
        val l = Look(t, heard, now, v, line, shown)
        return when (l.row.phase) {
            // What will not happen as planned stays in the bar until the flight is cleared; its alert is for the first hour.
            // On the line its struck plane stands at the start, wherever a plane of this flight was drawn before: it never leaves.
            Phase.CANCELED -> Bar(Sym.AIRPLANEMODE_INACTIVE, fit(v.say(Word.FLIGHT_BAR_CANCELED, l.number), v.say(Word.FLIGHT_BAR_CANCELED, l.f.number)),
                alert(l), active = true, desc = v.say(Word.FLIGHT_DESC_CANCELED, l.number), tooltip = l.tooltip).lined(l.route(0.0, struck = true))
            // Nobody knows where a diverted flight is going: its plane stays where it was last drawn, and stands in the middle if it never was.
            Phase.DIVERTED -> Bar(Sym.FLIGHT, fit(v.say(Word.FLIGHT_BAR_DIVERTED, l.number), v.say(Word.FLIGHT_BAR_DIVERTED, l.f.number)),
                alert(l), active = true, desc = v.say(Word.FLIGHT_DESC_DIVERTED, l.number), tooltip = l.tooltip).lined(l.route(l.shown ?: 0.5))
            Phase.TIMETABLE -> noUpdate(l)
            Phase.AHEAD -> ahead(l, beforeHours)
            Phase.SOON -> if (l.silent) noUpdate(l) else soon(l)
            Phase.IN_AIR -> air(l)
            Phase.LANDED -> landed(l)
        }
    }

    private fun alert(l: Look) = if (FlightRules.alert(l.t, l.now)) Tone.ALERT else Tone.NORMAL

    /**
     * This bar with its route line, where there is one to draw. The words stay as they are and take
     * the bar's own color: how the flight stands is the line's to say now. Only the alert stays.
     */
    private fun Bar.lined(route: BarRoute?): Bar = if (route == null) this else copy(tone = if (tone == Tone.ALERT) tone else Tone.NORMAL, route = route)

    /**
     * The name the plane of [t]'s flight is remembered by: the bar and the menu's card draw one plane,
     * which only ever goes forward ([Card.plane]). Null: [t] holds no flight.
     */
    fun plane(t: Tracked): String? = t.flight?.let { listOf(t.number, t.day.orEmpty(), it.from.code).joinToString(" ") }

    /**
     * Where the plane of [route] has flown to, to be remembered for its next drawing; null where the line
     * says nothing of that: there is none, or the flight will not arrive (where its plane stands on a
     * line that is over is no place it has flown to).
     */
    fun flown(route: BarRoute?): Double? = route?.takeUnless { it.whole }?.share?.toDouble()

    /** A plan whose time passed with no word: the plain plane and "no update", until the flight is cleared or the service speaks. */
    private fun noUpdate(l: Look): Bar = Bar(Sym.FLIGHT, fit(l.v.say(Word.FLIGHT_BAR_NO_UPDATE, l.number), l.v.say(Word.FLIGHT_BAR_NO_UPDATE, l.f.number), l.number),
        active = true, desc = l.v.say(Word.FLIGHT_DESC_NO_UPDATE, l.number), tooltip = l.tooltip)

    /** More than three hours before it leaves: the number, and the day and time. Too long: the number loses its space; then the time goes. */
    private fun ahead(l: Look, beforeHours: Int): Bar {
        val v = l.v
        val leaves = l.f.from.time ?: return Bar(Sym.FLIGHT_TAKEOFF, l.number, desc = l.tooltip, tooltip = l.tooltip)
        fun plan(number: String, form: TimeForm) = l.join(number, v.time(leaves, form))
        val text = when (FlightRules.dayForm(leaves, l.today)) {
            DayForm.NONE -> fit(plan(l.number, TimeForm.TIME), plan(l.f.number, TimeForm.TIME), l.number)
            DayForm.WEEKDAY -> fit(plan(l.number, TimeForm.DAY_TIME), plan(l.f.number, TimeForm.DAY_TIME), plan(l.number, TimeForm.DAY), plan(l.f.number, TimeForm.DAY), l.number)
            // Beyond six days a date, and no time.
            DayForm.DATE -> fit(plan(l.number, TimeForm.DATE), plan(l.f.number, TimeForm.DATE), l.number)
        }
        val active = !l.now.isBefore(l.f.from.moment(leaves).minus(Duration.ofHours(beforeHours.toLong())))
        return Bar(Sym.FLIGHT_TAKEOFF, text, active = active, desc = v.say(Word.FLIGHT_DESC_LEAVES_AT, l.number, l.f.to.place, l.at(leaves)), tooltip = l.tooltip)
    }

    /** Within three hours: the time to go, how late, the gate ("Gate G13" if it fits, else "G13", else none). On time and no gate: when it leaves. */
    private fun soon(l: Look): Bar {
        val v = l.v
        val minutes = l.row.headline.minutes
        val figure = figure(minutes)
        // Leaving early is on time: the figure counts to the earlier time already.
        val late = l.said.minutes.toLong().takeIf { l.said.saying == Saying.DELAYED }
        val gate = l.where.gate
        val leaves = l.f.from.time
        val text = when {
            l.stale -> v.say(Word.FLIGHT_BAR_NOT_LIVE, figure)
            late != null -> l.join(figure, v.say(Word.FLIGHT_BAR_LATE, figure(late))).let { both ->
                if (gate == null) fit(both, figure) else fit(l.join(both, v.say(Word.FLIGHT_BAR_GATE, gate)), l.join(both, gate), both, figure)
            }
            gate != null -> fit(l.join(figure, v.say(Word.FLIGHT_BAR_GATE, gate)), l.join(figure, gate), figure)
            // (Not a time that has passed: beside the last minute it would read as when it leaves.)
            leaves != null && !FlightRules.overdue(l.f, l.now) -> fit(l.join(figure, v.time(leaves, TimeForm.TIME)), figure)
            else -> figure
        }
        val desc = v.say(Word.FLIGHT_DESC_LEAVES_IN, l.number, l.f.to.place, spoken(minutes, v)) + if (l.stale) v.say(Word.FLIGHT_DESC_NOT_LIVE, l.updated)
            else late?.let { v.say(Word.FLIGHT_DESC_LATE, spoken(it, v)) }.orEmpty() + gate?.let { v.say(Word.FLIGHT_DESC_GATE, it) }.orEmpty()
        // On the line the plane waits at the start: nothing is flown until the service says it has left.
        return Bar(Sym.FLIGHT_TAKEOFF, text, counting(l.stale, late != null, minutes), active = true, desc = desc, tooltip = l.tooltip).lined(l.row.share?.let(l::flying))
    }

    /** In the air: the time to landing, then how late or early; on time, when it lands. */
    private fun air(l: Look): Bar {
        val v = l.v
        if (l.row.headline.heading != Heading.LANDS_IN) return Bar(Sym.FLIGHT_LAND, v.say(Word.FLIGHT_IN_AIR), active = true, desc = l.tooltip, tooltip = l.tooltip)
        val minutes = l.row.headline.minutes
        val figure = figure(minutes)
        val off = l.said.minutes.toLong()
        val late = l.said.saying == Saying.AIR_LATE
        val early = l.said.saying == Saying.AIR_EARLY
        val lands = l.f.to.time
        val text = when {
            l.stale -> v.say(Word.FLIGHT_BAR_NOT_LIVE, figure)
            late -> fit(l.join(figure, v.say(Word.FLIGHT_BAR_LATE, figure(off))), figure)
            early -> fit(l.join(figure, v.say(Word.FLIGHT_BAR_EARLY, figure(off))), figure)
            lands != null -> fit(l.join(figure, v.time(lands, TimeForm.TIME)), figure)
            else -> figure
        }
        // A line only where the plane has a place on it: with no time it left by, the minutes to landing still count, and how far it is nobody can say.
        val route = l.row.share?.let(l::flying)
        val desc = v.say(Word.FLIGHT_DESC_LANDS_IN, l.number, l.f.to.place, spoken(minutes, v)) + when {
            l.stale -> v.say(Word.FLIGHT_DESC_NOT_LIVE, l.updated)
            late -> v.say(Word.FLIGHT_DESC_LATE, spoken(off, v))
            early -> v.say(Word.FLIGHT_DESC_EARLY, spoken(off, v))
            // A green line is for the eye, and no figure in the words goes with it: the sentence says what it means.
            route?.stands == Stands.GOOD -> v.say(Word.FLIGHT_DESC_ON_TIME)
            else -> ""
        }
        return Bar(Sym.FLIGHT_LAND, text, counting(l.stale, late, minutes), active = true, desc = desc, tooltip = l.tooltip).lined(route)
    }

    /** A countdown's color: a warning while it runs late, else the accent in its last half hour. Not live, it claims neither. */
    private fun counting(stale: Boolean, late: Boolean, minutes: Long) = when {
        stale -> Tone.NORMAL
        late -> Tone.WARN
        minutes <= 30 -> Tone.ACCENT
        else -> Tone.NORMAL
    }

    /** Landed: for an hour the belt, else when it landed. Then the plain plane, while the menu keeps the flight. */
    private fun landed(l: Look): Bar {
        val v = l.v
        val at = l.f.to.time
        val belt = l.where.belt
        val since = Duration.between(at?.let(l.f.to::moment) ?: Instant.ofEpochMilli(l.t.heardAt), l.now)
        val inItsHour = since < FlightRules.LANDED_SHOWN
        // For its hour in the bar the plane stands at the far end of its line, all of it flown.
        val route = if (inItsHour) l.route(1.0) else null
        // The words say nothing of how it landed against its plan, and the line says it in a color only: the sentence
        // says that it was on time where the line is green, and by how much it was late where it is yellow or red.
        val how = when (route?.stands) {
            Stands.GOOD -> v.say(Word.FLIGHT_DESC_ON_TIME)
            Stands.LATE, Stands.VERY_LATE -> FlightRules.badge(l.f, l.now)?.minutes?.takeIf { it > 0 }?.let { v.say(Word.FLIGHT_DESC_LATE, spoken(it.toLong(), v)) }.orEmpty()
            else -> ""
        }
        val desc = (at?.let { v.say(Word.FLIGHT_DESC_LANDED, l.number, v.time(it, TimeForm.TIME)) } ?: l.tooltip) + how + belt?.let { v.say(Word.FLIGHT_DESC_BELT, it) }.orEmpty()
        if (!inItsHour) return Bar(Sym.FLIGHT, desc = desc, tooltip = l.tooltip)
        val plain = v.say(Word.FLIGHT_LANDED)
        val timed = at?.let { v.say(Word.FLIGHT_BAR_LANDED_AT, v.time(it, TimeForm.TIME)) } ?: plain
        return Bar(Sym.FLIGHT_LAND, if (belt == null) fit(timed, plain) else fit(v.say(Word.FLIGHT_BAR_LANDED_BELT, belt), timed, plain), active = true, desc = desc, tooltip = l.tooltip)
            .lined(route)
    }

    /** The headline as the card writes it, or ([aloud]) as it is spoken. An hour after landing it is "Landed" and counts no more. */
    private fun headline(l: Look, aloud: Boolean): String {
        val v = l.v
        val h = l.row.headline
        return when (h.heading) {
            Heading.LEAVES_AT -> l.f.from.time?.let { v.say(Word.FLIGHT_LEAVES_AT, l.at(it)) } ?: v.say(Word.FLIGHT_BADGE_PLANNED)
            Heading.LEAVES_IN -> v.say(Word.FLIGHT_LEAVES_IN, span(h.minutes, v, aloud))
            Heading.LANDS_IN -> v.say(Word.FLIGHT_LANDS_IN, span(h.minutes, v, aloud))
            Heading.IN_AIR -> v.say(Word.FLIGHT_IN_AIR)
            Heading.LANDED_NOW -> v.say(Word.FLIGHT_JUST_LANDED)
            Heading.LANDED_AGO -> if (h.minutes < FlightRules.LANDED_SHOWN.toMinutes()) v.say(Word.FLIGHT_LANDED_AGO, span(h.minutes, v, aloud)) else v.say(Word.FLIGHT_LANDED)
            Heading.LANDED -> v.say(Word.FLIGHT_LANDED)
            Heading.CANCELED -> v.say(Word.FLIGHT_CANCELED)
            Heading.DIVERTED -> v.say(Word.FLIGHT_DIVERTED)
            Heading.TIMETABLE -> v.say(Word.FLIGHT_TIMETABLE)
        }
    }

    private fun badge(l: Look, aloud: Boolean): String? = l.row.badge?.let { b ->
        when (b.verdict) {
            Verdict.PLANNED -> l.v.say(Word.FLIGHT_BADGE_PLANNED)
            Verdict.ON_TIME -> l.v.say(Word.FLIGHT_BADGE_ON_TIME)
            Verdict.DELAYED -> l.v.say(Word.FLIGHT_BADGE_DELAYED, span(b.minutes.toLong(), l.v, aloud))
            Verdict.LATE -> l.v.say(Word.FLIGHT_BADGE_LATE, span(b.minutes.toLong(), l.v, aloud))
            Verdict.EARLY -> l.v.say(Word.FLIGHT_BADGE_EARLY, span(b.minutes.toLong(), l.v, aloud))
        }
    }

    /**
     * One end of the line. Its small words, the gate first: "Gate G13 · Terminal 1", "Terminal 1 · Belt
     * 21", the aircraft. As a sentence: the city, the letters, the time ("was 10:50 AM" for one that
     * will not happen) and the same words.
     */
    private fun end(l: Look, e: FlightRules.EndSays, city: String, from: Boolean): End {
        val v = l.v
        val time = e.time?.let(l::at).orEmpty()
        val words = listOfNotNull(e.gate?.let { v.say(Word.FLIGHT_BAR_GATE, it) }, e.terminal?.let { v.say(Word.FLIGHT_TERMINAL, it) },
            e.belt?.let { v.say(Word.FLIGHT_BELT, it) }, e.aircraft)
        val rest = (listOfNotNull(time.takeIf { it.isNotEmpty() }?.let { if (e.struck) v.say(Word.FLIGHT_TIME_WAS, it) else it }) + words).joinToString(", ")
        val spoken = when {
            // Nothing is known of it but its letters: those, once.
            city.isEmpty() && rest.isEmpty() -> e.code
            city.isEmpty() || rest.isEmpty() -> v.say(if (from) Word.FLIGHT_ROUTE_FROM_CODE else Word.FLIGHT_ROUTE_TO_CODE, city.ifEmpty { e.code }, rest.ifEmpty { e.code })
            else -> v.say(if (from) Word.FLIGHT_ROUTE_FROM else Word.FLIGHT_ROUTE_TO, city, e.code, rest)
        }
        return End(e.code, time, e.struck, words.reduceOrNull(l::join).orEmpty(), spoken)
    }

    /** The card for what [t] follows at [now]; null: there is no flight to show (nothing is followed, nothing was heard yet, or it is cleared). */
    fun card(t: Tracked, now: Instant, v: Voice): Card? {
        val heard = t.flight
        if (heard == null || FlightRules.cleared(t, now)) return null
        val l = Look(t, heard, now, v)
        val f = l.f
        val row = l.row
        // No word of a flight past its time: that is the headline, with no badge and no plane, whatever was last said of it.
        val headline = if (l.silent) v.say(Word.FLIGHT_NO_UPDATE) else headline(l, aloud = false)
        val badge = if (l.silent) null else badge(l, aloud = false)
        val spoken = if (l.silent) headline else badge(l, aloud = true)?.let { v.say(Word.FLIGHT_DESC_HEADLINE_BADGE, headline(l, aloud = true), it) } ?: headline(l, aloud = true)
        val before = row.phase == Phase.AHEAD || row.phase == Phase.SOON
        // Before it leaves the far end has its terminal too, where one is named: whoever meets the flight looks for it there.
        val far = if (before) row.to.copy(terminal = f.to.terminal) else row.to
        val few = t.left != null && t.left < FlightRules.FEW && FlightRules.pace(f, now) != null
        // What is on screen came from the service, so its credit stays; only the end of the line says what the last ask came to.
        val status = when (t.failure) {
            Failure.REFUSED -> v.say(Word.FLIGHT_STATUS_REFUSED, l.updated)
            Failure.USED_UP -> v.say(Word.FLIGHT_STATUS_USED_UP, l.updated)
            Failure.OFFLINE -> v.say(Word.COMMON_UPDATED_OFFLINE, l.updated)
            Failure.NO_ANSWER -> v.say(Word.COMMON_UPDATED_NO_ANSWER, l.updated)
            else -> v.say(if (few) Word.FLIGHT_STATUS_FEW else Word.COMMON_UPDATED, l.updated)
        }
        return Card(
            title = if (f.airline.isEmpty()) l.number else v.say(Word.FLIGHT_HEADER, l.number, f.airline),
            route = v.say(Word.FLIGHT_ROUTE, f.from.place, f.to.place),
            headline = headline, gone = row.phase == Phase.CANCELED || row.phase == Phase.DIVERTED,
            badge = badge, kind = row.badge?.kind ?: Kind.PLAIN, spoken = spoken,
            share = row.share.takeUnless { l.silent },
            from = end(l, row.from, f.from.city, from = true), to = end(l, far, f.to.city, from = false),
            operatedAs = f.flownAs?.let { v.say(Word.FLIGHT_OPERATED_AS, FlightNumber.shown(it)) },
            loose = f.loose && f.timetable && before, note = v.say(Word.FLIGHT_NOTE, status),
            copy = copied(l, headline, badge), callsign = f.callsign, changeKey = t.failure == Failure.REFUSED,
            plane = plane(t).orEmpty(),
        )
    }

    /**
     * The flight as one line for the clipboard, with absolute times and whatever is not known left out:
     * "LH 455 SFO → FRA · Leaves 3:05 PM · Delayed 25 min · Gate G13 · Lands Sat 10:50 AM". Of a flight
     * past its time to leave, with no word that it left, the line says what the card's [headline] says
     * ("Leaves in 1 min", then "No update"), so the two cannot disagree about a time that is over.
     */
    private fun copied(l: Look, headline: String, badge: String?): String {
        val v = l.v
        val f = l.f
        val head = v.say(Word.FLIGHT_COPY_HEAD, l.number, f.from.code, f.to.code)
        val left = f.from.time?.let { v.say(Word.FLIGHT_COPY_LEFT, l.at(it)) }
        val lands = f.to.time?.let { v.say(Word.FLIGHT_COPY_LANDS, l.at(it)) }
        val parts = when (l.row.phase) {
            Phase.CANCELED, Phase.DIVERTED, Phase.TIMETABLE -> listOf(head, headline)
            // Past its time with no word that it left, the line says what the headline says: "Leaves in 1 min", then "No update".
            Phase.AHEAD, Phase.SOON -> if (l.silent) listOf(head, headline) else listOfNotNull(head,
                if (FlightRules.overdue(f, l.now)) headline else f.from.time?.let { v.say(Word.FLIGHT_LEAVES_AT, l.at(it)) },
                badge, l.where.gate?.let { v.say(Word.FLIGHT_BAR_GATE, it) }, lands)
            Phase.IN_AIR -> listOfNotNull(head, left, badge, lands)
            Phase.LANDED -> listOfNotNull(head, left, f.to.time?.let { v.say(Word.FLIGHT_BAR_LANDED_AT, l.at(it)) } ?: v.say(Word.FLIGHT_LANDED), badge, l.where.belt?.let { v.say(Word.FLIGHT_BELT, it) })
        }
        // Plain spaces: the line is for other apps, where a no-break space is only in the way (a device's times have a narrow one before AM and PM).
        return parts.reduce(l::join).replace('\u00A0', ' ').replace('\u202F', ' ')
    }

    /** Why a press of Track found nothing, in the menu's words. [number] as it is shown; [day]: the day that was chosen. */
    fun error(failure: Failure, number: String, day: LocalDate?, v: Voice): String = when (failure) {
        Failure.NOT_FOUND -> v.say(Word.FLIGHT_ERR_NOT_FOUND, number)
        Failure.NOT_THAT_DAY -> if (day == null) v.say(Word.FLIGHT_ERR_NOT_FOUND, number) else v.say(Word.FLIGHT_ERR_NOT_THAT_DAY, number, v.time(day.atStartOfDay(), TimeForm.DAY_DATE))
        Failure.REFUSED -> v.say(Word.FLIGHT_ERR_REFUSED)
        Failure.USED_UP -> v.say(Word.FLIGHT_ERR_USED_UP)
        Failure.OFFLINE -> v.say(Word.COMMON_NO_CONNECTION)
        Failure.NO_ANSWER -> v.say(Word.FLIGHT_ERR_NO_ANSWER)
    }

    /** True where the text in the field is what is wrong (no such flight, not on that day): the field is marked. Else it is a message under it. */
    fun ofTheField(failure: Failure): Boolean = failure == Failure.NOT_FOUND || failure == Failure.NOT_THAT_DAY

    /**
     * What the menu asks when a press of Track found the number flying more than once: [count] flights
     * on [day], the day that was chosen, which is the device's ("UA 1227 flies 3 times on Tue, Oct 6"),
     * or, with none, among the next ones ("UA 1227: 3 flights in the next 24 hours", which are
     * [AirLabs.DAY_AHEAD]). [number] as it is shown.
     */
    fun several(number: String, day: LocalDate?, count: Int, v: Voice): String =
        if (day == null) v.say(Word.FLIGHT_CHOICE_NEXT, number, v.count(Count.FLIGHT_CHOICE_FLIGHTS, count))
        else v.say(Word.FLIGHT_CHOICE_DAY, number, v.count(Count.FLIGHT_CHOICE_TIMES, count), v.time(day.atStartOfDay(), TimeForm.DAY_DATE))

    /**
     * [f], one of the flights to choose from, as it reads at [now]: "Orlando → Newark" and "Tue 8:45 AM –
     * 11:26 AM". The times are the ones its card will show once it is followed (what is expected, else
     * the plan). With no time to leave there are none: a time of landing alone would read as the other.
     */
    fun choice(f: Flight, now: Instant, v: Voice): Choice {
        val leaves = f.from.time?.let { at(it, LocalDate.ofInstant(now, v.zone), v) }
        val lands = f.to.time?.let { v.time(it, TimeForm.TIME) }
        return Choice(
            title = v.say(Word.FLIGHT_ROUTE, f.from.place, f.to.place),
            detail = if (leaves != null && lands != null) v.say(Word.FLIGHT_CHOICE_SPAN, leaves, lands) else leaves.orEmpty(),
            spoken = v.say(Word.FLIGHT_DESC_CHOICE, f.from.place, f.to.place) + leaves?.let { v.say(Word.FLIGHT_DESC_CHOICE_LEAVES, it) }.orEmpty() +
                lands?.let { v.say(Word.FLIGHT_DESC_CHOICE_LANDS, it) }.orEmpty(),
        )
    }

    /** The days the chips under the field stand for: the next flight (no day), today, tomorrow, and the five days after. */
    fun days(today: LocalDate): List<LocalDate?> = listOf<LocalDate?>(null) + (0L..6L).map { today.plusDays(it) }
}
