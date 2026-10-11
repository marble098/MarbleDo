package com.marbledo.feature.calendar

/** A checked source is not necessarily an official source; its kind is shown alongside its URL. */
data class CatalogReference(
    val id: String,
    val titleFa: String,
    val titleEn: String,
    val url: String,
    val kind: String,
    val checkedAt: String,
) {
    fun title(languageTag: String): String = if (languageTag == "fa") titleFa else titleEn
}

enum class OccasionEvidence(val key: String) {
    SOURCED("sourced"), TRADITIONAL("traditional"), GUIDANCE("guidance"), LEGACY("legacy");
}

/** No invented annual date: these school activities must be scheduled from the local circular. */
data class SchoolProgram(
    val id: String,
    val titleFa: String,
    val titleEn: String,
    val noteFa: String,
    val noteEn: String,
    val sourceIds: List<String>,
) {
    fun title(languageTag: String): String = if (languageTag == "fa") titleFa else titleEn
    fun note(languageTag: String): String = if (languageTag == "fa") noteFa else noteEn
}
