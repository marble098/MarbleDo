package com.marble098.marbledo

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the bundled occasion catalog so the offline copy stays complete: every month covered,
 * well-formed entries only, no duplicate identities that the parser would silently drop, and the
 * per-year overrides stay consistent with the base catalog.
 */
class OccasionCatalogAssetTest {
    private val catalog: Map<String, JsonElement> by lazy {
        val file = catalogFile()
        Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
    }

    private val categories = setOf("official", "national", "religious", "personal", "international")

    private fun catalogFile(): File {
        val candidates = listOf(
            File("../feature/calendar/src/main/assets/calendar/holidays-fa.json"),
            File("feature/calendar/src/main/assets/calendar/holidays-fa.json"),
            File("src/main/assets/calendar/holidays-fa.json"),
        )
        return candidates.firstOrNull(File::exists)
            ?: error("Bundled occasions catalog not found from working directory ${File(".").absolutePath}")
    }

    private fun entries(key: String): List<JsonObject> =
        catalog.getValue(key).jsonArray.map { it.jsonObject }

    private fun allEntries(): List<Pair<String, JsonObject>> =
        listOf("jalali", "lunar", "gregorian").flatMap { calendar -> entries(calendar).map { calendar to it } }

    private fun assertValidEntry(where: String, item: JsonObject) {
        val month = item.getValue("month").jsonPrimitive.int
        val day = item.getValue("day").jsonPrimitive.int
        assertTrue(month in 1..12, "$where month out of range: $item")
        assertTrue(day in 1..31, "$where day out of range: $item")
        assertTrue(item.getValue("titleFa").jsonPrimitive.content.isNotBlank(), "Blank titleFa in $where: $item")
        assertTrue(item.getValue("titleEn").jsonPrimitive.content.isNotBlank(), "Blank titleEn in $where: $item")
        val category = item.getValue("category").jsonPrimitive.content
        assertTrue(category in categories, "Unknown category $category in $where: $item")
        assertTrue(item.getValue("holiday").jsonPrimitive.content in setOf("true", "false"), "Malformed holiday flag in $where: $item")
    }

    @Test
    fun `bundled catalog is a large full-year database`() {
        val total = listOf("jalali", "lunar", "gregorian").sumOf { entries(it).size }
        assertTrue(total >= 500, "Expected a large catalog covering every observance type, found $total occasions")
    }

    @Test
    fun `every jalali month has occasions so the whole year is covered`() {
        val months = entries("jalali").map { it.getValue("month").jsonPrimitive.int }.toSet()
        assertEquals((1..12).toSet(), months, "Some Jalali months have no occasion at all")
    }

    @Test
    fun `every category is populated so each occasion type can be toggled`() {
        val used = allEntries().map { it.second.getValue("category").jsonPrimitive.content }.toSet()
        assertEquals(categories, used, "Each occasion category needs at least one entry")
    }

    @Test
    fun `entries carry valid dates, titles and categories`() {
        allEntries().forEach { (calendar, item) -> assertValidEntry(calendar, item) }
    }

    @Test
    fun `world days include the international day of the girl child on 11 October`() {
        val girlChildDay = entries("gregorian").singleOrNull {
            it.getValue("titleEn").jsonPrimitive.content == "International Day of the Girl Child"
        }
        assertTrue(girlChildDay != null, "International Day of the Girl Child is missing")
        assertEquals(10, girlChildDay!!.getValue("month").jsonPrimitive.int)
        assertEquals(11, girlChildDay.getValue("day").jsonPrimitive.int)
        assertEquals("international", girlChildDay.getValue("category").jsonPrimitive.content)
    }

    @Test
    fun `world days from the UN observances list are present`() {
        val titles = entries("gregorian").map { it.getValue("titleEn").jsonPrimitive.content }.toSet()
        listOf(
            "World Braille Day",
            "International Mother Language Day",
            "World Wildlife Day",
            "International Day of Happiness",
            "World Teachers' Day",
            "International Day of Older Persons",
            "World Mental Health Day",
            "World Food Day",
            "Human Rights Day",
            "International Migrants Day",
        ).forEach { assertTrue(it in titles, "Missing world day: $it") }
    }

    @Test
    fun `corrected Iranian dates are fixed`() {
        val jalali = entries("jalali")
        fun has(month: Int, day: Int, titleEn: String) = jalali.any {
            it.getValue("month").jsonPrimitive.int == month &&
                it.getValue("day").jsonPrimitive.int == day &&
                it.getValue("titleEn").jsonPrimitive.content == titleEn
        }
        assertTrue(has(2, 28, "Omar Khayyam Day"), "Khayyam Day must be 28 Ordibehesht")
        assertTrue(has(6, 13, "Cooperation Day"), "Cooperation Day must be 13 Shahrivar")
        assertTrue(has(12, 15, "Tree Planting Day"), "Tree Planting Day must be 15 Esfand")
    }

    @Test
    fun `yearly overrides use Jalali year keys and valid entries`() {
        val yearly = catalog["yearly"]?.jsonObject ?: error("The yearly section is required")
        assertTrue(yearly.isNotEmpty(), "The yearly section should cover upcoming years")
        yearly.forEach { (year, body) ->
            assertTrue((year.toIntOrNull() ?: 0) in 1400..1500, "Yearly key must be a Jalali year: $year")
            val section = body.jsonObject
            section["jalali"]?.jsonArray?.forEach { assertValidEntry("yearly $year", it.jsonObject) }
            section["lunar"]?.jsonArray?.forEach { assertValidEntry("yearly $year", it.jsonObject) }
            section["gregorian"]?.jsonArray?.forEach { assertValidEntry("yearly $year", it.jsonObject) }
            section["removeTitlesEn"]?.jsonArray?.forEach {
                assertTrue(it.jsonPrimitive.content.isNotBlank(), "Blank removeTitlesEn entry in yearly $year")
            }
        }
    }

    @Test
    fun `no duplicate occasion identities exist`() {
        val ids = mutableListOf<String>()
        listOf("jalali", "lunar", "gregorian").forEach { calendar ->
            entries(calendar).forEach { item ->
                ids += "$calendar-${item.getValue("month").jsonPrimitive.int}-${item.getValue("day").jsonPrimitive.int}-${item.getValue("titleEn").jsonPrimitive.content}"
            }
        }
        assertEquals(ids.size, ids.toSet().size, "Duplicate occasion ids would be dropped by the parser")
    }

    @Test
    fun `holidays exist for the jalali and lunar calendars`() {
        assertTrue(entries("jalali").any { it.getValue("holiday").jsonPrimitive.content == "true" }, "No official Jalali holiday")
        assertTrue(entries("lunar").any { it.getValue("holiday").jsonPrimitive.content == "true" }, "No religious holiday")
    }
}
