package io.github.kuscher.barbook.items

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
        /** A video-call or web link found in the location or description. */
        val link: String?,
    )

    private const val TAG = "BarBook"
    private lateinit var app: Context
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var events: List<Event> = emptyList(); private set
    @Volatile private var loadedAt = 0L
    private var observing = false

    fun init(context: Context) { app = context.applicationContext }

    fun allowed() = app.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

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
        )
        val out = ArrayList<Event>()
        CalendarContract.Instances.query(app.contentResolver, projection, begin, end)?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(9) == 0) continue // calendar hidden by the user
                if (c.getInt(8) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                val location = c.getString(6).orEmpty()
                out += Event(
                    eventId = c.getLong(0), title = c.getString(1).orEmpty().ifBlank { "(No title)" },
                    begin = c.getLong(2), end = c.getLong(3), allDay = c.getInt(4) != 0,
                    color = c.getInt(5), location = location,
                    link = findLink(location + "\n" + c.getString(7).orEmpty()),
                )
            }
        }
        out.sortedBy { it.begin }
    } catch (e: Exception) {
        Log.w(TAG, "calendar query failed", e)
        emptyList()
    }

    private val urlRe = Regex("""https?://[^\s<>"')\]]+""")
    private val callHosts = listOf("meet.google.com", "zoom.us", "teams.microsoft.com", "teams.live.com", "webex.com", "whereby.com", "jit.si")

    private fun findLink(text: String): String? {
        val urls = urlRe.findAll(text).map { it.value.trimEnd('.', ',', ';') }.toList()
        return urls.firstOrNull { u -> callHosts.any { u.contains(it) } } ?: urls.firstOrNull()
    }

    /** Timed (not all-day) events, current and upcoming. */
    fun timed(now: Long) = events.filter { !it.allDay && it.end > now }

    fun current(now: Long) = timed(now).firstOrNull { it.begin <= now }
    fun next(now: Long) = timed(now).firstOrNull { it.begin > now }

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
}
