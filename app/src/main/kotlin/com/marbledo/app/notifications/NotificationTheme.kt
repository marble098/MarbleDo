package com.marble098.marbledo.notifications

import android.content.Context
import android.content.res.Configuration
import com.marbledo.core.designsystem.MarblePalettes
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.AppThemeMode

/**
 * The accent every notification uses, taken from the app theme.
 *
 * Task reminders and calendar alerts used to spend a hard-coded `@color/marble_accent` on
 * `setColor()`, so a user on Ocean, Forest, Sunset or Rose still got the indigo notification chrome.
 * The colour now comes from the same palette table the screens and the widgets use, which is what
 * makes "change the theme" mean the whole app and not only the screens.
 *
 * Mood colours are untouched on purpose: a holiday stays red in the status bar and on the date badge
 * because that colour carries meaning, not branding.
 */
internal fun notificationAccent(context: Context, settings: AppSettings): Int =
    notificationAccent(context, settings.themeMode)

internal fun notificationAccent(context: Context, mode: AppThemeMode): Int {
    val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val dark = MarblePalettes.isDark(mode.name, systemDark)
    return MarblePalettes.resolve(mode, dark, context.applicationContext).primary.toInt()
}
