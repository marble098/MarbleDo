package com.marbledo.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarbleTypographyTest {
    private val allWeights = listOf(
        FontWeight.Thin,
        FontWeight.ExtraLight,
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
        FontWeight.Black,
    )

    private fun Typography.weightsInUse(): Set<FontWeight> = listOf(
        displayLarge, displayMedium, displaySmall,
        headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall,
        bodyLarge, bodyMedium, bodySmall,
        labelLarge, labelMedium, labelSmall,
    ).mapTo(mutableSetOf()) { it.fontWeight ?: FontWeight.Normal }

    @Test
    fun `persian typography assigns every vazir weight to a dedicated slot`() {
        val used = PersianTypography.weightsInUse()
        allWeights.forEach { weight ->
            assertTrue(weight in used, "Vazir weight $weight is not used by PersianTypography")
        }
    }

    @Test
    fun `persian typography slots all use the bundled vazir family`() {
        val styles = listOf(
            PersianTypography.displayLarge, PersianTypography.displayMedium, PersianTypography.displaySmall,
            PersianTypography.headlineLarge, PersianTypography.headlineMedium, PersianTypography.headlineSmall,
            PersianTypography.titleLarge, PersianTypography.titleMedium, PersianTypography.titleSmall,
            PersianTypography.bodyLarge, PersianTypography.bodyMedium, PersianTypography.bodySmall,
            PersianTypography.labelLarge, PersianTypography.labelMedium, PersianTypography.labelSmall,
        )
        styles.forEach { style ->
            assertEquals(MarbleFontFamily, style.fontFamily, "A typography slot does not use MarbleFontFamily")
        }
    }

    @Test
    fun `named text roles map one-to-one onto the nine vazir weights`() {
        val roles = listOf(
            MarbleTextStyles.heroDigits,
            MarbleTextStyles.yearDigits,
            MarbleTextStyles.dateDigits,
            MarbleTextStyles.bodyPrimary,
            MarbleTextStyles.metaLabel,
            MarbleTextStyles.itemTitle,
            MarbleTextStyles.cardTitle,
            MarbleTextStyles.sectionTitle,
            MarbleTextStyles.screenTitle,
        )
        assertEquals(allWeights, roles.map { it.fontWeight })
        roles.forEach { role -> assertEquals(MarbleFontFamily, role.fontFamily) }
    }

    @Test
    fun `marble typography keeps platform family for english while persian uses vazir`() {
        assertEquals(MarbleFontFamily, marbleTypography("fa").bodyLarge.fontFamily)
        assertTrue(marbleTypography("en").bodyLarge.fontFamily != MarbleFontFamily)
    }
}
