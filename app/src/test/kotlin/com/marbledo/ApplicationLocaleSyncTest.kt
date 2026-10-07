package com.marble098.marbledo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ApplicationLocaleSyncTest {
    @Test
    fun `does not apply a default locale while persisted settings are loading`() {
        assertNull(applicationLanguageToApply(persistedLanguageTag = null, currentLocaleTags = "en"))
    }

    @Test
    fun `does not request an activity recreation when the persisted locale is already active`() {
        assertNull(applicationLanguageToApply(persistedLanguageTag = "fa", currentLocaleTags = "fa,en"))
    }

    @Test
    fun `applies the loaded language when it differs from the active locale`() {
        assertEquals("en", applicationLanguageToApply(persistedLanguageTag = "en", currentLocaleTags = "fa"))
    }
}
