package com.marble098.marbledo

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the bundled occasion catalog so the offline copy stays complete: every month covered,
 * well-formed entries only, and no duplicate identities that the parser would silently drop.
 */
class OccasionCatalogAssetTest {
    private val catalog: Map<String, kotlinx.serialization.json.JsonElement> by lazy {
        val file = catalogFile()
        Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
    }

    private fun catalogFile(): File {
        val candidates = listOf(
            File("../feature/calendar/src/main/assets/calendar/holidays-fa.json"),
            File("feature/calendar/src/main/assets/calendar/holidays-fa.json"),
            File("src/main/assets/calendar/holidays-fa.json"),
        )
        return candidates.firstOrNull(File::exists)
            ?: error("Bundled occasions catalog not found from working directory ${File(".").absolutePath}")
    }

    private fun entries(key: String): List<kotlinx.serialization.json.JsonObject> =
        catalog.getValue(key).jsonArray.map { it.jsonObject }

    @Test
    fun `bundled catalog is well above the historical ninety-four occasions`() {
        val total = listOf("jalali", "lunar", "gregorian").sumOf { entries(it).size }
        assertTrue(total >= 120, "Expected an expanded catalog, found $total occasions")
    }

    @Test
    fun `every jalali month has occasions so the whole year is covered`() {
        val months = entries("jalali").map { it.getValue("month").jsonPrimitive.int }.toSet()
        assertEquals((1..12).toSet(), months, "Some Jalali months have no occasion at all")
    }

    @Test
    fun `entries carry valid dates, titles and categories`() {
        listOf("jalali", "lunar", "gregorian").forEach { calendar ->
            entries(calendar).forEach { item ->
                val month = item.getValue("month").jsonPrimitive.int
                val day = item.getValue("day").jsonPrimitive.int
                assertTrue(month in 1..12, "$calendar month out of range: $item")
                assertTrue(day in 1..31, "$calendar day out of range: $item")
                assertTrue(item.getValue("titleFa").jsonPrimitive.content.isNotBlank(), "Blank titleFa: $item")
                assertTrue(item.getValue("titleEn").jsonPrimitive.content.isNotBlank(), "Blank titleEn: $item")
                val category = item.getValue("category").jsonPrimitive.content
                assertTrue(
                    category in setOf("official", "national", "religious", "personal"),
                    "Unknown category $category in $item",
                )
                // Accessing the primitive as a boolean also fails the test when the flag is malformed.
                item.getValue("holiday").jsonPrimitive.boolean
            }
        }
    }

    @Test
    fun `no duplicate occasion identities exist`() {
        val ids = mutableListOf<String>()
        listOf("jalali" to "jalali", "lunar" to "lunar", "gregorian" to "gregorian").forEach { (key, calendar) ->
            entries(key).forEach { item ->
                ids += "$calendar-${item.getValue("month").jsonPrimitive.int}-${item.getValue("day").jsonPrimitive.int}-${item.getValue("titleEn").jsonPrimitive.content}"
            }
        }
        assertEquals(ids.size, ids.toSet().size, "Duplicate occasion ids would be dropped by the parser")
    }

    @Test
    fun `holidays exist for the jalali and lunar calendars`() {
        assertTrue(entries("jalali").any { it.getValue("holiday").jsonPrimitive.boolean }, "No official Jalali holiday")
        assertTrue(entries("lunar").any { it.getValue("holiday").jsonPrimitive.boolean }, "No religious holiday")
    }
}
