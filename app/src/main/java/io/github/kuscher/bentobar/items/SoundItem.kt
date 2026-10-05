package io.github.kuscher.bentobar.items

import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.ui.MediaButtons
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.SectionLabel
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon

// The Sound item, in a file of its own (it was in ToolItems.kt).

object SoundItem : ItemType("sound", R.string.item_sound_title, Sym.VOLUME_UP, R.string.item_sound_desc) {
    private fun am() = Env.app.getSystemService(AudioManager::class.java)

    override fun state(item: ItemConfig): ItemState {
        val am = am() ?: return ItemState(icon = Sym.VOLUME_UP)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val v = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val muted = am.isStreamMute(AudioManager.STREAM_MUSIC) || v == 0
        val pct = v * 100 / max
        return ItemState(icon = when { muted -> Sym.VOLUME_OFF; pct < 34 -> Sym.VOLUME_MUTE; pct < 67 -> Sym.VOLUME_DOWN; else -> Sym.VOLUME_UP },
            text = if (muted) Env.str(R.string.sound_muted) else "$pct%", widthKey = if (muted) null else "pct",
            desc = if (muted) Env.str(R.string.sound_muted_desc) else Env.plural(R.plurals.sound_volume_desc, pct))
    }

    override val usesWheel = true
    override fun onScroll(item: ItemConfig, steps: Int) {
        am()?.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
    }

    private fun mediaKey(code: Int) {
        val am = am() ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { _, host ->
        rememberTick()
        val am = am()
        if (am != null) {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            // Follows the volume keys and the scroll wheel while the menu is open, except mid-drag.
            val actual = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            var dragging by remember { mutableStateOf(false) }
            var v by remember { mutableFloatStateOf(actual) }
            LaunchedEffect(actual) { if (!dragging) v = actual }
            val muted = am.isStreamMute(AudioManager.STREAM_MUSIC)
            val volumeLabel = stringResource(R.string.sound_media_volume)
            MenuCard(Sym.VOLUME_UP, stringResource(R.string.item_sound_title),
                if (muted) stringResource(R.string.sound_muted) else stringResource(R.string.sound_media_volume_pct, (v * 100 / max).toInt())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                    }) { SymIcon(if (muted) Sym.VOLUME_OFF else Sym.VOLUME_UP, size = 20.sp, contentDescription = stringResource(if (muted) R.string.sound_unmute else R.string.sound_mute)) }
                    Spacer(Modifier.width(8.dp))
                    Slider(value = v, onValueChange = {
                        dragging = true
                        v = it
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                    }, onValueChangeFinished = { dragging = false }, valueRange = 0f..max.toFloat(), steps = (max - 1).coerceAtLeast(0),
                        modifier = Modifier.weight(1f).semantics { contentDescription = volumeLabel })
                }
                SectionLabel(stringResource(R.string.sound_media))
                MediaButtons(playing = am.isMusicActive, onPrevious = { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                    onPlayPause = { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, onNext = { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) })
                MenuDivider()
                MenuEntry(Sym.TUNE, stringResource(R.string.sound_all_volumes)) { host.close(); Env.launch(Intent(Settings.Panel.ACTION_VOLUME)) }
                MenuEntry(Sym.SETTINGS, stringResource(R.string.sound_settings)) { host.close(); Env.launch(Intent(Settings.ACTION_SOUND_SETTINGS)) }
            }
        }
    }
}
