package com.marble098.marbledo.notifications

import android.content.Context
import android.content.res.Configuration
import com.marbledo.core.designsystem.MarbleContrast
import com.marbledo.core.designsystem.MarblePalettes
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.AppThemeMode

/**
 * The palette the shade is painted from, derived from the app theme rather than from SystemUI.
 *
 * A custom `RemoteViews` card carries its own colours, but it is still drawn inside SystemUI's
 * shade, which follows the wallpaper or the device theme. The card therefore may not depend on the
 * shade for anything: it paints its own plate ([background]), states every text colour explicitly,
 * and derives those colours from the same palette the screens use. That is what makes one theme
 * switch repaint the whole app instead of only the Compose surfaces.
 *
 * Every pair below is checked with [MarbleContrast]: text clears WCAG AA for body text (4.5:1) on
 * the plate, and accents keep their hue while being lifted or deepened until they do.
 */
internal data class NotificationPalette(
    /** Opaque plate of the card; the shade behind it cannot change how the text reads. */
    val background: Long,
    val text: Long,
    val muted: Long,
    val accent: Long,
    val accentSoft: Long,
    val hairline: Long,
    val isDark: Boolean,
    /** "Overdue" reads as a warning: a deep red-orange that never shares the accent's hue. */
    val overdue: Long = 0L,
    /** "Today" is the brand accent, i.e. the day the user is living in. */
    val today: Long = 0L,
    /** "Occasion" is a calm neutral purple, deliberately the opposite of the warning red. */
    val occasion: Long = 0L,
    /** A soft container for the overdue chip and the day badge; the ink on it is [overdueIcon]. */
    val overdueSoft: Long = 0L,
    /** White or near-black on [overdueSoft], chosen by contrast rather than assumed. */
    val overdueIcon: Long = 0L,
    /** The tinted container behind the occasion (priority 2) section. */
    val occasionSoft: Long = 0L,
    /** [accentSoft] composited onto the plate: a chip fill has to be opaque to hold ink. */
    val accentFill: Long = 0L,
    /** [occasionSoft] composited onto the plate: same reason. */
    val occasionFill: Long = 0L,
)

/**
 * The two plates the card can paint, matching `drawable/notification_card_plate_light.xml` and
 * `..._dark.xml` exactly.
 *
 * These are literals here for the same reason they are literals in the drawables: the plate is
 * chosen from the *app* theme's lightness, so it may not follow the night configuration. Having one
 * copy of each value means the palette can check every text colour against the plate the card
 * really paints, instead of against a `surface` the card does not use.
 */
internal const val NOTIFICATION_PLATE_LIGHT = 0xFFF2F1F6L
internal const val NOTIFICATION_PLATE_DARK = 0xFF26272EL

/**
 * The hue of "overdue", before it is lifted to read on a plate.
 *
 * A deep red-orange, not a shade of the brand accent: the old card painted "عقب‌افتاده" in a brown
 * that was a blend of the indigo accent and the day's mood, which is why it sat next to the purple
 * "مناسبت" label without any visible difference.
 */
internal const val NOTIFICATION_OVERDUE_HUE = 0xFFC33A22L

/** The hue of "occasion": a calm purple, a full hue step away from the warning red above. */
internal const val NOTIFICATION_OCCASION_HUE = 0xFF6B4FA8L

/** The app theme's own light/dark decision, not the system's. */
internal fun appIsDark(context: Context, settings: AppSettings): Boolean =
    MarblePalettes.isDark(settings.themeMode.name, systemIsDark(context))

internal fun systemIsDark(context: Context): Boolean =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

/**
 * Resolves the notification palette. [dark] is the app theme's decision, so a user on the pinned
 * DARK theme gets a dark card inside a light shade and vice versa — the card still looks like the
 * app, and every colour on it is checked against the plate the card actually paints.
 */
