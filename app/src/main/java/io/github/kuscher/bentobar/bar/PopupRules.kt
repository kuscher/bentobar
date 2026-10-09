package io.github.kuscher.bentobar.bar

/** When pointing at an item opens its popup. Pure, so the rules are tested (`PopupRulesTest`). */
internal object PopupRules {
    /**
     * Whether the pointer coming onto item [hovered] opens its popup: only with "Switch popups on hover" [on] (off by
     * default: nothing opens on hover by itself), only while an item's popup or the ‹ menu is [open] (a key as
     * `toggleMenu` has it: "item:ID", "ctx:ID", "bentobar"), never for the item already open, never for a right-click
     * menu, and only for an item that [hasPopup]: one that acts on a click (keep awake, a Shortcut) must not act on a hover.
     */
    fun hoverOpens(open: String?, hovered: String, hasPopup: Boolean, on: Boolean): Boolean {
        if (!on || open == null || !hasPopup) return false
        if (open != "bentobar" && !open.startsWith("item:")) return false
        return open != "item:$hovered"
    }
}
