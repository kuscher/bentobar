package io.github.kuscher.bentobar.items

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Online
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.items.FxRules.FxPair
import io.github.kuscher.bentobar.items.FxRules.PairQuote
import io.github.kuscher.bentobar.ui.ChoiceRow
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.MenuNote
import io.github.kuscher.bentobar.ui.SearchField
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon
import java.time.ZoneId
import kotlin.math.abs

// Menu and settings for the Exchange rates item. No logging.

private const val FRANKFURTER_SITE = "https://frankfurter.dev/"

@Composable
internal fun FxMenu(item: ItemConfig, host: MenuHost) {
    rememberTick()
    val online by Online.state.collectAsState()
    val on = Online.Service.FRANKFURTER in online.on
    val pairs = remember(item) { FxRules.pairs(item) }
    val title = stringResource(R.string.item_fx_title)
    LaunchedEffect(item.id, on) { FxItem.opened(item) }

    val table = FxItem.table(ask = false)
    when {
        !on && !FxItem.isStaged(item) -> MenuCard(Sym.SWAP_VERT, title, stringResource(if (Online.setUp(Online.Service.FRANKFURTER)) R.string.common_off_in_setup else R.string.common_not_set_up)) {
            MenuNote(stringResource(R.string.fx_consent))
            MenuEntry(Sym.TOGGLE_ON, stringResource(R.string.fx_turn_on)) {
                Online.turnOn(Online.Service.FRANKFURTER)
                Ticker.refresh(FxItem.type)
            }
        }
        pairs.isEmpty() -> MenuCard(Sym.SWAP_VERT, title, stringResource(R.string.fx_none_yet)) {
            MenuEntry(Sym.ADD, stringResource(R.string.fx_add_pairs)) { host.openItemSettings(item.id) }
        }
        table == null || (!table.hasRates && table.failure == null) -> MenuCard(Sym.SWAP_VERT, title, stringResource(R.string.usage_loading)) {
            MenuEntry(Sym.EDIT, stringResource(R.string.fx_edit)) { host.openItemSettings(item.id) }
        }
        !table.hasRates -> {
            val offline = table.failure == Failure.OFFLINE
            MenuCard(Sym.SWAP_VERT, title, stringResource(if (offline) R.string.common_no_connection else R.string.fx_desc_no_rates)) {
                MenuEntry(Sym.REFRESH, stringResource(R.string.common_try_again)) { FxItem.refresh() }
                MenuEntry(Sym.EDIT, stringResource(R.string.fx_edit)) { host.openItemSettings(item.id) }
            }
        }
        else -> {
            val quotes = FxRules.quotesFor(pairs, table)
            var amountText by remember { mutableStateOf("100") }
            val amount = FxRules.parseAmount(amountText)
            MenuCard(Sym.SWAP_VERT, title, table.date.ifEmpty { null }) {
                quotes.forEach { q ->
                    FxQuoteRow(q, amount)
                }
                Spacer(Modifier.height(4.dp))
                SearchField(
                    label = stringResource(R.string.fx_convert_label),
                    initial = amountText,
                    selectAll = true,
                    keyboard = KeyboardType.Decimal,
                    onChange = { amountText = it },
                    onEnter = { amountText = it },
                )
                val clock = WeatherItem.times().clock(table.fetchedAt, ZoneId.systemDefault())
                val updated = when (table.failure) {
                    Failure.OFFLINE -> stringResource(R.string.common_updated_offline, clock)
                    null -> stringResource(R.string.common_updated, clock)
                    else -> stringResource(R.string.common_updated_no_answer, clock)
                }
                MenuNote(stringResource(R.string.fx_note, updated))
                MenuDivider()
                MenuEntry(Sym.REFRESH, stringResource(R.string.common_refresh)) { FxItem.refresh() }
                MenuEntry(Sym.EDIT, stringResource(R.string.fx_edit)) { host.openItemSettings(item.id) }
                val site = stringResource(R.string.fx_site)
                val opens = stringResource(R.string.common_opens_browser, site)
                MenuEntry(Sym.OPEN_IN_NEW, site, modifier = Modifier.semantics { contentDescription = opens }) {
                    host.close()
                    Env.launch(Intent(Intent.ACTION_VIEW, Uri.parse(FRANKFURTER_SITE)))
                }
            }
        }
    }
}