internal fun notificationPalette(
    context: Context,
    mode: AppThemeMode,
    dark: Boolean = MarblePalettes.isDark(mode.name, systemIsDark(context)),
): NotificationPalette {
    val app = MarblePalettes.resolve(mode, dark, context.applicationContext)
    // The plate the card paints, not `surface`: the two differ by a few steps, and the whole point
    // of this palette is that its contrasts are measured against the pixels that end up on screen.
    val plate = if (dark) NOTIFICATION_PLATE_DARK else NOTIFICATION_PLATE_LIGHT
    val accent = MarbleContrast.readable(app.primary, plate, MarbleContrast.TEXT_MINIMUM)
    // The three states the card colours: a warning red for what is late, the brand accent for
    // today, and a calm purple for occasions. They are *hues* that carry meaning, so each one
    // keeps its hue and is only moved in lightness until it reads on the plate — a red that is
    // merely darker indigo is the exact bug that made "overdue" and "occasion" look alike.
    val overdue = MarbleContrast.readable(NOTIFICATION_OVERDUE_HUE, plate, MarbleContrast.TEXT_MINIMUM)
    val occasion = MarbleContrast.readable(NOTIFICATION_OCCASION_HUE, plate, MarbleContrast.TEXT_MINIMUM)
    // The warning disc: the hue as a fill, with the ink picked against the fill.
    val overdueSoft = MarbleContrast.blend(overdue, plate, 0.82)
    val accentSoft = MarbleContrast.withAlpha(accent, if (MarbleContrast.relativeLuminance(plate) < 0.4) 0.16 else 0.09)
    val occasionSoft = MarbleContrast.withAlpha(occasion, if (MarbleContrast.relativeLuminance(plate) < 0.4) 0.18 else 0.10)
    return NotificationPalette(
        background = plate,
        text = MarbleContrast.readable(app.onSurface, plate, MarbleContrast.TEXT_MINIMUM),
        muted = MarbleContrast.readable(app.onSurfaceVariant, plate, MarbleContrast.TEXT_MINIMUM),
        accent = accent,
        // A tinted section plate has to stay visible on the card without turning into a second
        // background, so its alpha follows the plate's own lightness.
        accentSoft = accentSoft,
        hairline = MarbleContrast.withAlpha(accent, if (MarbleContrast.relativeLuminance(plate) < 0.4) 0.34 else 0.24),
        isDark = dark,
        overdue = overdue,
        today = accent,
        occasion = occasion,
        overdueSoft = overdueSoft,
        overdueIcon = MarbleContrast.bestInk(overdueSoft),
        occasionSoft = occasionSoft,
        // A `RemoteViews` chip is a bitmap, and a bitmap cannot be translucent and still hold
        // ink: the alpha versions above are for a Compose background, where the plate shows
        // through. These are the same tints already composited onto the plate the card paints.
        accentFill = MarbleContrast.alphaOver(accent, plate, if (MarbleContrast.relativeLuminance(plate) < 0.4) 0.16 else 0.09),
        occasionFill = MarbleContrast.alphaOver(occasion, plate, if (MarbleContrast.relativeLuminance(plate) < 0.4) 0.18 else 0.10),
    )
}

/**
 * The accent every notification uses, taken from the app theme.
 *
 * Task reminders and calendar alerts used to spend a hard-coded `@color/marble_accent` on
 * `setColor()`, so a user on Ocean, Forest, Sunset or Rose still got the indigo notification chrome.
 * The colour now comes from the same palette table the screens and the widgets use, which is what
 * makes "change the theme" mean the whole app and not only the screens.
 *
 * Only the mood chip keeps its semantic colour (a holiday stays red because that colour carries
 * meaning, not branding); the badge plate, the rules, the lines and the shapes around it are all
 * painted from the theme accent now.
 */
internal fun notificationAccent(context: Context, settings: AppSettings): Int =
    notificationAccent(context, settings.themeMode)

internal fun notificationAccent(context: Context, mode: AppThemeMode): Int =
    notificationPalette(context, mode).accent.toInt()
