package com.roadside.util

import android.util.Log
import java.io.File

/**
 * Keeps capture caches bounded. Every recording (~32 KB/s) and every 12 MP photo
 * (2–3.5 MB) used to be kept forever in the app cache.
 */
object CacheFiles {

    /** Recordings / photos kept per directory; older ones are deleted. */
    const val KEEP_NEWEST = 30

    /**
     * Deletes all but the newest [keep] files with [extension] in [dir], and every file
     * with one of [alwaysDelete] extensions except [exclude] (the file being written now).
     */
    fun prune(
        dir: File,
        extension: String,
        keep: Int = KEEP_NEWEST,
        alwaysDelete: Set<String> = emptySet(),
        exclude: File? = null
    ) {
        val files = dir.listFiles() ?: return
        var deleted = 0
        files.filter { it.extension in alwaysDelete && it != exclude }
            .forEach { if (it.delete()) deleted++ }
        files.filter { it.extension == extension && it != exclude }
            .sortedByDescending { it.lastModified() }
            .drop(keep)
            .forEach { if (it.delete()) deleted++ }
        if (deleted > 0) Log.i("CacheFiles", "Pruned $deleted old file(s) from ${dir.name}")
    }
}
