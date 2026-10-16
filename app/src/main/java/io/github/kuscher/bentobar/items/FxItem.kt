package io.github.kuscher.bentobar.items

import androidx.compose.runtime.Composable
import io.github.kuscher.bentobar.R
import io.github.kuscher.bentobar.data.ItemConfig
import io.github.kuscher.bentobar.data.Online
import io.github.kuscher.bentobar.data.Section
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.items.FxRules.FxTable
import io.github.kuscher.bentobar.util.Now
import io.github.kuscher.bentobar.util.Sym
import kotlin.math.abs

/**
 * Exchange rates: reference foreign exchange rates from the European Central Bank via Frankfurter
 * (`api.frankfurter.dev`, no key required), rotating followed currency pairs in the bar and offering
 * an inline converter in its menu. No logging.
 */
object FxItem : ItemType("fx", R.string.item_fx_title, Sym.SWAP_VERT, R.string.item_fx_desc) {
    override val menuWidthDp = 340
    override val online = Online.Service.FRANKFURTER
    override val canBeActive = true

    private val move = Threshold("movePct", 1, 1..5) { "$it%" }
    override val trigger = Trigger(R.string.trigger_fx, R.string.trigger_fx_short, move)

    internal val rates: Refresher<Unit, FxTable> = Background.refresher(
        type = type,
        service = online,
        every = { _, t -> FxRules.every(t) },
        load = { _, last -> FxLoad.load(last, Now.wall()) },
    )

    @Volatile private var staged: FxTable? = null

    private fun firstShown(): ItemConfig? = Store.config.value.items.firstOrNull { it.type == type && it.section != Section.OFF }

    internal fun isStaged(item: ItemConfig): Boolean = staged != null && firstShown()?.id == item.id

    internal fun table(ask: Boolean = true): FxTable? {
        staged?.let { return it }
        if (!Online.on(online)) return null
        if (ask) rates.want(Unit)
        return rates.peek(Unit)
    }

    internal fun opened(item: ItemConfig) {
        if (isStaged(item) || item.section == Section.OFF || !Online.on(online)) return
        val t = rates.peek(Unit)
        if (t == null) rates.want(Unit) else if (t.failure == null) rates.refresh(Unit, floorMs = FxRules.MENU_MS)
    }

    internal fun refresh() {
        if (staged != null || !Online.on(online)) return
        rates.refresh(Unit, floorMs = 10_000L)
    }

    override fun state(item: ItemConfig): ItemState {
        val pairs = FxRules.pairs(item)
        if (!Online.on(online) && !isStaged(item)) {
            val d = Env.str(if (Online.setUp(online)) R.string.fx_desc_off else R.string.fx_desc_not_set_up)
            return ItemState(icon = Sym.SWAP_VERT, filled = false, text = null, desc = d, tooltip = d)
        }
        if (pairs.isEmpty()) {
            val d = Env.str(R.string.fx_none_yet)
            return ItemState(icon = Sym.SWAP_VERT, filled = false, text = null, desc = d, tooltip = d)
        }
        val t = table(ask = item.section != Section.OFF)
        if (t == null || !t.hasRates) {
            val d = Env.str(if (t?.failure != null) R.string.fx_desc_no_rates else R.string.fx_desc_loading)
            return ItemState(icon = Sym.SWAP_VERT, filled = false, text = null, desc = d, tooltip = d)
        }
        val quotes = FxRules.quotesFor(pairs, t)
        if (quotes.isEmpty()) {
            val d = Env.str(R.string.fx_desc_no_rates)
            return ItemState(icon = Sym.SWAP_VERT, filled = false, text = null, desc = d, tooltip = d)
        }
        val turns = quotes.map { q ->
            when {
                q.changePct >= 0.05 -> Env.str(R.string.fx_bar_up, q.pair.code, q.formattedRate, q.formattedPct)
                q.changePct <= -0.05 -> Env.str(R.string.fx_bar_down, q.pair.code, q.formattedRate, q.formattedPct)
                else -> Env.str(R.string.fx_bar_flat, q.pair.code, q.formattedRate)
            }
        }
        val turnSec = FxRules.turnSec(item)
        val idx = ((Now.wall() / 1000L) / turnSec).mod(turns.size)
        val currentQuote = quotes[idx]
        val icon = when {
            currentQuote.changePct >= 0.05 -> Sym.TRENDING_UP
            currentQuote.changePct <= -0.05 -> Sym.TRENDING_DOWN
            else -> Sym.SWAP_VERT
        }
        val threshold = move.shown(item).toDouble()
        val bigMove = quotes.any { abs(it.changePct) >= threshold }
        val summary = quotes.joinToString("; ") { "${it.pair.code} ${it.formattedRate}" }
        val desc = Env.str(R.string.fx_desc_live, summary)
        return ItemState(
            icon = icon,
            filled = true,
            text = turns[idx],
            turns = if (turns.size > 1) turns else null,
            tone = if (bigMove) Tone.ACCENT else Tone.NORMAL,
            active = bigMove,
            desc = desc,
            tooltip = desc,
            textLimit = FxRules.BAR_CHARS,
        )
    }

    override fun forgetFetched() {
        staged = null
    }

    override val menu: @Composable (ItemConfig, MenuHost) -> Unit = { item, host -> FxMenu(item, host) }
    override val options: @Composable (ItemConfig, (ItemConfig) -> Unit) -> Unit = { item, set -> FxOptions(item, set) }

    override fun debug(args: List<String>): String? = when (args.firstOrNull()) {
        null -> "staged=${staged != null} on=${Online.on(online)}"
        "stage" -> {
            val s = FxRules.sample(args.getOrNull(1).orEmpty(), Now.wall())
            if (s == null) "fx stage calm|big|offline" else {
                staged = s
                Ticker.refresh(type)
                "staged ${args[1]}"
            }
        }
        "off" -> {
            staged = null
            Ticker.refresh(type)
            "staging off"
        }
        else -> null
    }
}
