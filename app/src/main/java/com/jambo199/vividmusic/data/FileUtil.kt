package com.jambo199.vividmusic.data

import android.util.Log
import java.io.File

/** Writes via a temp file and rename so a crash mid-write never leaves a half-written file. */
fun File.writeTextAtomically(text: String): Boolean = try {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        writeText(text)
        tmp.delete()
    }
    true
} catch (e: Exception) {
    Log.w("FileUtil", "Could not write $name", e)
    false
}
