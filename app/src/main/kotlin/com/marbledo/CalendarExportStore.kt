package com.marble098.marbledo

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A bounded snapshot survives the document picker / Activity recreation without a large Bundle. */
internal class CalendarExportStore(directory: File) {
    private val file = File(directory, "pending-occasions.ics")

    fun stage(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES)
        val temporary = File.createTempFile("pending-occasions-", ".tmp", file.parentFile)
        try {
            temporary.writeBytes(bytes)
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    fun copyTo(destination: OutputStream) {
        require(file.isFile && file.length() in 1..MAX_BYTES.toLong()) { "Calendar snapshot missing or too large" }
        file.inputStream().use { it.copyTo(destination) }
    }

    fun discard() { file.delete() }

    companion object { const val MAX_BYTES = 5_000_000 }
}
