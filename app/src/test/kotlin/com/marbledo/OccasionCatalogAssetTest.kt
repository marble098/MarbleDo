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
 * Guards the bundled occasion catalog so the offline copy retains month coverage,
 * well-formed entries only, no duplicate identities that the parser would silently drop, and the
 * per-year overrides stay consistent with the base catalog.
 */
class OccasionCatalogAssetTest {
    private val catalog: Map<String, JsonElement> by lazy {
        val file = catalogFile()
        Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
    }

    private val categories = setOf("official", "national", "religious", "personal", "international", "educational")

    private fun catalogFile(): File {
        val candidates = listOf(
            File("../feature/calendar/src/main/assets/calendar/occasions-v4-fa.json"),
            File("feature/calendar/src/main/assets/calendar/occasions-v4-fa.json"),
            File("src/main/assets/calendar/occasions-v4-fa.json"),
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
        assertTrue(total >= 700, "Expected a broad catalog (not necessarily exhaustive), found $total occasions")
    }

    @Test
    fun `every month of every calendar has occasions so the whole year is covered`() {
        listOf("jalali", "lunar", "gregorian").forEach { calendar ->
            val months = entries(calendar).map { it.getValue("month").jsonPrimitive.int }.toSet()
            assertEquals((1..12).toSet(), months, "Some $calendar months have no occasion at all")
        }
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
    fun `Iranian national and traditional cultural occasions are present`() {
        val jalali = entries("jalali")
        fun has(month: Int, day: Int, titleEn: String) = jalali.any {
            it.getValue("month").jsonPrimitive.int == month &&
                it.getValue("day").jsonPrimitive.int == day &&
                it.getValue("titleEn").jsonPrimitive.content == titleEn
        }
        assertTrue(has(2, 3, "Ardibeheshtgan Festival"), "Ardibeheshtgan is missing")
        assertTrue(has(2, 20, "Damask Rose and Rosewater Day"), "Rosewater Day is missing")
        assertTrue(has(2, 25, "Persian Language Day"), "Persian Language Day is missing")
        assertTrue(has(2, 30, "National Population Day"), "Population Day is missing")
        assertTrue(has(7, 1, "Flag Day"), "Flag Day is missing")
        assertTrue(has(7, 10, "National Elites Day"), "Elites Day is missing")
        assertTrue(has(11, 29, "Sepandarmazgan Festival (Iranian Love Day)"), "Sepandarmazgan is missing")
        assertTrue(has(12, 18, "Bushehr Day"), "Bushehr Day is missing")
    }

    @Test
    fun `Iranian-Islamic and Islamic occasions are present in the lunar calendar`() {
        val lunar = entries("lunar")
        fun has(month: Int, day: Int, titleEn: String) = lunar.any {
            it.getValue("month").jsonPrimitive.int == month &&
                it.getValue("day").jsonPrimitive.int == day &&
                it.getValue("titleEn").jsonPrimitive.content == titleEn
        }
        assertTrue(has(2, 5, "Death of Lady Ruqayya (traditional report)"), "Lady Ruqayya is missing")
        assertTrue(has(4, 8, "Birth of Imam Hassan al-Askari"), "Imam Askari's birth is missing")
        assertTrue(has(4, 10, "Death of Lady Masouma"), "Lady Masouma is missing")
        assertTrue(has(5, 5, "Nurses Day"), "Nurses Day is missing")
        assertTrue(has(7, 3, "Martyrdom of Imam Ali al-Hadi"), "Imam Hadi is missing")
        assertTrue(has(8, 3, "Islamic Revolutionary Guard Corps Day"), "Guards Day is missing")
        assertTrue(has(8, 15, "World Day of the Oppressed"), "World Day of the Oppressed is missing")
        assertTrue(has(9, 10, "Death of Lady Khadija"), "Lady Khadija is missing")
        assertTrue(has(11, 1, "Birth of Lady Masouma and Girls' Day"), "Girls' Day is missing")
        assertTrue(has(12, 1, "Anniversary of Ali and Fatima's Wedding"), "The wedding anniversary is missing")
    }

    @Test
    fun `additional international and cultural observances are retained`() {
        val titles = entries("gregorian").map { it.getValue("titleEn").jsonPrimitive.content }.toSet()
        listOf(
            "World Kindness Day",
            "World Alzheimer's Day",
            "International Human Solidarity Day",
            "World Turkic Language Family Day",
            "World Mosque Day",
            "World Parkinson's Day",
            "Sustainable Gastronomy Day",
        ).forEach { assertTrue(it in titles, "Missing world day: $it") }
    }

    @Test
    fun `variable observances use rules rather than guessed solar dates`() {
        val all = allEntries().map { it.second }
        fun find(title: String) = all.single { it.getValue("titleEn").jsonPrimitive.content == title }
        assertEquals("last_weekday", find("International Quds Day").getValue("dateRule").jsonPrimitive.content)
        assertEquals(5, find("International Quds Day").getValue("weekday").jsonPrimitive.int)
        assertEquals("last_weekday", find("Chaharshanbe Suri").getValue("dateRule").jsonPrimitive.content)
        assertEquals(-1, find("Chaharshanbe Suri").getValue("dayOffset").jsonPrimitive.int)
        assertEquals("month_end", find("Martyrdom of Imam Reza (last day of Safar)").getValue("dateRule").jsonPrimitive.content)
        assertEquals("month_end", find("Martyrdom of Imam Jawad").getValue("dateRule").jsonPrimitive.content)
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
        assertTrue(has(12, 8, "Islamic Education Affairs Day"), "Education Affairs must be 8 Esfand")
        assertTrue(has(6, 12, "Day against British Colonialism"), "Anti-colonial day belongs in Shahrivar, not Khordad")
    }

    @Test
    fun `yearly overrides use Jalali year keys and valid entries`() {
        val yearly = catalog["yearly"]?.jsonObject ?: error("The yearly section is required")
        assertTrue(yearly.isNotEmpty(), "The yearly section retains explicitly documented annual instructions")
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
                ids += item.getValue("id").jsonPrimitive.content
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
