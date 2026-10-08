package com.marble098.marbledo.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import com.marble098.marbledo.R
import com.marble098.marbledo.core.designsystem.R as DesignR

/** The state the persistent notification reflects in its artwork and accent color. */
enum class NotificationMood {
    /** Plain date only: no occasions and nothing due today. */
    QUIET,

    /** At least one dated task is due today. */
    TASKS_DUE,

    /** Today carries an occasion from any enabled category. */
    OCCASION,

    /** Today carries a religious (lunar) occasion. */
    RELIGIOUS,

    /** Today is an official holiday. */
    HOLIDAY,
}

/**
 * Draws the "smart" notification artwork: a status-bar icon that shows today's day number with a
 * mood glyph, and a colored date badge used as the large icon. Everything is generated on-device
 * so the icon is different every single day without shipping thirty-one static assets.
 */
object NotificationArtwork {

    fun mood(
        hasHoliday: Boolean,
        hasReligiousOccasion: Boolean,
        hasOccasion: Boolean,
        tasksDueToday: Int,
    ): NotificationMood = when {
        hasHoliday -> NotificationMood.HOLIDAY
        hasReligiousOccasion -> NotificationMood.RELIGIOUS
        hasOccasion -> NotificationMood.OCCASION
        tasksDueToday > 0 -> NotificationMood.TASKS_DUE
        else -> NotificationMood.QUIET
    }

    fun accentColor(context: Context, mood: NotificationMood): Int = when (mood) {
        NotificationMood.HOLIDAY -> color(context, R.color.notification_holiday)
        NotificationMood.RELIGIOUS -> color(context, R.color.notification_religious)
        NotificationMood.OCCASION -> color(context, R.color.notification_occasion)
        NotificationMood.TASKS_DUE -> color(context, R.color.notification_tasks)
        NotificationMood.QUIET -> color(context, R.color.notification_quiet)
    }

    /** Static vector fallback shown when the dynamic bitmap cannot be drawn. */
    fun vectorIconRes(mood: NotificationMood): Int = when (mood) {
        NotificationMood.HOLIDAY -> R.drawable.ic_stat_calendar_flag
        NotificationMood.RELIGIOUS -> R.drawable.ic_stat_calendar_moon
        NotificationMood.OCCASION -> R.drawable.ic_stat_calendar_star
        NotificationMood.TASKS_DUE -> R.drawable.ic_stat_calendar_tasks
        NotificationMood.QUIET -> R.drawable.ic_stat_calendar
    }

    /**
     * Status-bar icon: an alpha-only calendar page holding today's [dayDigits] plus a small glyph
     * that encodes the mood (star, crescent, flag or check). Falls back to the mood vector when a
     * device cannot render the generated bitmap.
     */
    fun smallIcon(context: Context, dayDigits: String, mood: NotificationMood): IconCompat {
        val bitmap = statusBitmap(context, dayDigits, mood)
        return if (bitmap != null) {
            IconCompat.createWithBitmap(bitmap)
        } else {
            IconCompat.createWithResource(context, vectorIconRes(mood))
        }
    }

    /** Colored large icon: gradient date badge with the day number and month name. */
    fun dateBadge(context: Context, dayDigits: String, monthLabel: String, mood: NotificationMood): Bitmap? =
        runCatching {
            val size = 256
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val accent = accentColor(context, mood)
            val deep = ColorUtils.blendARGB(accent, Color.BLACK, 0.38f)
            val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    size.toFloat(),
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.18f),
                    deep,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 56f, 56f, plate)

            val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 225
                textAlign = Paint.Align.CENTER
                typeface = vazirTypeface(context, 600)
                textSize = 34f
            }
            canvas.drawText(monthLabel, size / 2f, 72f, monthPaint)

            val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = vazirTypeface(context, 700)
                textSize = 118f
            }
            val measured = dayPaint.measureText(dayDigits)
            if (measured > 186f) {
                dayPaint.textSize = 118f * (186f / measured)
            }
            canvas.drawText(dayDigits, size / 2f, 196f, dayPaint)

            if (mood == NotificationMood.HOLIDAY || mood == NotificationMood.RELIGIOUS) {
                val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 210 }
                canvas.drawRoundRect(RectF(96f, 216f, 160f, 228f), 6f, 6f, chipPaint)
            }
            bitmap
        }.getOrNull()

    private fun statusBitmap(context: Context, dayDigits: String, mood: NotificationMood): Bitmap? =
        runCatching {
            val size = 108
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val white = Color.WHITE

            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                style = Paint.Style.STROKE
                strokeWidth = 7f
            }
            canvas.drawRoundRect(RectF(10f, 18f, 98f, 98f), 16f, 16f, frame)

            val rings = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(32f, 8f, 32f, 24f, rings)
            canvas.drawLine(76f, 8f, 76f, 24f, rings)

            drawMoodGlyph(canvas, white, mood)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                textAlign = Paint.Align.CENTER
                typeface = vazirTypeface(context, 600)
                textSize = if (mood == NotificationMood.QUIET) 52f else 44f
            }
            val maxWidth = 70f
            val measured = textPaint.measureText(dayDigits)
            if (measured > maxWidth) {
                textPaint.textSize *= maxWidth / measured
            }
            val centerY = if (mood == NotificationMood.QUIET) 62f else 68f
            val baseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(dayDigits, 54f, baseline, textPaint)
            bitmap
        }.getOrNull()

    /** Compact monochrome marks that keep the status icon recognizable for each mood. */
    private fun drawMoodGlyph(canvas: Canvas, tint: Int, mood: NotificationMood) {
        when (mood) {
            NotificationMood.QUIET, NotificationMood.TASKS_DUE -> Unit
            NotificationMood.OCCASION -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = tint }
                val path = Path().apply {
                    moveTo(54f, 26f)
                    lineTo(58.2f, 34.2f)
                    lineTo(66.8f, 35.2f)
                    lineTo(60.9f, 41f)
                    lineTo(62.3f, 49.6f)
                    lineTo(54f, 45.2f)
                    lineTo(45.7f, 49.6f)
                    lineTo(47.1f, 41f)
                    lineTo(41.2f, 35.2f)
                    lineTo(49.8f, 34.2f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            NotificationMood.RELIGIOUS -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = tint
                    style = Paint.Style.FILL
                }
                // Crescent: the left side of a large disc with a smaller disc carved out of it.
                val path = Path().apply {
                    moveTo(61f, 25.9f)
                    arcTo(RectF(40f, 24f, 68f, 52f), 300f, -240f)
                    arcTo(RectF(51f, 27f, 73f, 49f), 95f, 170f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            NotificationMood.HOLIDAY -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = tint }
                val pole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = tint
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = 5f
                }
                canvas.drawLine(43f, 24f, 43f, 52f, pole)
                val path = Path().apply {
                    moveTo(46f, 25f)
                    lineTo(68f, 25f)
                    lineTo(61.5f, 33f)
                    lineTo(68f, 41f)
                    lineTo(46f, 41f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun color(context: Context, resId: Int): Int =
        ResourcesCompat.getColor(context.resources, resId, context.theme)

    /**
     * Loads the bundled Vazirmatn face and, on API 28+, picks the requested variable weight so the
     * generated artwork matches the in-app typography ladder.
     */
    private fun vazirTypeface(context: Context, weight: Int): Typeface {
        val base = ResourcesCompat.getFont(context, DesignR.font.vazirmatn_variable)
            ?: Typeface.DEFAULT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, weight, false)
        } else {
            base
        }
    }
}