@Composable
private fun FxQuoteRow(q: PairQuote, amount: Double?) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q.pair.code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(q.formattedRate, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium)
            if (abs(q.changePct) >= 0.005) {
                val sign = if (q.changePct > 0) "+" else "−"
                val color = if (q.changePct > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Text(
                    "  $sign${q.formattedPct}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = color,
                )
            }
        }
        if (amount != null) {
            val amtFmt = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
            Text(
                stringResource(R.string.fx_converted, amtFmt, q.pair.base, FxRules.formatConverted(amount, q.rate), q.pair.quote),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = quiet,
            )
        }
    }
}

@Composable
internal fun FxOptions(item: ItemConfig, set: (ItemConfig) -> Unit) {
    rememberTick()
    val online by Online.state.collectAsState()
    val on = Online.Service.FRANKFURTER in online.on
    key(item.id) {
        var invalid by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.fx_section), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.fx_consent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!on) {
                Spacer(Modifier.height(4.dp))
                MenuEntry(Sym.TOGGLE_ON, stringResource(R.string.fx_turn_on)) {
                    Online.turnOn(Online.Service.FRANKFURTER)
                    Ticker.refresh(FxItem.type)
                }
            }
            val pairs = remember(item) { FxRules.pairs(item) }
            if (pairs.size < FxRules.MOST_PAIRS) {
                SearchField(
                    label = stringResource(R.string.fx_add_label),
                    placeholder = "EUR/USD",
                    submit = stringResource(R.string.fx_add_button),
                    error = if (invalid) stringResource(R.string.fx_invalid_pair) else null,
                    onChange = { invalid = false },
                    onEnter = { text ->
                        val p = FxRules.parsePair(text)
                        if (p == null) invalid = true else {
                            invalid = false
                            Online.turnOn(Online.Service.FRANKFURTER)
                            set(FxRules.addPair(item, p))
                        }
                    },
                )
            } else {
                MenuNote(stringResource(R.string.fx_full))
            }
            if (pairs.isEmpty()) {
                MenuNote(stringResource(R.string.fx_none_yet))
            } else {
                pairs.forEachIndexed { i, p ->
                    FollowedPairRow(item, p, first = i == 0, last = i == pairs.lastIndex, set)
                }
            }
        }
    }
    val res = LocalContext.current.resources
    ChoiceRow(
        stringResource(R.string.fx_turn_label),
        FxRules.TURNS.map { sec ->
            sec to if (sec % 60 == 0) res.getQuantityString(R.plurals.common_minutes_short, sec / 60, sec / 60)
            else res.getQuantityString(R.plurals.stocks_seconds_short, sec, sec)
        },
        FxRules.turnSec(item),
        help = stringResource(R.string.fx_turn_help),
    ) { set(item.with("turnSec", it.takeIf { s -> s != FxRules.TURN_SEC }?.toString())) }
}

@Composable
private fun FollowedPairRow(item: ItemConfig, p: FxPair, first: Boolean, last: Boolean, set: (ItemConfig) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(p.code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        val up = stringResource(R.string.fx_move_up, p.code)
        val down = stringResource(R.string.fx_move_down, p.code)
        val remove = stringResource(R.string.fx_remove, p.code)
        IconButton(onClick = { set(FxRules.movePair(item, p, -1)) }, enabled = !first, modifier = Modifier.semantics { contentDescription = up }) {
            SymIcon(Sym.ARROW_UPWARD)
        }
        IconButton(onClick = { set(FxRules.movePair(item, p, 1)) }, enabled = !last, modifier = Modifier.semantics { contentDescription = down }) {
            SymIcon(Sym.ARROW_DOWNWARD)
        }
        IconButton(onClick = { set(FxRules.removePair(item, p)) }, modifier = Modifier.semantics { contentDescription = remove }) {
            SymIcon(Sym.CLOSE)
        }
    }
}
