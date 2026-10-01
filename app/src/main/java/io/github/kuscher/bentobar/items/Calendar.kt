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
import java.util.concurrent.Executors

/** Upcoming calendar events from the Calendar provider (needs READ_CALENDAR). */
object Calendar {
    data class Event(
        val eventId: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val color: Int,
        val location: String,
        /**
         * A meeting: timed (not all-day), on a calendar the user can edit (contributor access or
         * more). Not: Todoist feeds, holidays, birthdays and other subscribed calendars.
         */
        val meeting: Boolean,
        /** A video-call link found in the location or description; meetings only. */
        val link: String?,
    ) {
        /** A place to get directions to: a meeting's location, unless that is itself a link. */
        val place: String? get() = location.trim().takeIf { meeting && it.isNotEmpty() && !CallLinks.hasUrl(it) }
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
            CalendarContract.Instances.CALENDAR_ACCESS_LEVEL,
        )
        val out = ArrayList<Event>()
        CalendarContract.Instances.query(app.contentResolver, projection, begin, end)?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(9) == 0) continue // calendar hidden by the user
                if (c.getInt(8) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                val location = c.getString(6).orEmpty()
                val allDay = c.getInt(4) != 0
                // Tasks synced in from a task app (Todoist links each one) aren't meetings either, and
                // a completed one (Todoist puts "✓" before its title) isn't shown at all.
                val task = CallLinks.isTask(location + "\n" + c.getString(7).orEmpty())
                if (task && c.getString(1).orEmpty().trimStart().startsWith("✓")) continue
                val meeting = !allDay && c.getInt(10) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR && !task
                out += Event(
                    eventId = c.getLong(0), title = c.getString(1).orEmpty().ifBlank { app.getString(R.string.calendar_no_title) },
                    begin = c.getLong(2), end = c.getLong(3), allDay = allDay,
                    color = c.getInt(5), location = location, meeting = meeting,
                    link = if (meeting) CallLinks.find(location + "\n" + c.getString(7).orEmpty()) else null,
                )
            }
        }
        out.sortedBy { it.begin }
    } catch (e: Exception) {
        Log.w(TAG, "calendar query failed", e)
        emptyList()
    }

    /** Meetings (see [Event.meeting]), current and upcoming. Next meeting and the chip use only these. */
    fun meetings(now: Long) = events.filter { it.meeting && it.end > now }

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
