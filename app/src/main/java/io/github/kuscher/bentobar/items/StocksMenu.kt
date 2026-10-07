package io.github.kuscher.bentobar.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Online
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.ui.ChoiceRow
import io.github.kuscher.bentobar.ui.KeyWords
import io.github.kuscher.bentobar.ui.MenuCard
import io.github.kuscher.bentobar.ui.MenuDivider
import io.github.kuscher.bentobar.ui.MenuEntry
import io.github.kuscher.bentobar.ui.MenuNote
import io.github.kuscher.bentobar.ui.SearchField
import io.github.kuscher.bentobar.ui.SearchStatus
import io.github.kuscher.bentobar.ui.ServiceKey
import io.github.kuscher.bentobar.ui.rememberTick
import io.github.kuscher.bentobar.util.Now
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon

// The Stocks item's menu and settings, drawn from what StocksRules works out. Nothing here asks the
// service: a click that could goes through StocksItem, where the rules for that are.

/** The item's menu: one card for each state the item can be in. Redrawn with the tick, so the market's line and the prices follow. */
@Composable
internal fun StocksMenu(item: ItemConfig, host: MenuHost) {
    rememberTick()
    // The switch and the key can change under an open menu (Setup's window beside it).
    val online by Online.state.collectAsState()
    val title = stringResource(R.string.item_stocks_title)
    // On opening: while the market is open, quotes older than half a minute are asked again.
    LaunchedEffect(item.id, online) { StocksItem.opened(item) }
    when (StocksItem.status(item)) {
        StocksStatus.NoKey -> MenuCard(Sym.SHOW_CHART, title, stringResource(R.string.common_not_set_up)) {
            MenuNote(stringResource(R.string.stocks_consent))
            MenuEntry(Sym.KEY, stringResource(R.string.stocks_add_key)) { host.openItemSettings(item.id) }
        }
        StocksStatus.Off -> MenuCard(Sym.SHOW_CHART, title, stringResource(R.string.common_off_in_setup)) {
            MenuNote(stringResource(R.string.stocks_consent))
            MenuEntry(Sym.TOGGLE_ON, stringResource(R.string.stocks_turn_on)) { Online.turnOn(Online.Service.FINNHUB); Ticker.refresh() }
        }
        StocksStatus.NoStocks -> MenuCard(Sym.SHOW_CHART, title, stringResource(R.string.stocks_none_yet)) {
            MenuEntry(Sym.ADD, stringResource(R.string.stocks_add_stocks)) { host.openItemSettings(item.id) }
        }
        StocksStatus.Live -> {
            val v = StocksRules.menu(StocksItem.look(item), StocksItem.quotesFor(item, ask = false), Now.wall(), StocksItem.Words, StocksItem.times())
            MenuCard(Sym.SHOW_CHART, title, v.market) {
                v.lines.forEach { l -> StockRow(l.symbol, l.name.takeIf { it != l.symbol }, l.price, l.change, l.up, listOfNotNull(l.range, l.problem), l.desc) }
                v.note?.let { MenuNote(it) }
                MenuDivider()
                MenuEntry(Sym.REFRESH, stringResource(R.string.common_refresh)) { StocksItem.refresh(item) }
                MenuEntry(Sym.EDIT, stringResource(R.string.stocks_edit)) { host.openItemSettings(item.id) }
                val site = stringResource(R.string.stocks_site)
                val opens = stringResource(R.string.common_opens_browser, site)
                MenuEntry(Sym.OPEN_IN_NEW, site, modifier = Modifier.semantics { contentDescription = opens }) { host.close(); StocksItem.openSite() }
            }
        }
    }
}

/**
 * One stock: symbol and name on the left, price and the day's change on the right, and under them the
 * day's range and what went wrong, a line each. One node for a screen reader, which says it in a
 * sentence ([desc]).
 */
@Composable
private fun StockRow(symbol: String, name: String?, price: String?, change: String?, up: Boolean?, below: List<String>, desc: String) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clearAndSetSemantics { contentDescription = desc }) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(symbol, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                if (name != null) Text(name, style = MaterialTheme.typography.bodySmall, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price.orEmpty(), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium, maxLines = 1)
                // The sign says which way it went; the color only repeats it.
                if (change != null) Text(change, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), maxLines = 1,
                    color = when (up) { true -> MaterialTheme.colorScheme.primary; false -> MaterialTheme.colorScheme.error; null -> quiet })
            }
        }
        below.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = quiet, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

/** The item's settings: the key, the stocks (the search, and the list to order and prune), and how long each shows. */
@Composable
internal fun StocksOptions(item: ItemConfig, set: (ItemConfig) -> Unit) {
    rememberTick()
    val online by Online.state.collectAsState()
    val keyed = Online.Service.FINNHUB in online.keyed
    ServiceKey(keyed, STOCKS_KEY, onSave = { StocksItem.saveKey(it) }, onRemove = { StocksItem.removeKey() }, onGetKey = { StocksItem.openSignUp() },
        heading = stringResource(R.string.stocks_key_section))
    // One card serves whichever item is selected: nothing typed or found for one item stays for the next.
    key(item.id) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.stocks_section), style = MaterialTheme.typography.labelLarge)
            val stocks = StocksRules.stocks(item)
            if (keyed) {
                if (stocks.size < StocksRules.MOST_STOCKS) StockSearch(item, stocks, set)
                else MenuNote(stringResource(R.string.stocks_full))
            } else MenuNote(stringResource(R.string.stocks_need_key))
            if (stocks.isEmpty()) MenuNote(stringResource(R.string.stocks_none_added))
            else stocks.forEachIndexed { i, s -> FollowedRow(item, s, first = i == 0, last = i == stocks.lastIndex, set) }
        }
    }
    val res = LocalContext.current.resources
    ChoiceRow(stringResource(R.string.stocks_turn_label), StocksRules.TURNS.map { sec ->
        sec to if (sec % 60 == 0) res.getQuantityString(R.plurals.common_minutes_short, sec / 60, sec / 60) else res.getQuantityString(R.plurals.stocks_seconds_short, sec, sec)
    }, StocksRules.turnSec(item), help = stringResource(R.string.stocks_turn_help)) { set(item.with("turnSec", it.takeIf { s -> s != StocksRules.TURN_SEC }?.toString())) }
}

