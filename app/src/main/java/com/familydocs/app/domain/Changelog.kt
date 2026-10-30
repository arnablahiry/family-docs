package com.familydocs.app.domain

/**
 * Release notes (assets/changelog.txt, also read from GitHub for an update that isn't installed yet). Pure Kotlin.
 * Format: "## 0.8.4" starts a release, "- " lines are its changes, "#" lines are comments.
 */
object Changelog {
    data class Entry(val version: String, val notes: List<String>)

    fun parse(text: String): List<Entry> {
        val out = mutableListOf<Entry>()
        var version: String? = null; var notes = mutableListOf<String>()
        fun flush() { version?.let { if (notes.isNotEmpty()) out += Entry(it, notes) } }
        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("## ") -> { flush(); version = line.removePrefix("## ").trim(); notes = mutableListOf() }
                line.startsWith("- ") && version != null -> notes.add(line.removePrefix("- ").trim())
            }
        }
        flush()
        return out
    }

    /** Releases after [after] (exclusive; null: none excluded) up to [upTo] (inclusive), newest first. */
    fun between(entries: List<Entry>, after: String?, upTo: String): List<Entry> =
        entries.filter { compare(it.version, upTo) <= 0 && (after == null || compare(it.version, after) > 0) }.sortedWith { a, b -> compare(b.version, a.version) }

    /** Compares "0.8.10" and "0.8.9" numerically. */
    fun compare(a: String, b: String): Int {
        val x = a.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }; val y = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 }); if (d != 0) return d }
        return 0
    }
}
