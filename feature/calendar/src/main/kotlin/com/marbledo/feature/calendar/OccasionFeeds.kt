package com.marbledo.feature.calendar

import org.json.JSONArray
import org.json.JSONObject

/** Where a fact came from. The app never treats one community calendar as an authority. */
enum class OccasionSourceTier(val key: String) {
    OFFICIAL("official"), UNOFFICIAL("unofficial"), OPEN_SOURCE("open-source");

    companion object {
        fun fromKey(key: String): OccasionSourceTier? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One online endpoint. [catalog] feeds are full schema-6 documents that replace the visible
 * catalog; the other feeds are independent holiday lists used only as a cross-check, because
 * several of them still convert Hijri dates with the arithmetic calendar.
 */
data class RemoteOccasionFeed(
    val id: String,
    val tier: OccasionSourceTier,
    val url: String,
    val catalog: Boolean,
    val accept: String = "application/json",
)

/** Result of comparing the catalog's holiday days with the independent day lists. */
data class HolidayCrossCheck(
    val feeds: Int = 0,
    /** Days at least two independent feeds also mark as holidays, keyed month * 100 + day. */
    val confirmed: Set<Int> = emptySet(),
    /** Days the community feeds mark but the official table does not; never published as holidays. */
    val communityOnly: Set<Int> = emptySet(),
) {
    val isEmpty: Boolean get() = feeds == 0
}

object OccasionFeeds {
    private const val FEED_PATH = "data/occasions/occasions-live.json"

    /**
     * Independent delivery paths for the aggregated feed that CI publishes. These are mirrors of
     * one document - not independent sources - so the app also reads [holidayDayFeeds] directly.
     */
    val catalogMirrors: List<RemoteOccasionFeed> = listOf(
        RemoteOccasionFeed(
            id = "feed-github-raw",
            tier = OccasionSourceTier.OPEN_SOURCE,
            url = "https://raw.githubusercontent.com/marble098/MarbleDo/main/$FEED_PATH",
            catalog = true,
        ),
        RemoteOccasionFeed(
            id = "feed-jsdelivr",
            tier = OccasionSourceTier.OPEN_SOURCE,
            url = "https://cdn.jsdelivr.net/gh/marble098/MarbleDo@main/$FEED_PATH",
            catalog = true,
        ),
        RemoteOccasionFeed(
            id = "feed-jsdelivr-fastly",
            tier = OccasionSourceTier.OPEN_SOURCE,
            url = "https://fastly.jsdelivr.net/gh/marble098/MarbleDo@main/$FEED_PATH",
            catalog = true,
        ),
        RemoteOccasionFeed(
            id = "feed-github-api",
            tier = OccasionSourceTier.OPEN_SOURCE,
            url = "https://api.github.com/repos/marble098/MarbleDo/contents/$FEED_PATH?ref=main",
            catalog = true,
            accept = "application/vnd.github.raw",
        ),
    )

    /**
     * Community holiday lists, read directly from the internet for the current and next Jalali
     * year. They are a cross-check for the aggregated feed, not a date authority: a Hijri
     * occasion that they place one day late is reported, never copied.
     */
    fun holidayDayFeeds(jalaliYear: Int): List<RemoteOccasionFeed> = listOf(
        RemoteOccasionFeed(
            id = "iran-holidays-json",
            tier = OccasionSourceTier.OPEN_SOURCE,
            url = "https://api.github.com/repos/iyazdanicharati/IranHollidaysJSON/contents/$jalaliYear.json?ref=main",
            catalog = false,
            accept = "application/vnd.github.raw",
        ),
        RemoteOccasionFeed(
            id = "shamsi-holidays",
            tier = OccasionSourceTier.OPEN_SOURCE,
            url = "https://api.github.com/repos/hasan-ahani/shamsi-holidays/contents/holidays/$jalaliYear.json?ref=master",
            catalog = false,
            accept = "application/vnd.github.raw",
        ),
        RemoteOccasionFeed(
            id = "jalali-calendar-year",
            tier = OccasionSourceTier.OPEN_SOURCE,
            url = "https://api.github.com/repos/alirezadesh/jalali_calendar/contents/${jalaliYear}_data.json?ref=main",
            catalog = false,
            accept = "application/vnd.github.raw",
        ),
    )

    val urls: List<String> get() = catalogMirrors.map(RemoteOccasionFeed::url)

    /** The community day-list endpoints for a given year, exposed for diagnostics and tests. */
    fun communityDayFeedUrls(jalaliYear: Int): List<String> = holidayDayFeeds(jalaliYear).map(RemoteOccasionFeed::url)
}

/** Parsers for the independent day lists. Kept pure so they are unit-testable without network. */
object HolidayDayParser {
    /**
     * Returns the set of Jalali days (month * 100 + day) the payload marks as a public holiday.
     * Three published shapes are supported; anything unrecognised yields an empty set instead of
     * a wrong day.
     */
    fun parse(payload: String): Set<Int> = runCatching {
        val trimmed = payload.trim()
        if (trimmed.startsWith("[")) parseList(JSONArray(trimmed)) else parseObject(JSONObject(trimmed))
    }.getOrDefault(emptySet())

    private fun parseList(array: JSONArray): Set<Int> {
        val days = mutableSetOf<Int>()
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val events = row.optJSONArray("events")
            var holiday = row.optBoolean("is_holiday", false) || row.optBoolean("isHoliday", false)
            if (events != null) {
                for (eventIndex in 0 until events.length()) {
                    val event = events.optJSONObject(eventIndex) ?: continue
                    if (event.optBoolean("is_holiday", false) || event.optBoolean("isHoliday", false)) {
                        holiday = true
                    }
                }
            }
            if (holiday) dayKey(row.optString("date"))?.let(days::add)
        }
        return days
    }

    private fun parseObject(root: JSONObject): Set<Int> {
        val rows = root.optJSONArray("data") ?: return emptySet()
        val days = mutableSetOf<Int>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            if (row.optBoolean("isHoliday", false) || row.optBoolean("is_holiday", false)) {
                dayKey(row.optString("date"))?.let(days::add)
            }
        }
        return days
    }

