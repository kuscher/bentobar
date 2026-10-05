package io.github.kuscher.bentobar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What the app promises in its manifest, pinned: which permissions it has, that its notification
 * listener asks for no notifications, that nothing goes out in clear text, and what stays out of a
 * backup. A change to any of these is a decision, so it has to be made here too.
 */
class ManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private fun xml(path: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(path)).documentElement
    private val manifest = xml("src/main/AndroidManifest.xml")
    private fun Element.all(tag: String): List<Element> = getElementsByTagName(tag).let { l -> (0 until l.length).map { l.item(it) as Element } }
    private fun Element.attr(name: String): String = getAttributeNS(android, name)
    private val application = manifest.all("application").single()

    @Test fun thePermissionsAreTheseAndNoOthers() {
        // No Bluetooth, no location, no reading of notifications as a permission: a new one is added here on purpose.
        assertEquals(setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.WAKE_LOCK",
            "android.permission.POST_PROMOTED_NOTIFICATIONS",
            "android.permission.READ_CALENDAR",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.QUERY_ADVANCED_PROTECTION_MODE",
            "android.permission.PACKAGE_USAGE_STATS",
        ), manifest.all("uses-permission").map { it.attr("name") }.toSet())
    }

    @Test fun theNotificationListenerAsksForNoNotificationsAndIsNotStartedByAndroid() {
        val listener = application.all("service").single { it.attr("name") == ".items.MediaAccess" }
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", listener.attr("permission"))
        val meta = listener.all("meta-data").associate { it.attr("name") to it.attr("value") }
        // "A present but empty value means 'allow no types'" (NotificationListenerService).
        assertTrue(listener.all("meta-data").any { it.attr("name") == "android.service.notification.default_filter_types" && it.hasAttributeNS(android, "value") })
        assertEquals("", meta["android.service.notification.default_filter_types"])
        // Conversations 1, alerting 2, silent 4, ongoing 8: all four types, never wanted.
        assertEquals(1 or 2 or 4 or 8, meta["android.service.notification.disabled_filter_types"]!!.toInt())
        assertEquals("false", meta["android.service.notification.default_autobind_listenerservice"])
        // And it is the only notification listener.
        assertEquals(1, application.all("service").count { s -> s.all("action").any { it.attr("name") == "android.service.notification.NotificationListenerService" } })
    }

    @Test fun nothingIsEverSentInClearText() {
        assertEquals("false", application.attr("usesCleartextTraffic"))
        assertEquals("@xml/network_security_config", application.attr("networkSecurityConfig"))
        val config = xml("src/main/res/xml/network_security_config.xml")
        val base = config.all("base-config").single()
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        // No domain gets an exception, and only debug builds trust anything but the system's authorities.
        assertEquals(0, config.all("domain-config").size)
        assertEquals(listOf("system"), base.all("certificates").map { it.getAttribute("src") })
        assertEquals(listOf("user"), config.all("debug-overrides").single().all("certificates").map { it.getAttribute("src") })
    }

    @Test fun whatBelongsToOneInstallStaysOutOfBackupsAndTransfers() {
        assertEquals("@xml/data_extraction_rules", application.attr("dataExtractionRules"))
        val rules = xml("src/main/res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val s = rules.all(section).single()
            assertTrue(section, s.all("exclude").any { it.getAttribute("domain") == "root" && it.getAttribute("path") == "no_backup" })
            // Nothing is named for inclusion, which would turn the rest of the rules into "only this".
            assertEquals(section, 0, s.all("include").size)
        }
    }

    @Test fun theExportedPartsAreTheOnesAndroidNeeds() {
        // The launcher's two entries, the accessibility service, the three tiles and the notification listener:
        // each is guarded by a system permission or is an activity the user starts.
        val exported = (application.all("activity") + application.all("service") + application.all("receiver") + application.all("provider"))
            .filter { it.attr("exported") == "true" }.map { it.attr("name") }.toSet()
        assertEquals(setOf(".ui.MainActivity", ".ui.BarMenuActivity", ".bar.BarService", ".tile.BarTile", ".tile.AwakeTile", ".tile.TimerTile",
            ".items.MediaAccess"), exported)
        for (service in application.all("service")) assertNotNull(service.attr("name"), service.attr("permission").takeIf { it.isNotEmpty() })
    }
}
