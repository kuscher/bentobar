package io.github.kuscher.barbook.ui

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
import io.github.kuscher.barbook.bar.BarService
import io.github.kuscher.barbook.data.Store
import io.github.kuscher.barbook.items.Chips
import io.github.kuscher.barbook.items.Env
import io.github.kuscher.barbook.items.Notify
import io.github.kuscher.barbook.items.Ticker
import io.github.kuscher.barbook.util.Sym
import io.github.kuscher.barbook.util.SymIcon

class MainActivity : ComponentActivity() {
    /** Bumped on resume so permission and service rows re-read their state. */
    private val resumes = mutableIntStateOf(0)
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
            BarBookTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    App(resumes.intValue)
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

    override fun onResume() {
        super.onResume()
        resumes.intValue++
        Ticker.start("settings")
        Chips.update(this)
    }

    override fun onPause() {
        super.onPause()
        Ticker.stop("settings")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        resumes.intValue++
        Ticker.refresh()
    }

    @Composable
    private fun App(@Suppress("UNUSED_PARAMETER") resumeCount: Int) {
        val cfg by Store.config.collectAsState()
        val running = serviceOn(this)
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxHeight()) {
                Spacer(Modifier.height(12.dp))
                SymIcon(Sym.LOCAL_BAR, size = 28.sp, filled = true, color = MaterialTheme.colorScheme.primary)
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
                        Text(listOf("Your bar", "Add items", "Look and behaviour", "Setup", "About BarBook")[page],
                            style = MaterialTheme.typography.headlineSmall)
                        Text(when {
                            !running -> "BarBook's bar is off. Turn it on in Setup."
                            !cfg.enabled -> "Hidden for now; switch it back on here or with the Quick Settings tile."
                            else -> "Live in the status bar. Changes apply right away."
                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Show in status bar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 10.dp))
                    Switch(checked = cfg.enabled, onCheckedChange = { on -> Store.update { it.copy(enabled = on) } })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        0 -> BarPage(running, selected, onSelect = { selected = it }, onSetup = { page = 3 })
                        1 -> AddPage { id -> selected = id; page = 0 }
                        2 -> LookPage()
                        3 -> SetupPage(this@MainActivity, running)
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

        /** Opens BarBook's settings, on [itemId] if given. */
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
         * Accessibility settings. The extras ask Settings to scroll to and highlight BarBook
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
