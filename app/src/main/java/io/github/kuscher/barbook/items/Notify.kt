package io.github.kuscher.barbook.items

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import io.github.kuscher.barbook.data.ChipMode
import io.github.kuscher.barbook.data.Store
import io.github.kuscher.barbook.ui.MainActivity
import io.github.kuscher.barbook.util.Fmt
import io.github.kuscher.barbook.util.Sym
import java.text.DateFormat
import java.util.Date

/** Notification channels and the timer-finished alert. */
object Notify {
    const val ALERTS = "alerts"
    const val LIVE = "live"
    const val ID_DONE = 10
    const val ID_CHIP = 20

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(ALERTS, "Timer alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When a timer or Pomodoro round ends"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        })
        nm.createNotificationChannel(NotificationChannel(LIVE, "Live Update chip", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "The running timer or next meeting as a chip in the status bar"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        })
    }

    fun allowed(context: Context) =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun timerDone(context: Context, title: String, text: String) {
        if (!allowed(context)) return
        channels(context)
        val n = Notification.Builder(context, ALERTS)
            .setSmallIcon(Glyphs.icon(Sym.TIMER))
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE))
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(ID_DONE, n)
    }
}

/** Material Symbols drawn into bitmaps, for notification icons (which must be plain alpha shapes). */
object Glyphs {
    private val cache = HashMap<String, Icon>()
    private var typeface: Typeface? = null

    fun bitmap(sym: String, px: Int, color: Int = Color.WHITE): Bitmap {
        val tf = typeface ?: Typeface.createFromAsset(Env.app.assets, "fonts/MaterialSymbolsRounded_Fill.ttf").also { typeface = it }
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = tf; textSize = px.toFloat(); this.color = color; textAlign = Paint.Align.CENTER }
        val fm = p.fontMetrics
        Canvas(b).drawText(sym, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, p)
        return b
    }

    fun icon(sym: String): Icon = synchronized(cache) { cache.getOrPut(sym) { Icon.createWithBitmap(bitmap(sym, 96)) } }
}

/**
 * The Live Update chip: Android shows ONE promoted notification per app as a chip left of the
 * system icons (verified on Googlebook OS; longer than ~7 characters, the text is dropped). BarBook
 * uses it for the running timer, else a meeting that is on or about to start.
 */
object Chips {
    fun update(context: Context) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        val cfg = Store.config.value
        val barUp = Env.service != null && cfg.enabled
        val wanted = when (cfg.chipMode) {
            ChipMode.OFF -> false
            ChipMode.FALLBACK -> !barUp
            ChipMode.ALWAYS -> true
        }
        val n = if (wanted && Notify.allowed(app)) build(app) else null
        if (n == null) nm.cancel(Notify.ID_CHIP) else { Notify.channels(app); nm.notify(Notify.ID_CHIP, n) }
    }

    private fun build(context: Context): Notification? {
        val t = Timers.state.value
        if (t != null) return timer(context, t)
        val cfg = Store.config.value
        if (cfg.items.none { it.type == "event" && it.section != io.github.kuscher.barbook.data.Section.OFF }) return null
        val now = System.currentTimeMillis()
        val e = Calendar.current(now) ?: Calendar.next(now)?.takeIf { it.begin - now <= 15 * 60_000 } ?: return null
        return event(context, e, now)
    }

    private fun base(context: Context, sym: String, title: String, text: String) =
        Notification.Builder(context, Notify.LIVE)
            .setSmallIcon(Glyphs.icon(sym))
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE))

    private fun action(context: Context, sym: String, label: String, cmd: String) =
        Notification.Action.Builder(Glyphs.icon(sym), label, PendingIntent.getBroadcast(context, cmd.hashCode(),
            Intent(context, ChipAction::class.java).setAction(cmd), PendingIntent.FLAG_IMMUTABLE)).build()

    private fun timer(context: Context, t: Timers.State): Notification {
        val now = Timers.now()
        val title = when (t.mode) {
            Timers.Mode.STOPWATCH -> "Stopwatch"
            Timers.Mode.POMODORO -> if (t.phase == Timers.Phase.WORK) "Focus · round ${t.round + 1}" else "Break"
            Timers.Mode.TIMER -> t.label.ifBlank { "Timer" }
        }
        val b = base(context, if (t.mode == Timers.Mode.STOPWATCH) Sym.AVG_PACE else Sym.TIMER, title,
            when {
                !t.running -> "Paused"
                t.mode == Timers.Mode.STOPWATCH -> "Started at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t.at))}"
                else -> "Ends at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t.at))}"
            })
        if (t.running) {
            b.setWhen(t.at).setShowWhen(true).setUsesChronometer(true)
                .setChronometerCountDown(t.mode != Timers.Mode.STOPWATCH)
        } else {
            b.setShortCriticalText(Fmt.clock(if (t.mode == Timers.Mode.STOPWATCH) t.pausedMs else t.pausedMs))
        }
        b.addAction(action(context, if (t.running) Sym.PAUSE else Sym.PLAY_ARROW, if (t.running) "Pause" else "Resume", ChipAction.TOGGLE))
        if (t.mode != Timers.Mode.STOPWATCH) b.addAction(action(context, Sym.ADD, "+1 min", ChipAction.PLUS))
        b.addAction(action(context, Sym.STOP, "Stop", ChipAction.STOP))
        return b.build()
    }

    private fun event(context: Context, e: Calendar.Event, now: Long): Notification {
        val on = e.begin <= now
        val b = base(context, Sym.EVENT, e.title,
            if (on) "Ends at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.end))}"
            else "Starts at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.begin))}")
            .setWhen(if (on) e.end else e.begin).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        if (e.link != null) b.addAction(action(context, Sym.VIDEOCAM, "Join", ChipAction.JOIN))
        b.addAction(action(context, Sym.OPEN_IN_NEW, "Open", ChipAction.OPEN_EVENT))
        return b.build()
    }
}

/** Buttons on the Live Update chip's card. */
class ChipAction : BroadcastReceiver() {
    companion object {
        const val TOGGLE = "io.github.kuscher.barbook.chip.TOGGLE"
        const val PLUS = "io.github.kuscher.barbook.chip.PLUS"
        const val STOP = "io.github.kuscher.barbook.chip.STOP"
        const val JOIN = "io.github.kuscher.barbook.chip.JOIN"
        const val OPEN_EVENT = "io.github.kuscher.barbook.chip.OPEN_EVENT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Env.init(context)
        val now = System.currentTimeMillis()
        val e = Calendar.current(now) ?: Calendar.next(now)
        when (intent.action) {
            TOGGLE -> Timers.toggle()
            PLUS -> Timers.add(60_000)
            STOP -> Timers.stop()
            JOIN -> e?.let { Calendar.join(it) }
            OPEN_EVENT -> e?.let { Calendar.open(it) }
        }
    }
}
