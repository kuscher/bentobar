package io.github.kuscher.bentobar.items

import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.ui.ChoiceRow
import io.github.kuscher.bentobar.ui.MediaButtons
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.MenuNote
import io.github.kuscher.bentobar.ui.Meter
import io.github.kuscher.bentobar.ui.SectionLabel
import io.github.kuscher.bentobar.ui.Setup
import io.github.kuscher.bentobar.ui.SliderRow
import io.github.kuscher.bentobar.ui.SmallIconButton
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.Now
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Now playing's menu and its options in settings. What each state shows is MediaText's (pure, tested); this draws it.

/**
 * The menu: what plays, where it stands, previous, play or pause and next, the other players, and
 * the way to the player's own window. Without notification access it is the three buttons, sent as
 * media keys, and the words about what access adds.
 *
 * It is drawn from the source's value as it is now, so when access is taken away or a player closes
 * while it is open, it becomes that state's menu (within the two seconds the source needs) and stays
 * open. Nothing here holds on to a player: every control names it by its key, which the source
 * simply doesn't find any more.
 */
@Composable
internal fun MediaMenu(item: ItemConfig, host: MenuHost) {
    val now by NowPlaying.state.collectAsState()
    rememberTick() // "Paused" ends by the clock, and whether access is on is asked again with each tick
    val words = MediaItem.words
    val main = now.main
    val track = main?.let(MediaItem::track)
    val view = MediaText.menu(now.access, now.starting, MediaItem.granted(), MediaItem.phase(item, now), track,
        main?.let { MediaText.Controls(it.playing, it.durationMs, it.canPrevious, it.canPlayPause, it.canNext) }, words)
    // The first player, where the menu is about one.
    val player = main?.takeIf { view.player }
    MenuCard(Sym.MUSIC_NOTE, stringResource(R.string.item_media_title), view.subtitle) {
        if (player != null && track != null) {
            if (view.track) TrackBlock(player.art, track.title, track.artist, track.album, MediaText.spoken(track, words))
            // A player that says nothing about what it plays: one line, on the plain tile.
            else if (view.line != null) TrackBlock(null, view.line, "", "", view.line)
            if (view.position) {
                key(player.key) { PositionBar(player) }
                Spacer(Modifier.height(8.dp))
            }
        }
        MediaButtons(playing = view.playing, canPrevious = view.canPrevious, canPlayPause = view.canPlayPause, canNext = view.canNext,
            arrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            onPrevious = { NowPlaying.previous() }, onPlayPause = { NowPlaying.playPause() }, onNext = { NowPlaying.next() })
        if (player != null && track != null) {
            Spacer(Modifier.height(4.dp))
            val others = now.others
            if (others.isNotEmpty()) {
                SectionLabel(stringResource(R.string.media_other_players))
                others.forEach { other -> key(other.key) { OtherPlayer(other, host) } }
            }
            MenuDivider()
            MenuEntry(Sym.OPEN_IN_NEW, MediaText.open(track, words)) { open(null, host) }
        }
        if (view.consent) {
            // Without notification access the item knows that something plays, not what: say what access adds, then offer it.
            MenuDivider()
            Text(stringResource(R.string.media_access_lead), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            MenuNote(stringResource(R.string.media_access_explain))
            if (remember { Env.sideloaded() }) MenuNote(stringResource(R.string.media_access_restricted))
            MenuEntry(Sym.TOGGLE_ON, stringResource(R.string.media_access_allow)) { host.close(); NowPlaying.openAccessSettings() }
        }
    }
}

/** Opens a player's own window ([key]; null: the first player's) and closes the menu; says so when nothing can open it. */
private fun open(key: String?, host: MenuHost) {
    val opened = NowPlaying.open(key)
    host.close()
    if (!opened) Toast.makeText(Env.app, Env.str(R.string.toast_nothing_can_open), Toast.LENGTH_SHORT).show()
}

/**
 * What is playing: the artwork, or a plain tile with a note while there is none (none given, or
 * still being made small), and up to three lines. A line that is empty is left out. A title may be
 * right-to-left, so each line takes its direction from its own text. Read as one sentence
 * ([description]); not a stop for the keyboard.
 */
@Composable
internal fun TrackBlock(art: Bitmap?, title: String, artist: String, album: String, description: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(12.dp)
        // The border is for a white cover on the light card, which would have no edge.
        Box(Modifier.size(56.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
            Crossfade(art, animationSpec = io.github.kuscher.bentobar.ui.Motion.spec(tween<Float>(150)), label = "artwork") { picture ->
                if (picture == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SymIcon(Sym.MUSIC_NOTE, size = 24.sp, filled = true, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Image(remember(picture) { picture.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (artist.isNotEmpty()) Text(artist, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (album.isNotEmpty()) Text(album, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** How long the bar stays where it was dragged to while the player hasn't got there: after that, a player that never goes is believed. */
private const val SOUGHT_MS = 2_000L

/**
 * Where the track stands, between its elapsed time and its length; for a player that can seek, a
 * slider to move it with (Material's, so the keyboard and a screen reader can too). The bar moves on
 * with the tick, in steps. While it is dragged the elapsed time follows the thumb, and the player is
 * told once, on release. Only drawn for a track whose length is known.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PositionBar(session: NowPlaying.Session) {
    // The position moves by the clock, so the tick is read here: with the same player as a second ago, a redraw of
    // the menu around it would pass this bar by, and it would stand still until the player said something.
    rememberTick()
    // Never drawn for a track without a length; held above nothing all the same, so a range can't be empty.
    val length = session.durationMs.coerceAtLeast(1)
    var dragged by remember(length) { mutableStateOf<Float?>(null) }
    // Where the player was sent on release: the bar stays there until the player is (MediaText.position).
    var sought by remember(length) { mutableStateOf<Long?>(null) }
    LaunchedEffect(sought) { if (sought != null) { delay(SOUGHT_MS); sought = null } }
    val shown = MediaText.position(session.position(Now.elapsed()), dragged?.toLong(), sought, length)
    val label = stringResource(R.string.media_position)
    val state = MediaText.positionState(shown, length, MediaItem.words)
    Column(Modifier.fillMaxWidth()) {
        if (session.canSeek) {
            val source = remember { MutableInteractionSource() }
            val focused by source.collectIsFocusedAsState()
            val ring = RoundedCornerShape(10.dp)
            // 32 dp high, all of it taking the pointer. Material's own minimum of 48 would push the buttons below apart, so
            // it is switched off here; the slider is then as high as its thumb's slot, which is why that slot is 32 dp high
            // around a 16 dp bar (with a 16 dp slot only the middle half of the 32 dp would take the pointer).
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Slider(
                    value = shown.toFloat(),
                    onValueChange = { dragged = it },
                    onValueChangeFinished = {
                        dragged?.let { to -> sought = to.toLong(); NowPlaying.seekTo(session.key, to.toLong()) }
                        dragged = null
                    },
                    valueRange = 0f..length.toFloat(),
                    interactionSource = source,
                    // The menus' ring for what the keyboard is on: with a thumb of its own, Material's slider shows none.
                    modifier = Modifier.fillMaxWidth().height(32.dp)
                        .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, ring) else Modifier)
                        .semantics { contentDescription = label; stateDescription = state },
                    track = {
                        SliderDefaults.Track(it, modifier = Modifier.height(6.dp), drawStopIndicator = null, drawTick = { _, _ -> }, thumbTrackGapSize = 0.dp,
                            colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest))
                    },
                    thumb = {
                        Box(Modifier.size(width = 4.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(width = 4.dp, height = 16.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                        }
                    },
                )
            }
        } else {
            val fraction = MediaText.fraction(shown, length)
            Box(Modifier.fillMaxWidth().semantics {
                contentDescription = label; stateDescription = state
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }) { Meter(fraction) }
        }
        // The two times are in what the bar itself says ("1:42 of 5:37"), so they are not read a second time.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {}) {
            val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
            Text(MediaText.time(shown), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(MediaText.time(length), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * One of the other players: its app's icon, its title (its app's name when it gives none), and a
 * button that plays or pauses that player and no other, with the menu staying open. A click on the
 * row opens the player and closes the menu.
 */
@Composable
private fun OtherPlayer(session: NowPlaying.Session, host: MenuHost) {
    val track = MediaItem.track(session)
    MenuEntry(Sym.MUSIC_NOTE, MediaText.other(track), image = appIcon(session.pkg), trailing = {
        SmallIconButton(if (session.playing) Sym.PAUSE else Sym.PLAY_ARROW, MediaText.otherButton(track, session.playing, MediaItem.words),
            enabled = session.canPlayPause) { NowPlaying.playPause(session.key) }
    }) { open(session.key, host) }
}

/** An app's icon for a row: null until it is drawn, or when the app gives none (the row then shows a note). */
@Composable
private fun appIcon(pkg: String): ImageBitmap? {
    val icon by produceState(MediaIcons.drawn(pkg), pkg) { if (value == null) value = withContext(Dispatchers.Default) { MediaIcons.draw(pkg) } }
    return icon
}

/** The players' app icons, drawn small once for each app, off the main thread. An app's icon says nothing of what it plays. */
private object MediaIcons {
    /** 20 dp at up to three times the density. */
    private const val PX = 60
    private val icons = HashMap<String, ImageBitmap>()

    fun drawn(pkg: String): ImageBitmap? = synchronized(icons) { icons[pkg] }

    fun draw(pkg: String): ImageBitmap? = runCatching {
        val icon = Env.app.packageManager.getApplicationIcon(pkg)
        val bitmap = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, PX, PX)
        icon.draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    }.getOrNull()?.also { synchronized(icons) { icons[pkg] = it } }
}

/**
 * The item's own settings: what of a track the bar names, how long that may be, and what a click
 * does. Titles need notification access, and "Show" says so while it is missing.
 */
@Composable
internal fun MediaOptions(item: ItemConfig, set: (ItemConfig) -> Unit) {
    // Observed, not asked while drawing: Android's switch is flipped in a window of its own while this one stays up.
    val setup by Setup.state.collectAsState()
    val resources = LocalResources.current
    ChoiceRow(stringResource(R.string.option_show), listOf(MediaText.Show.TITLE to stringResource(R.string.media_show_title),
        MediaText.Show.BOTH to stringResource(R.string.media_show_both), MediaText.Show.ARTIST to stringResource(R.string.media_show_artist)),
        MediaText.show(item.options), help = if (setup.mediaAccess) null else stringResource(R.string.media_show_needs_access)) {
        set(item.with(MediaText.OPTION_SHOW, it.id))
    }
    SliderRow(stringResource(R.string.event_longest_title), MediaText.chars(item.options), MediaText.CHARS,
        { resources.getQuantityString(R.plurals.event_characters, it, it) }) { set(item.with(MediaText.OPTION_CHARS, it.toString())) }
    ChoiceRow(stringResource(R.string.media_click), listOf(MediaText.CLICK_MENU to stringResource(R.string.media_click_menu),
        MediaText.CLICK_TOGGLE to stringResource(R.string.media_click_toggle)),
        if (MediaText.toggles(item.options)) MediaText.CLICK_TOGGLE else MediaText.CLICK_MENU) { set(item.with(MediaText.OPTION_CLICK, it)) }
}
