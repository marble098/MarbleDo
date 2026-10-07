package com.marble098.marbledo

/**
 * Returns the persisted app language only when settings have loaded and it differs from the active
 * AppCompat locale. A null settings value must never be treated as the default Persian profile:
 * DataStore can emit the user's saved language only after the first composition.
 */
internal fun applicationLanguageToApply(
    persistedLanguageTag: String?,
    currentLocaleTags: String?,
): String? {
    val requestedLanguage = persistedLanguageTag ?: return null
    val currentLanguage = currentLocaleTags.orEmpty().substringBefore(',')
    return requestedLanguage.takeIf { it != currentLanguage }
}
