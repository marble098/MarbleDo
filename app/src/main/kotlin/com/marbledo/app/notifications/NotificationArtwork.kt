package com.marble098.marbledo.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
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
import com.marbledo.domain.model.StatusBarIconStyle

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
 *
 * The status bar gives an icon only a narrow strip of pixels, so the day number is drawn to fill
 * as much of the canvas as possible: the size is solved from both the measured width *and* the
 * measured height, strokes are thickened with a fill-and-stroke pass, and the mood becomes a small
 * corner mark instead of a glyph sitting on top of the digits.
 */
object NotificationArtwork {

    /** Bitmap edge of a generated status-bar icon. The platform scales it down to the strip height. */
    private const val STATUS_ICON_SIZE = 96

    /** Unit square every mood glyph is authored in, before it is mapped onto its box. */
    private val UNIT_RECT = RectF(0f, 0f, 1f, 1f)

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
     * Status-bar icon for today. [style] decides how much of the icon the day number may use;
     * every style falls back to the mood vector when a device cannot render the generated bitmap.
     */
    fun smallIcon(
        context: Context,
        dayDigits: String,
        mood: NotificationMood,
        style: StatusBarIconStyle = StatusBarIconStyle.DEFAULT,
    ): IconCompat {
        if (style == StatusBarIconStyle.ICON_ONLY) return IconCompat.createWithResource(context, vectorIconRes(mood))
        val bitmap = statusBitmap(context, dayDigits, mood, style)
        return if (bitmap != null) {
            IconCompat.createWithBitmap(bitmap)
        } else {
            IconCompat.createWithResource(context, vectorIconRes(mood))
        }
    }

    /** The generated status-bar icon, also used to preview the styles inside Settings. */
    fun statusBitmap(
        context: Context,
        dayDigits: String,
        mood: NotificationMood,
        style: StatusBarIconStyle = StatusBarIconStyle.DEFAULT,
    ): Bitmap? = when (style) {
        StatusBarIconStyle.ICON_ONLY -> null
        StatusBarIconStyle.BOLD_DIGITS -> boldDigitsBitmap(context, dayDigits, mood)
        StatusBarIconStyle.CALENDAR_PAGE -> calendarPageBitmap(context, dayDigits, mood)
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

    /**
     * Roomy style: the digits are the icon. They are solved against both the width and the height
     * of the canvas, painted with a fill-and-stroke pass so thin Vazirmatn strokes survive being
     * scaled down into the status bar, and the mood sits as a small mark in the top corner.
     */
    private fun boldDigitsBitmap(context: Context, dayDigits: String, mood: NotificationMood): Bitmap? =
        runCatching {
            val size = STATUS_ICON_SIZE
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val white = Color.WHITE
            val typeface = vazirTypeface(context, 900)

            // Digits may use almost the whole square; a little room is left for the corner mark.
            val boxWidth = size * 0.95f
            val boxHeight = size * (if (mood == NotificationMood.QUIET) 0.97f else 0.90f)
            val textSize = fitTextSize(dayDigits, typeface, boxWidth, boxHeight)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                textAlign = Paint.Align.CENTER
                this.typeface = typeface
                this.textSize = textSize
                // Thickening the outline keeps hairline Persian strokes visible at 18dp.
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = textSize * 0.06f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            val centerY = size * if (mood == NotificationMood.QUIET) 0.5f else 0.55f
            val baseline = centerY - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(dayDigits, size / 2f, baseline, paint)

            if (mood != NotificationMood.QUIET) {
                val mark = size * 0.30f
                drawMoodMark(
                    canvas = canvas,
                    tint = white,
                    mood = mood,
                    box = RectF(size - mark - size * 0.02f, size * 0.01f, size - size * 0.02f, mark + size * 0.01f),
                )
            }
            bitmap
        }.getOrNull()

    /** Classic calendar page: frame, rings and the mood glyph above the day number. */
    private fun calendarPageBitmap(context: Context, dayDigits: String, mood: NotificationMood): Bitmap? =
        runCatching {
            val size = STATUS_ICON_SIZE
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val white = Color.WHITE
            val unit = size / 108f

            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                style = Paint.Style.STROKE
                strokeWidth = 7f * unit
            }
            canvas.drawRoundRect(
                RectF(10f * unit, 18f * unit, 98f * unit, 98f * unit),
                16f * unit,
                16f * unit,
                frame,
            )

            val rings = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                strokeWidth = 8f * unit
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(32f * unit, 8f * unit, 32f * unit, 24f * unit, rings)
            canvas.drawLine(76f * unit, 8f * unit, 76f * unit, 24f * unit, rings)

            if (mood != NotificationMood.QUIET && mood != NotificationMood.TASKS_DUE) {
                val mark = 32f * unit
                drawMoodMark(
                    canvas = canvas,
                    tint = white,
                    mood = mood,
                    box = RectF(size / 2f - mark / 2f, 26f * unit, size / 2f + mark / 2f, 26f * unit + mark),
                )
            }

            val boxWidth = 76f * unit
            val boxHeight = (if (mood == NotificationMood.QUIET || mood == NotificationMood.TASKS_DUE) 66f else 54f) * unit
            val textSize = fitTextSize(dayDigits, vazirTypeface(context, 800), boxWidth, boxHeight)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                textAlign = Paint.Align.CENTER
                typeface = vazirTypeface(context, 800)
                this.textSize = textSize
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = textSize * 0.05f
                strokeJoin = Paint.Join.ROUND
            }
            val centerY = (if (mood == NotificationMood.QUIET || mood == NotificationMood.TASKS_DUE) 64f else 74f) * unit
            val baseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(dayDigits, size / 2f, baseline, textPaint)

