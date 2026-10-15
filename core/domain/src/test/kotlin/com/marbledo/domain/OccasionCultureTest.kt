package com.marbledo.domain

import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.OccasionCulture
import com.marbledo.domain.model.enabledOccasionCultures
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OccasionCultureTest {
    @Test fun `old settings adopt all packs while an explicit empty selection stays empty`() {
        val old = Json.decodeFromString<AppSettings>("""{"languageTag":"fa"}""")
        assertEquals(OccasionCulture.all, old.enabledOccasionCultures())
        val none = Json.decodeFromString<AppSettings>("""{"languageTag":"en","occasionCultureIds":[]}""")
        assertTrue(none.enabledOccasionCultures().isEmpty())
        assertEquals(none, Json.decodeFromString<AppSettings>(Json.encodeToString(none)))
    }

    @Test fun `culture ids are stable unique and unknown future ids do not enable unrelated content`() {
        assertEquals(listOf("iranian", "global", "western", "north_america", "europe", "oceania"), OccasionCulture.defaultIds)
        assertEquals(6, OccasionCulture.defaultIds.toSet().size)
        assertEquals(setOf(OccasionCulture.EUROPE), AppSettings(occasionCultureIds = listOf("europe", "europe", "future-pack")).enabledOccasionCultures())
    }
}
