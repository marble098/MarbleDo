package com.marbledo.domain.util

import com.marbledo.domain.model.NumeralMode

object TextNormalizer {
    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    fun digitsToLatin(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when {
                char in '۰'..'۹' -> append((char.code - '۰'.code).toString())
                char in '٠'..'٩' -> append((char.code - '٠'.code).toString())
                else -> append(char)
            }
        }
    }

    fun toPersianDigits(value: String): String = mapDigits(value, persianDigits)
    fun toArabicDigits(value: String): String = mapDigits(value, arabicDigits)
    fun formatDigits(value: String, mode: NumeralMode): String = when (mode) {
        NumeralMode.PERSIAN -> toPersianDigits(value)
        NumeralMode.LATIN -> digitsToLatin(value)
        NumeralMode.ARABIC -> toArabicDigits(value)
    }

    /** Search normalization unifies Arabic/Persian letter variants and treats ZWNJ as a space. */
    fun searchKey(value: String): String = value
        .lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('\u0649', 'ی')
        .replace('\u200c', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun mapDigits(value: String, digits: CharArray): String = buildString(value.length) {
        value.forEach { char ->
            when {
                char in '0'..'9' -> append(digits[char.code - '0'.code])
                char in '٠'..'٩' -> append(digits[char.code - '٠'.code])
                char in '۰'..'۹' -> append(digits[char.code - '۰'.code])
                else -> append(char)
            }
        }
    }
}