private val STOCKS_KEY = KeyWords(R.string.stocks_key_section, R.string.stocks_consent, R.string.stocks_get_key, R.string.stocks_save_key,
    R.string.stocks_key_help, R.string.stocks_key_saved, R.string.stocks_replace_key, R.string.stocks_remove_key, R.string.stocks_remove_key_help)

/**
 * The search: the field with its Search button, and under it what the last search found, each US
 * stock with a press to add it. Typing sends nothing; the button or Enter sends the text. An item in
 * Off may ask nothing. From the keyboard: when stocks arrive the focus is on the first that can be
 * added, so Enter, Enter is search and add; Down and Up go between the field and it.
 */
@Composable
private fun StockSearch(item: ItemConfig, stocks: List<Stock>, set: (ItemConfig) -> Unit) {
    val by = "settings:" + item.id
    val asked by StocksItem.search.state.collectAsState()
    val state = asked.shownTo { it.by == by }
    val field = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val found = ((state as? Ask.State.Done)?.answer as? StockFound.Listings)?.list.orEmpty()
    val firstFree = found.indexOfFirst { l -> stocks.none { it.symbol == l.symbol } }
    // What was found belongs to this field while it is drawn: gone with it.
    DisposableEffect(by) { onDispose { StocksItem.search.clearIf { it.by == by } } }
    LaunchedEffect(found) { if (firstFree >= 0) runCatching { first.requestFocus() } }
    Box(Modifier.onPreviewKeyEvent { e ->
        if (firstFree < 0 || e.key != Key.DirectionDown) false
        else { if (e.type == KeyEventType.KeyDown) runCatching { first.requestFocus() }; true }
    }) {
        SearchField(label = stringResource(R.string.stocks_search_label), submit = stringResource(R.string.common_search),
            canSubmit = item.section != Section.OFF, focus = field,
            onChange = { StocksItem.search.clearIf { it.by == by } },
            onEnter = { text -> StocksLoad.query(text)?.let { StocksItem.search.ask(StockQuery(it, by)) } })
    }
    when (state) {
        Ask.State.Idle -> {}
        is Ask.State.Busy -> SearchStatus(stringResource(R.string.stocks_searching))
        is Ask.State.Done -> when (val answer = state.answer) {
            StockFound.Unasked -> {}
            is StockFound.Failed -> SearchStatus(stringResource(when (answer.failure) {
                Finnhub.Failure.OFFLINE -> R.string.common_no_connection
                Finnhub.Failure.REFUSED -> R.string.stocks_key_refused
                Finnhub.Failure.SLOW_DOWN -> R.string.stocks_search_slow
                else -> R.string.stocks_search_no_answer
            }))
            is StockFound.Listings -> if (found.isEmpty()) SearchStatus(stringResource(R.string.stocks_nothing_found)) else found.forEachIndexed { i, l ->
                val following = stocks.any { it.symbol == l.symbol }
                MenuEntry(if (following) Sym.CHECK else Sym.ADD, l.symbol, sub = l.name, enabled = !following,
                    detail = if (following) stringResource(R.string.stocks_following) else null,
                    modifier = if (i != firstFree) Modifier else Modifier.focusRequester(first).onPreviewKeyEvent { e ->
                        if (e.key != Key.DirectionUp) false else { if (e.type == KeyEventType.KeyDown) runCatching { field.requestFocus() }; true }
                    }) {
                    set(StocksRules.add(item, l))
                    StocksItem.search.clear()
                    // The stock found goes, and the focus with it: back to the field, for the next search.
                    runCatching { field.requestFocus() }
                }
            }
        }
    }
}

/** One followed stock: its symbol and name, and moving or removing it. */
@Composable
private fun FollowedRow(item: ItemConfig, s: Stock, first: Boolean, last: Boolean, set: (ItemConfig) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.symbol, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
            if (s.name != s.symbol) Text(s.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val up = stringResource(R.string.stocks_move_up, s.symbol)
        val down = stringResource(R.string.stocks_move_down, s.symbol)
        val remove = stringResource(R.string.stocks_remove, s.symbol)
        IconButton(onClick = { set(StocksRules.move(item, s.symbol, -1)) }, enabled = !first, modifier = Modifier.semantics { contentDescription = up }) {
            SymIcon(Sym.ARROW_UPWARD)
        }
        IconButton(onClick = { set(StocksRules.move(item, s.symbol, 1)) }, enabled = !last, modifier = Modifier.semantics { contentDescription = down }) {
            SymIcon(Sym.ARROW_DOWNWARD)
        }
        IconButton(onClick = { set(StocksRules.remove(item, s.symbol)) }, modifier = Modifier.semantics { contentDescription = remove }) {
            SymIcon(Sym.CLOSE)
        }
    }
}
