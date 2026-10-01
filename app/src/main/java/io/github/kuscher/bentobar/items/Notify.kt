package io.github.kuscher.bentobar.items

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
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ChipMode
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.ui.MainActivity
import io.github.kuscher.bentobar.util.Fmt
import io.github.kuscher.bentobar.util.Sym
import android.text.format.DateFormat
import java.util.Date

/** Notification channels and the timer-finished alert. */
object Notify {
    const val ALERTS = "alerts"
    const val LIVE = "live"
    const val ID_DONE = 10
    const val ID_CHIP = 20

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_alerts_desc)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        })
        nm.createNotificationChannel(NotificationChannel(LIVE, context.getString(R.string.channel_live), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.channel_live_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        })
    }

    fun allowed(context: Context) =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            io.github.kuscher.bentobar.data.Uses.on(io.github.kuscher.bentobar.data.Uses.NOTIFICATIONS)

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

    /**
     * Notification icons. Resource icons where BentoBar ships the glyph as a vector drawable: the
     * status bar's Live Update chip only appears for those (verified on Googlebook OS; a bitmap
     * icon gave a promoted notification but no chip).
     */
    fun icon(sym: String): Icon = synchronized(cache) {
        cache.getOrPut(sym) {
            val res = drawables[sym]
            if (res != null) Icon.createWithResource(Env.app, res) else Icon.createWithBitmap(bitmap(sym, 96))
        }
    }

    private val drawables = mapOf(
        Sym.TIMER to io.github.kuscher.bentobar.R.drawable.sym_timer,
        Sym.AVG_PACE to io.github.kuscher.bentobar.R.drawable.sym_avg_pace,
        Sym.EVENT to io.github.kuscher.bentobar.R.drawable.sym_event,
        Sym.COFFEE to io.github.kuscher.bentobar.R.drawable.sym_coffee,
        Sym.PAUSE to io.github.kuscher.bentobar.R.drawable.sym_pause,
        Sym.PLAY_ARROW to io.github.kuscher.bentobar.R.drawable.sym_play_arrow,
        Sym.ADD to io.github.kuscher.bentobar.R.drawable.sym_add,
        Sym.STOP to io.github.kuscher.bentobar.R.drawable.sym_stop,
        Sym.VIDEOCAM to io.github.kuscher.bentobar.R.drawable.sym_videocam,
        Sym.OPEN_IN_NEW to io.github.kuscher.bentobar.R.drawable.sym_open_in_new,
    )
}

/**
 * The Live Update chip: Android shows ONE promoted notification per app as a chip left of the
 * system icons (verified on Googlebook OS; longer than ~7 characters, the text is dropped). BentoBar
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
        if (cfg.items.none { it.type == "event" && it.section != io.github.kuscher.bentobar.data.Section.OFF }) return null
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
            Timers.Mode.STOPWATCH -> context.getString(R.string.timer_stopwatch)
            Timers.Mode.POMODORO -> if (t.phase == Timers.Phase.WORK) context.getString(R.string.chip_focus_round, t.round + 1) else context.getString(R.string.timer_break)
            Timers.Mode.TIMER -> t.label.ifBlank { context.getString(R.string.item_timer_title) }
        }
        // The system's time format: 12/24-hour as set in Android, not just the locale's default.
        val time = DateFormat.getTimeFormat(context)
        val b = base(context, if (t.mode == Timers.Mode.STOPWATCH) Sym.AVG_PACE else Sym.TIMER, title,
            when {
                !t.running -> context.getString(R.string.common_paused)
                t.mode == Timers.Mode.STOPWATCH -> context.getString(R.string.chip_started_at, time.format(Date(t.at)))
                else -> context.getString(R.string.common_ends_at, time.format(Date(t.at)))
            })
        if (t.running) {
            b.setWhen(t.at).setShowWhen(true).setUsesChronometer(true)
                .setChronometerCountDown(t.mode != Timers.Mode.STOPWATCH)
        } else {
            b.setShortCriticalText(Fmt.clock(if (t.mode == Timers.Mode.STOPWATCH) t.pausedMs else t.pausedMs))
        }
        b.addAction(action(context, if (t.running) Sym.PAUSE else Sym.PLAY_ARROW,
            context.getString(if (t.running) R.string.common_pause else R.string.common_resume), ChipAction.TOGGLE))
        if (t.mode != Timers.Mode.STOPWATCH) b.addAction(action(context, Sym.ADD, context.getString(R.string.common_plus_one_min), ChipAction.PLUS))
        b.addAction(action(context, Sym.STOP, context.getString(R.string.chip_stop), ChipAction.STOP))
        return b.build()
    }

    private fun event(context: Context, e: Calendar.Event, now: Long): Notification {
        val on = e.begin <= now
        val time = DateFormat.getTimeFormat(context)
        val b = base(context, Sym.EVENT, e.title,
            if (on) context.getString(R.string.common_ends_at, time.format(Date(e.end)))
            else context.getString(R.string.chip_starts_at, time.format(Date(e.begin))))
            .setWhen(if (on) e.end else e.begin).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        if (e.link != null) b.addAction(action(context, Sym.VIDEOCAM, context.getString(R.string.common_join), ChipAction.JOIN))
        b.addAction(action(context, Sym.OPEN_IN_NEW, context.getString(R.string.common_open), ChipAction.OPEN_EVENT))
        return b.build()
    }
}

/** Buttons on the Live Update chip's card. */
class ChipAction : BroadcastReceiver() {
    companion object {
        const val TOGGLE = "io.github.kuscher.bentobar.chip.TOGGLE"
        const val PLUS = "io.github.kuscher.bentobar.chip.PLUS"
        const val STOP = "io.github.kuscher.bentobar.chip.STOP"
        const val JOIN = "io.github.kuscher.bentobar.chip.JOIN"
        const val OPEN_EVENT = "io.github.kuscher.bentobar.chip.OPEN_EVENT"
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
