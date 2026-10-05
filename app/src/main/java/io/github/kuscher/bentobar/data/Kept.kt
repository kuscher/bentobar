package io.github.kuscher.bentobar.data

import java.io.File

/**
 * Small texts that stay on this install: a service's last answer, so that a restart can show it
 * without asking again. They live under Android's no-backup directory, which the system leaves out
 * of cloud backups and device-to-device transfers, and they are no part of the layout, so Copy
 * settings never carries them.
 *
 * Pure Kotlin (plain files), unit-tested on the JVM. Reading and writing touch the disk: do it from a
 * background load, not from an item's state.
 *
 * Never keep a service's reply as it came: a flight service's reply repeats the key it was asked
 * with. Keep your own model, serialized, which cannot hold what you did not put into it.
 */
class Kept(private val dir: File, private val maxEntries: Int = 32, private val maxChars: Int = 200_000) {
    /** [savedAt]: wall-clock milliseconds, as given to [write]. */
    class Entry(val text: String, val savedAt: Long)

    private fun file(name: String) = File(dir, safe(name) + EXT)

    /** What was kept under [name], or null: nothing was, or it can't be read (then it is removed). */
    @Synchronized
    fun read(name: String): Entry? {
        val f = file(name)
        if (!f.isFile) return null
        return try {
            val all = f.readText(Charsets.UTF_8)
            val cut = all.indexOf('\n')
            Entry(all.substring(cut + 1), all.substring(0, cut).toLong())
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    /** Keeps [text] under [name], in place of what was there. False if it is too long or the disk refused. */
    @Synchronized
    fun write(name: String, text: String, savedAt: Long): Boolean {
        if (text.length > maxChars) return false
        return try {
            dir.mkdirs()
            val target = file(name)
            // Written beside it and then renamed: a reader sees the old text or the new, never half of one.
            val part = File(dir, target.name + ".part")
            part.writeText("$savedAt\n$text", Charsets.UTF_8)
            if (!part.renameTo(target)) { part.delete(); return false }
            trim()
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun remove(name: String) { file(name).delete() }

    /** The names that have something kept (in their file-safe form, see [safe]). */
    @Synchronized
    fun names(): Set<String> = entries().mapTo(HashSet()) { it.name.removeSuffix(EXT) }

    /** Removes everything not named in [names]: what belonged to an item that is gone. */
    @Synchronized
    fun keepOnly(names: Set<String>) {
        val keep = names.mapTo(HashSet()) { safe(it) + EXT }
        entries().forEach { if (it.name !in keep) it.delete() }
    }

    @Synchronized
    fun clear() { dir.listFiles()?.forEach { it.delete() } }

    private fun entries(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }?.toList().orEmpty()

    /** More than [maxEntries]: the ones written longest ago go. */
    private fun trim() {
        val all = entries()
        if (all.size > maxEntries) all.sortedBy { it.lastModified() }.take(all.size - maxEntries).forEach { it.delete() }
    }

    companion object {
        private const val EXT = ".txt"
        @Volatile private var root: File? = null

        /** [noBackupDir]: `Context.getNoBackupFilesDir()`. Called by [Online.init]. */
        fun init(noBackupDir: File) { root = noBackupDir }

        private fun under(path: String) = Kept(File(root ?: error("Kept.init has not run"), path))

        /** What a service sent, kept for a restart: emptied when that service is switched off. */
        fun fetched(service: Online.Service): Kept = under("fetched/" + service.id)

        /** A feature's own notes by [name] (which flight an item follows): only the feature empties it. */
        fun own(name: String): Kept = under("kept/" + safe(name))

        /** [name] as a file's name: letters, digits, `.`, `_` and `-` stay, anything else becomes `_`; 80 characters at most. */
        fun safe(name: String): String =
            name.take(80).map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' || it == '-') it else '_' }
                .joinToString("").ifEmpty { "_" }.let { if (it.all { c -> c == '.' }) "_" else it }
    }
}
