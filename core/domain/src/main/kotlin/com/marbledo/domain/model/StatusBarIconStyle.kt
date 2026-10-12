package com.marbledo.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * How the day number is drawn inside the persistent status-bar date icon.
 *
 * The status bar gives an icon a strip roughly eighteen pixels tall, so the day number has to be
 * legible at that size and nothing else may compete with it. These looks trade framing for digit
 * size — from the plain number, through outlined or filled containers, to the classic calendar
 * page — and every one of them is generated at the exact pixel size the status bar renders, with
 * the mood of the day reduced to one small mark.
 */
@Serializable
enum class StatusBarIconStyle {
    /** Default: only the day number, drawn as large and as clean as the icon allows. */
    NUMERAL,

    /** The number inside a rounded outline, like a stamped tile. */
    FRAME,

    /** The number punched out of a filled rounded square: the boldest silhouette in the set. */
    TILE,

    /** The number punched out of a filled disc. */
    DISC,

    /** Classic calendar page: two rings, the mood in the header and the number in the body. */
    PAGE,

    /** No generated bitmap: the plain vector calendar icon, with no day number at all. */
    ICON_ONLY,
    ;

    companion object {
        val DEFAULT = NUMERAL

        /**
         * Ids are persisted, so the names of the first generation ("BOLD_DIGITS", "CALENDAR_PAGE")
         * still resolve; [BOLD_DIGITS][NUMERAL] was the old default and keeps its meaning.
         */
        private val legacyIds = mapOf(
            "BOLD_DIGITS" to NUMERAL,
            "CALENDAR_PAGE" to PAGE,
        )

        fun from(id: String): StatusBarIconStyle =
            entries.firstOrNull { it.name == id } ?: legacyIds[id] ?: DEFAULT

        fun isKnown(id: String): Boolean = entries.any { it.name == id } || legacyIds.containsKey(id)
    }
}

/**
 * Serializes the style by name and reads it back through [StatusBarIconStyle.from], so a backup
 * written by a release that shipped the first generation of looks still restores instead of failing
 * the whole import on one renamed enum constant.
 */
object StatusBarIconStyleSerializer : KSerializer<StatusBarIconStyle> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.marbledo.domain.model.StatusBarIconStyle", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StatusBarIconStyle) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): StatusBarIconStyle = StatusBarIconStyle.from(decoder.decodeString())
}