    /** Accepts `1405/01/01`, `1405-01-01` and Persian digits; returns month * 100 + day. */
    fun dayKey(raw: String): Int? {
        val parts = StringBuilder()
        raw.trim().forEach { character ->
            when (character) {
                in '\u06F0'..'\u06F9' -> parts.append('0' + (character - '\u06F0'))
                in '\u0660'..'\u0669' -> parts.append('0' + (character - '\u0660'))
                in '0'..'9' -> parts.append(character)
                '/', '-' -> parts.append('/')
            }
        }
        val fields = parts.toString().trim('/').split('/')
        if (fields.size != 3) return null
        val year = fields[0].toIntOrNull() ?: return null
        val month = fields[1].toIntOrNull() ?: return null
        val day = fields[2].toIntOrNull() ?: return null
        if (year !in 1200..1700 || month !in 1..12 || day !in 1..31) return null
        return month * 100 + day
    }
}

object HolidayCrossChecker {
    /**
     * Compares the national holidays of [catalog] with day lists published by independent feeds.
     * A day is *confirmed* when at least two feeds agree; days the feeds mark while the official
     * table does not are kept as [HolidayCrossCheck.communityOnly] and reported, never published.
     */
    fun check(nationalHolidayDays: Set<Int>, feedDays: List<Set<Int>>): HolidayCrossCheck {
        if (feedDays.isEmpty()) return HolidayCrossCheck()
        val votes = mutableMapOf<Int, Int>()
        feedDays.forEach { days -> days.forEach { day -> votes[day] = (votes[day] ?: 0) + 1 } }
        val community = votes.filterValues { it >= 2 }.keys
        val mine = nationalHolidayDays
        return HolidayCrossCheck(
            feeds = feedDays.size,
            confirmed = mine.intersect(community),
            communityOnly = community - mine,
        )
    }
}
