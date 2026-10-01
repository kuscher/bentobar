package io.github.kuscher.bentobar.ui

import android.app.Activity
import android.os.Bundle
import io.github.kuscher.bentobar.bar.BarService
import io.github.kuscher.bentobar.items.Env

/**
 * "BentoBar menu" in the launcher: opens the bar's menu with every item in it, so a keyboard
 * shortcut bound to it in the system's shortcut settings (Action + / › Customize) reaches the bar
 * without a pointer. BentoBar doesn't read keys itself. Without the strip on screen it opens settings.
 */
class BarMenuActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Env.init(this)
        val opened = (Env.service as? BarService)?.controller()?.openBarMenu() == true
        if (!opened) MainActivity.open(this, null)
        finish()
    }
}
