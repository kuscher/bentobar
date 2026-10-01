package io.github.kuscher.bentobar.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.bentobar.bar.BarService
import io.github.kuscher.bentobar.bar.BarStatus
import io.github.kuscher.bentobar.data.Store
import io.github.kuscher.bentobar.items.Chips
import io.github.kuscher.bentobar.items.Env
import io.github.kuscher.bentobar.items.Notify
import io.github.kuscher.bentobar.items.Ticker
import io.github.kuscher.bentobar.util.Sym
import io.github.kuscher.bentobar.util.SymIcon

class MainActivity : ComponentActivity() {
    // Accessibility services turned on or off (in Settings' own window, on a Googlebook).
    private val services = AccessibilityManager.AccessibilityServicesStateChangeListener { Setup.refresh(this) }
    private var page by mutableIntStateOf(0)
    private var selected by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        Env.init(this)
        Notify.channels(this)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            BentoBarTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    App()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_ITEM)?.let { selected = it; page = 0 }
        if (intent.getBooleanExtra(EXTRA_EDIT, false)) page = 0
        intent.getStringExtra(EXTRA_REQUEST)?.let { perm ->
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(perm), 1)
        }
        if (intent.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES") page = 2
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        getSystemService(AccessibilityManager::class.java)?.addAccessibilityServicesStateChangeListener(mainExecutor, services)
    }

    override fun onStop() {
        getSystemService(AccessibilityManager::class.java)?.removeAccessibilityServicesStateChangeListener(services)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        Setup.refresh(this)
        Ticker.start("settings")
        Chips.update(this)
    }

    override fun onPause() {
        super.onPause()
        Ticker.stop("settings")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Setup.refresh(this)
        Ticker.refresh()
    }

    // In desktop windowing, Settings opens in its own window and this one stays resumed, so coming
    // back is a focus change, not a resume.
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (isTopResumedActivity) Setup.refresh(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Setup.refresh(this)
    }

    @Composable
    private fun App() {
        val cfg by Store.config.collectAsState()
        val setup by Setup.state.collectAsState()
        val status by BarStatus.current.collectAsState()
        val running = setup.serviceOn
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxHeight()) {
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(io.github.kuscher.bentobar.R.drawable.ic_bentobar), contentDescription = "BentoBar",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(20.dp))
                val pages = listOf(Sym.WYSIWYG to "Bar", Sym.ADD to "Add", Sym.PALETTE to "Look", Sym.TUNE to "Setup", Sym.INFO to "About")
                pages.forEachIndexed { i, (icon, label) ->
                    NavigationRailItem(selected = page == i, onClick = { page = i },
                        icon = { SymIcon(icon, size = 22.sp, filled = page == i) }, label = { Text(label) })
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(listOf("Your bar", "Add items", "Look and behaviour", "Setup", "About BentoBar")[page],
                            style = MaterialTheme.typography.headlineSmall)
                        // Says what the strip is actually doing, not just whether the service is on.
                        Text(when {
                            !running -> "BentoBar's accessibility service is off. Turn it on in Setup."
                            !cfg.enabled -> "Hidden for now; switch it back on here or with the Quick Settings tile."
                            else -> when (status) {
                                BarStatus.STOPPED -> "Starting…"
                                BarStatus.NO_ROOM -> "The status bar has no free space for BentoBar's items right now."
                                BarStatus.NO_BAR -> "The status bar is hidden right now (a full-screen app), so BentoBar is too."
                                BarStatus.COVERED -> "A system panel covers the status bar; BentoBar is back when it closes."
                                else -> "Live in the status bar. Changes apply right away."
                            }
                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Only meaningful once the service runs; before that, Setup is the way in.
                    if (running) {
                        Text("Show in status bar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 10.dp))
                        Switch(checked = cfg.enabled, onCheckedChange = { on -> Store.update { it.copy(enabled = on) } })
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        0 -> BarPage(running, selected, onSelect = { selected = it }, onSetup = { page = 3 })
                        1 -> AddPage { id -> selected = id; page = 0 }
                        2 -> LookPage()
                        3 -> SetupPage(this@MainActivity, setup)
                        else -> AboutPage()
                    }
                }
            }
        }
    }

    companion object {
        /** The open settings window, for the adb test hooks. */
        @Volatile var current: MainActivity? = null
        const val EXTRA_ITEM = "item"
        const val EXTRA_EDIT = "edit"
        const val EXTRA_REQUEST = "request"

        /** Opens BentoBar's settings, on [itemId] if given. */
        fun open(context: Context, itemId: String?) {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_EDIT, true).apply { if (itemId != null) putExtra(EXTRA_ITEM, itemId) })
        }

        /** Runtime permissions need an activity; the bar's menus come through here. */
        fun requestPermission(context: Context, permission: String) {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_REQUEST, permission))
        }

        fun serviceComponent(context: Context) = ComponentName(context, BarService::class.java)

        /** Enabled in Accessibility settings (the service may take a moment to connect). */
        fun serviceOn(context: Context): Boolean {
            if (Env.service != null) return true
            val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
            val me = serviceComponent(context)
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.let { s -> s.packageName == me.packageName && s.name == me.className } }
        }

        /**
         * Accessibility settings. The extras ask Settings to scroll to and highlight BentoBar
         * (honoured by Android's own Settings app; harmless elsewhere).
         */
        fun openAccessibility(context: Context) {
            val cn = serviceComponent(context).flattenToString()
            Env.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", cn)
                .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", cn) }))
        }

        fun openAppInfo(context: Context) {
            Env.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }
    }
}
