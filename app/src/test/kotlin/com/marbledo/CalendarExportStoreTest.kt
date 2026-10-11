package com.marble098.marbledo

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CalendarExportStoreTest {
    private fun withStore(block: (CalendarExportStore, File) -> Unit) {
        val directory = Files.createTempDirectory("marbledo-ics-test").toFile()
        try { block(CalendarExportStore(directory), directory) } finally { directory.deleteRecursively() }
    }

    @Test fun `snapshot survives recreation and preserves UTF8`() = withStore { store, directory ->
        val text = "BEGIN:VCALENDAR\r\nSUMMARY:شب یلدا 🌙\r\nEND:VCALENDAR\r\n"
        store.stage(text)
        val output = ByteArrayOutputStream()
        CalendarExportStore(directory).copyTo(output)
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), output.toByteArray())
        assertEquals(listOf("pending-occasions.ics"), directory.list()!!.toList())
    }

    @Test fun `cancellation deletes staged data and cannot reuse it`() = withStore { store, _ ->
        store.stage("old snapshot")
        store.discard()
        assertThrows(IllegalArgumentException::class.java) { store.copyTo(ByteArrayOutputStream()) }
    }

    @Test fun `oversized snapshot never replaces the previous valid file`() = withStore { store, _ ->
        store.stage("valid snapshot")
        assertThrows(IllegalArgumentException::class.java) { store.stage("الف".repeat(CalendarExportStore.MAX_BYTES / 3)) }
        val output = ByteArrayOutputStream()
        store.copyTo(output)
        assertEquals("valid snapshot", output.toString("UTF-8"))
    }

    @Test fun `failed document provider does not silently count as a successful copy`() = withStore { store, _ ->
        store.stage("snapshot")
        assertThrows(IOException::class.java) {
            store.copyTo(object : OutputStream() { override fun write(value: Int) { throw IOException("provider failed") } })
        }
        val output = ByteArrayOutputStream()
        store.copyTo(output)
        assertEquals("snapshot", output.toString("UTF-8"))
    }
}
