package io.github.kuscher.bentobar.items

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The app's US English text as the resource files have it, for the tests of Device batteries, Heat
 * and the CPU rule: a rule's words are then tested as a user will read them, to the character, and
 * not as the test would have written them.
 */
internal class HeatDevicesStrings(vararg files: String) {
    private val strings = HashMap<String, String>()
    private val plurals = HashMap<String, HashMap<String, String>>()

    init {
        for (file in files) {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values", file)).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as? org.w3c.dom.Element ?: continue
                val name = node.getAttribute("name")
                when (node.tagName) {
                    "string" -> strings[name] = shown(node.textContent)
                    "plurals" -> {
                        val items = node.getElementsByTagName("item")
                        for (j in 0 until items.length) {
                            val item = items.item(j) as org.w3c.dom.Element
                            plurals.getOrPut(name) { HashMap() }[item.getAttribute("quantity")] = shown(item.textContent)
                        }
                    }
                }
            }
        }
    }

    /** What Android makes of a resource's text: `\'` is an apostrophe, ` ` the character it names. */
    private fun shown(raw: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) { out.append(c); i++; continue }
            when (val next = raw[i + 1]) {
                'u' -> { out.append(raw.substring(i + 2, i + 6).toInt(16).toChar()); i += 6 }
                'n' -> { out.append('\n'); i += 2 }
                else -> { out.append(next); i += 2 }
            }
        }
        return out.toString()
    }

    val names: Set<String> get() = strings.keys + plurals.keys

    /** The text as written, with its `%1$s` still in it. */
    operator fun get(name: String): String = strings[name] ?: error("no string named $name")

    /** The text with its arguments filled in, as `getString(id, args)` does. */
    fun format(name: String, vararg args: Any): String = String.format(Locale.US, get(name), *args)

    /** A plural for [count], by English's rule: one, or other. */
    fun plural(name: String, count: Int): String {
        val forms = plurals[name] ?: error("no plural named $name")
        return String.format(Locale.US, forms[if (count == 1) "one" else "other"] ?: error("$name has no form for $count"), count)
    }

    /** The two forms of a plural, as the copy deck lists them. */
    fun forms(name: String): Pair<String, String> = (plurals[name] ?: error("no plural named $name")).let { it.getValue("one") to it.getValue("other") }
}
