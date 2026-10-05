package io.github.kuscher.bentobar.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.ui.MediaButtons
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.MenuNote
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.Now
import io.github.kuscher.bentobar.util.Sym

/**
 * Now playing: that something plays, and the controls for it. The source is [NowPlaying], which
 * runs only while an item of this type is live ([onLive], [onIdle]) and knows the players' titles
 * only with notification access; without it the item still shows that something plays and controls
 * it with the media keys.
 *
 * This is the first cut: the note while something plays (and for the rule's minutes after it
 * pauses), the three buttons in the menu, and the way to notification access. Titles in the bar, the
 * menu's artwork and position and the other players come next.
 */
object MediaItem : ItemType("media", R.string.item_media_title, Sym.MUSIC_NOTE, R.string.item_media_desc) {
    override val canBeActive = true
    override val addsWhenActive = true
    override val notificationAccess = true
    // A title is the user's own business: debug output says how long the text is, not what it says.
    override val discreet = true
    override val menuWidthDp = 320

    /** How long the item stays after playback pauses, in minutes: a pause for a call doesn't lose it. */
    private val linger = Threshold("lingerMin", 2, 1..10) { Env.plural(R.plurals.common_minutes_short, it, it) }
    override val trigger = Trigger(R.string.trigger_media, R.string.trigger_media_short, linger)

    /** [Now.elapsed] when something last played; 0: nothing has since the item went live. */
    @Volatile private var playedAt = 0L

    override fun onLive() { playedAt = 0; NowPlaying.start() }
    override fun onIdle() = NowPlaying.stop()
    override fun sample(now: Long) {
        NowPlaying.tick(now)
        if (NowPlaying.state.value.active) playedAt = now
    }

    /** Paused not long ago: within the rule's minutes of the last moment something played. */
    private fun paused(item: ItemConfig, playing: Boolean) =
        !playing && playedAt != 0L && Now.elapsed() - playedAt < linger.of(item) * 60_000L

    override fun state(item: ItemConfig): ItemState {
        val playing = NowPlaying.state.value.active
        val paused = paused(item, playing)
        // The note looks the same filled and outlined at this size, so "nothing playing" is a glyph of its own.
        return ItemState(icon = when { playing -> Sym.MUSIC_NOTE; paused -> Sym.PAUSE; else -> Sym.MUSIC_OFF }, active = playing || paused,
            desc = Env.str(when { playing -> R.string.media_playing; paused -> R.string.common_paused; else -> R.string.media_nothing }))
    }

    override val usesWheel = true
    override fun onScroll(item: ItemConfig, steps: Int) = NowPlaying.volume(steps)

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host ->
        val now by NowPlaying.state.collectAsState()
        rememberTick() // "Paused" ends by the clock
        val subtitle = when {
            now.starting -> stringResource(R.string.status_starting)
            now.active -> now.main?.app ?: stringResource(R.string.media_playing)
            paused(item, false) -> stringResource(R.string.common_paused)
            else -> stringResource(R.string.media_nothing)
        }
        MenuCard(Sym.MUSIC_NOTE, stringResource(R.string.item_media_title), subtitle) {
            val player = now.main
            MediaButtons(playing = now.active, canPrevious = player?.canPrevious ?: true, canNext = player?.canNext ?: true,
                arrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                onPrevious = { NowPlaying.previous() }, onPlayPause = { NowPlaying.playPause() }, onNext = { NowPlaying.next() })
            if (!now.access && !now.starting) {
                // Without notification access the item knows that something plays, not what: say what access adds.
                MenuDivider()
                Text(stringResource(R.string.media_access_lead), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                MenuNote(stringResource(R.string.media_access_explain))
                if (remember { Env.sideloaded() }) MenuNote(stringResource(R.string.media_access_restricted))
                MenuEntry(Sym.TOGGLE_ON, stringResource(R.string.media_access_allow)) { host.close(); NowPlaying.openAccessSettings() }
            }
        }
    }

    override fun debug(args: List<String>): String? = if (args.isEmpty()) NowPlaying.debugLine() else null
}
