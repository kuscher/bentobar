package io.github.kuscher.bentobar.items

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The app's text files as a test reads them, so that what a test compares is the text the app shows, not a copy of it. */
object StringFiles {
    /** The strings and the plurals (each quantity's text) of some files, by resource name. */
    class Texts(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    /** The strings and plurals of [files], those in `values` first and then [folder]'s on top, as Android resolves them. */
    fun read(files: List<String>, folder: String = "values"): Texts {
        val strings = HashMap<String, String>()
        val plurals = HashMap<String, Map<String, String>>()
        for (dir in listOf("values", folder).distinct()) for (file in files) {
            val f = File("src/main/res/$dir/$file")
            if (!f.isFile) continue
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                when (e.tagName) {
                    "string" -> strings[e.getAttribute("name")] = unescape(e.textContent)
                    "plurals" -> {
                        val items = e.getElementsByTagName("item")
                        plurals[e.getAttribute("name")] = (0 until items.length).map { items.item(it) as Element }
                            .associate { it.getAttribute("quantity") to unescape(it.textContent) }
                    }
                }
            }
        }
        return Texts(strings, plurals)
    }

    /** The names of the strings and plurals one file defines, in its order. */
    fun names(path: String): List<String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.map { it.getAttribute("name") }
    }

    /** What Android makes of a resource's text: `\'` is an apostrophe, ` ` a character, `\n` a new line. */
    fun unescape(raw: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) { out.append(c); i++; continue }
            when (val next = raw[i + 1]) {
                'n' -> { out.append('\n'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                'u' -> { out.append(raw.substring(i + 2, i + 6).toInt(16).toChar()); i += 6 }
                else -> { out.append(next); i += 2 }
            }
        }
        return out.toString()
    }
}
