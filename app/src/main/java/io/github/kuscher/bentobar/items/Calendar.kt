package io.github.kuscher.bentobar.items

import io.github.kuscher.bentobar.data.Uses
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import io.github.kuscher.bentobar.R
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.Executors

/** Upcoming calendar events from the Calendar provider (needs READ_CALENDAR). Tasks synced in by task apps are left out. */
object Calendar {
    data class Event(
        val eventId: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val color: Int,
        val location: String,
        /** On a calendar the user can edit (contributor access or more): not holidays, birthdays or subscriptions. */
        val editable: Boolean,
        /**
         * A real meeting (see [Meetings.isMeeting]): timed, on an [editable] calendar, and with a video-call
         * [link] or at least one attendee besides the user. A flight Gmail added on its own is not one.
         */
        val meeting: Boolean,
        /** A video-call link found in the location or description. */
        val link: String?,
    ) {
        /** A place to get directions to: a timed event's location on an editable calendar, unless that is itself a link. */
        val place: String? get() = location.trim().takeIf { editable && !allDay && it.isNotEmpty() && !CallLinks.hasUrl(it) }
    }

    private const val TAG = "BentoBar"
    private lateinit var app: Context
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var events: List<Event> = emptyList(); private set
    @Volatile private var loadedAt = 0L
    private var observing = false

    fun init(context: Context) { app = context.applicationContext }

    fun allowed() = app.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        Uses.on(Uses.CALENDAR)

    /** Reloads at most once a minute, or right away after the provider changes. */
    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - loadedAt < 60_000) return
        loadedAt = now
        if (!allowed()) { events = emptyList(); return }
        observe()
        io.execute { events = load(now) }
    }

    private fun observe() {
        if (observing) return
        observing = true
        app.contentResolver.registerContentObserver(CalendarContract.Instances.CONTENT_URI, true,
            object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) { refresh(force = true) }
            })
    }

    private fun load(now: Long): List<Event> = try {
        val begin = now - 36 * 3_600_000L
        val end = now + 40 * 86_400_000L
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS, CalendarContract.Instances.VISIBLE,
            CalendarContract.Instances.CALENDAR_ACCESS_LEVEL, CalendarContract.Instances.OWNER_ACCOUNT,
        )
        val out = ArrayList<Event>()
        // The calendar owner of each event, for telling the user apart from other attendees.
        val owners = HashMap<Long, String>()
        CalendarContract.Instances.query(app.contentResolver, projection, begin, end)?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(9) == 0) continue // calendar hidden by the user
                if (c.getInt(8) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                val location = c.getString(6).orEmpty()
                val text = location + "\n" + c.getString(7).orEmpty()
                // Tasks synced in from a task app (Todoist links each one, open or done) aren't events:
                // they show nowhere, not in the agenda, the month view, Next meeting or the chip.
                if (CallLinks.isTask(text)) continue
                val allDay = c.getInt(4) != 0
                val id = c.getLong(0)
                c.getString(11)?.let { owners[id] = it }
                out += Event(
                    eventId = id, title = c.getString(1).orEmpty().ifBlank { app.getString(R.string.calendar_no_title) },
                    begin = c.getLong(2), end = c.getLong(3), allDay = allDay,
                    color = c.getInt(5), location = location,
                    editable = c.getInt(10) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    meeting = false, link = CallLinks.find(text),
                )
            }
        }
        // Without a call link, a meeting needs someone else invited. Attendees are read only for the
        // candidates before the meeting horizon, once per load (not per tick). The horizon is taken a
        // few minutes ahead: it moves at midnight, and the next load can be up to a minute away.
        val zone = ZoneId.systemDefault()
        val horizon = Meetings.horizon(now + 5 * 60_000L, zone)
        val candidates = out.filter { it.link == null && !it.allDay && it.editable && Meetings.inHorizon(it.begin, it.end, now, horizon) }
            .mapTo(HashSet()) { it.eventId }
        val withOthers = runCatching { withOthers(candidates, owners) }
            .onFailure { Log.w(TAG, "attendee query failed", it) }.getOrDefault(emptySet())
        out.map { e ->
            val meeting = Meetings.isMeeting(e.allDay, e.editable, e.link != null, e.eventId in withOthers)
            if (meeting) e.copy(meeting = true) else e
        }.sortedBy { it.begin }
    } catch (e: Exception) {
        Log.w(TAG, "calendar query failed", e)
        emptyList()
    }

    /**
     * Of [ids], the events with at least one attendee who isn't the user. The user is an attendee
     * whose email is the event's calendar owner ([owners]), or the organizer when that's one of the
     * user's own accounts (the owners of calendars they own).
     */
    private fun withOthers(ids: Set<Long>, owners: Map<Long, String>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val mine = HashSet<String>()
        app.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars.OWNER_ACCOUNT),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_OWNER}", null, null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let { mine += it.lowercase(Locale.ROOT) }
        }
        val found = HashSet<Long>()
        app.contentResolver.query(CalendarContract.Attendees.CONTENT_URI,
            arrayOf(CalendarContract.Attendees.EVENT_ID, CalendarContract.Attendees.ATTENDEE_EMAIL, CalendarContract.Attendees.ATTENDEE_RELATIONSHIP),
            "${CalendarContract.Attendees.EVENT_ID} IN (${ids.joinToString(",")})", null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id in found) continue
                val email = c.getString(1).orEmpty().trim().lowercase(Locale.ROOT)
                if (email.isEmpty()) continue // no one to tell apart (and no one to meet)
                val isUser = email == owners[id]?.lowercase(Locale.ROOT) ||
                    (c.getInt(2) == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER && email in mine)
                if (!isUser) found += id
            }
        }
        return found
    }

    /**
     * Meetings (see [Event.meeting]) on now or starting before the horizon ([Meetings.horizon]: 03:00
     * tomorrow). The Next meeting item and the chip use only these.
     */
    fun meetings(now: Long): List<Event> {
        val horizon = Meetings.horizon(now, ZoneId.systemDefault())
        return events.filter { it.meeting && Meetings.inHorizon(it.begin, it.end, now, horizon) }
    }

    fun current(now: Long) = meetings(now).firstOrNull { it.begin <= now }
    fun next(now: Long) = meetings(now).firstOrNull { it.begin > now }

    /** Events overlapping the local day that starts at [dayStart] (all-day events use UTC dates). */
    fun on(dayStart: Long, dayEnd: Long, utcDayStart: Long): List<Event> = events.filter {
        if (it.allDay) it.begin <= utcDayStart && it.end > utcDayStart else it.begin < dayEnd && it.end > dayStart
    }

    fun open(e: Event) = Env.launch(Intent(Intent.ACTION_VIEW,
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end))

    fun openDay(millis: Long) = Env.launch(Intent(Intent.ACTION_VIEW,
        CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(millis.toString()).build()))

    fun join(e: Event) = e.link?.let { Env.launch(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } ?: false

    /** Google Maps (or any map app) searching for the event's place; the Maps website if none handles geo:. */
    fun directions(e: Event): Boolean {
        val q = Uri.encode(e.place ?: return false)
        return Env.launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")), quiet = true) ||
            Env.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$q")))
    }
}