            if (mood == NotificationMood.TASKS_DUE) {
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white }
                canvas.drawCircle(size / 2f, 30f * unit, 5f * unit, dot)
            }
            bitmap
        }.getOrNull()

    /**
     * Largest text size at which [text] still fits [boxWidth] by [boxHeight]. Measuring the height
     * as well as the width is what keeps "8" and "28" equally tall inside the status bar.
     */
    private fun fitTextSize(text: String, typeface: Typeface, boxWidth: Float, boxHeight: Float): Float {
        val probeSize = 100f
        val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = probeSize
            textAlign = Paint.Align.CENTER
        }
        val width = probe.measureText(text).coerceAtLeast(1f)
        val height = (probe.descent() - probe.ascent()).coerceAtLeast(1f)
        return probeSize * minOf(boxWidth / width, boxHeight / height)
    }

    /** Compact monochrome marks that keep the status icon recognizable for each mood. */
    private fun drawMoodMark(canvas: Canvas, tint: Int, mood: NotificationMood, box: RectF) {
        when (mood) {
            NotificationMood.QUIET -> Unit
            NotificationMood.TASKS_DUE -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
                val radius = box.width() * 0.17f
                val centerY = box.centerY()
                val step = box.width() * 0.30f
                canvas.drawCircle(box.centerX() - step, centerY, radius, paint)
                canvas.drawCircle(box.centerX(), centerY, radius, paint)
                canvas.drawCircle(box.centerX() + step, centerY, radius, paint)
            }
            NotificationMood.OCCASION -> canvas.drawUnitPath(starPath(), box, fillPaint(tint))
            NotificationMood.RELIGIOUS -> canvas.drawUnitPath(crescentPath(), box, fillPaint(tint))
            NotificationMood.HOLIDAY -> {
                val pole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = tint
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = box.width() * 0.12f
                }
                canvas.drawLine(
                    box.left + box.width() * 0.26f,
                    box.top + box.height() * 0.10f,
                    box.left + box.width() * 0.26f,
                    box.bottom - box.height() * 0.06f,
                    pole,
                )
                canvas.drawUnitPath(flagPath(), box, fillPaint(tint))
            }
        }
    }

    private fun fillPaint(tint: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }

    /** Maps a path authored in the unit square into [box], preserving its aspect ratio. */
    private fun Canvas.drawUnitPath(path: Path, box: RectF, paint: Paint) {
        val matrix = Matrix().apply { setRectToRect(UNIT_RECT, box, Matrix.ScaleToFit.CENTER) }
        drawPath(Path(path).apply { transform(matrix) }, paint)
    }

    /** Five-pointed star centered in the unit square. */
    private fun starPath(): Path {
        val path = Path()
        for (index in 0 until 10) {
            val radius = if (index % 2 == 0) 0.5f else 0.2f
            val angle = Math.toRadians(-90.0 + index * 36.0)
            val x = 0.5f + (radius * Math.cos(angle)).toFloat()
            val y = 0.5f + (radius * Math.sin(angle)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** Crescent: a disc with a smaller, offset disc subtracted from it. */
    private fun crescentPath(): Path {
        val outer = Path().apply { addCircle(0.5f, 0.5f, 0.5f, Path.Direction.CW) }
        val inner = Path().apply { addCircle(0.72f, 0.38f, 0.44f, Path.Direction.CW) }
        return Path().apply { op(outer, inner, Path.Op.DIFFERENCE) }
    }

    /** Flag body; the pole is drawn separately so it can be a round-capped line. */
    private fun flagPath(): Path = Path().apply {
        moveTo(0.32f, 0.10f)
        lineTo(0.92f, 0.10f)
        lineTo(0.74f, 0.34f)
        lineTo(0.92f, 0.58f)
        lineTo(0.32f, 0.58f)
        close()
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
