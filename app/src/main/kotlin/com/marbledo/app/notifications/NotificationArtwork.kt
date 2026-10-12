package com.marble098.marbledo.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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
import kotlin.math.min
import kotlin.math.roundToInt

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
 * mood mark, and a colored date badge used as the large icon. Everything is generated on-device so
 * the icon is different every single day without shipping thirty-one static assets.
 *
 * The status bar gives an icon a narrow strip of pixels, so the day number has to win the space
 * fight outright:
 *
 *  * the bitmap is generated at the size the status bar actually draws ([STATUS_ICON_DP]) and with
 *    a matching density, so Android renders it one-to-one instead of blurring a 96dp artwork down
 *    to a quarter of its size;
 *  * the digits are measured by their ink box and stretched along whichever axis is still free, so
 *    two-digit days are as tall as one-digit days instead of leaving half the icon empty;
 *  * strokes are thickened with a fill-and-stroke pass, because hairline Persian strokes disappear
 *    when a system draws the icon at eighteen pixels.
 *
 * The mood is a small mark under the number; it never takes space from the digits.
 */
object NotificationArtwork {

    /** The status bar draws its small icons at roughly this size. */
    const val STATUS_ICON_DP = 24f

    /** Extra resolution used for the in-app previews, where a crisp bitmap is worth the pixels. */
    const val PREVIEW_ICON_DP = 48f

    /** Unit square every mood glyph is authored in, before it is mapped onto its box. */
    private val UNIT_RECT = RectF(0f, 0f, 1f, 1f)

    /** Text size the ink boxes are measured at; ratios are size independent. */
    private const val PROBE_TEXT_SIZE = 100f

    /** How much a digit may be stretched along its free axis to fill the icon. */
    private const val MAX_ANISOTROPY = 1.38f

    /** Fraction of the icon height reserved for the mood mark when there is one to draw. */
    private const val MOOD_BAND_FRACTION = 0.16f

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

    /**
     * The generated status-bar icon, also used to preview the styles inside Settings. [sizeDp] is
     * the dp size the bitmap should occupy; the status bar itself asks for [STATUS_ICON_DP].
     */
    fun statusBitmap(
        context: Context,
        dayDigits: String,
        mood: NotificationMood,
        style: StatusBarIconStyle = StatusBarIconStyle.DEFAULT,
        sizeDp: Float = STATUS_ICON_DP,
    ): Bitmap? = when (style) {
        StatusBarIconStyle.ICON_ONLY -> null
        StatusBarIconStyle.BOLD_DIGITS -> digitsBitmap(context, dayDigits, mood, sizeDp)
        StatusBarIconStyle.CALENDAR_PAGE -> calendarPageBitmap(context, dayDigits, mood, sizeDp)
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

            // The same ink-filling layout the status icon uses, so the two always agree.
            drawDayNumber(
                canvas = canvas,
                text = dayDigits,
                box = RectF(size * 0.14f, size * 0.34f, size * 0.86f, size * 0.82f),
                typeface = vazirTypeface(context, 700),
                color = Color.WHITE,
            )

            if (mood == NotificationMood.HOLIDAY || mood == NotificationMood.RELIGIOUS) {
                val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 210 }
                canvas.drawRoundRect(RectF(96f, 216f, 160f, 228f), 6f, 6f, chipPaint)
            }
            bitmap
        }.getOrNull()

    /**
     * Digits-first style: the number fills the icon and the mood sits in a slim band underneath.
     * This is the style that answers "the day number is too small to see".
     */
    private fun digitsBitmap(context: Context, dayDigits: String, mood: NotificationMood, sizeDp: Float): Bitmap? =
        runCatching {
            val icon = squareIcon(context, sizeDp)
            val size = icon.size
            val moodBand = if (mood == NotificationMood.QUIET) 0f else size * MOOD_BAND_FRACTION
            drawDayNumber(
                canvas = icon.canvas,
                text = dayDigits,
                box = RectF(size * 0.025f, size * 0.015f, size * 0.975f, size - moodBand - size * 0.01f),
                typeface = vazirTypeface(context, 900),
                color = Color.WHITE,
            )
            if (moodBand > 0f) {
                drawMoodMark(
                    canvas = icon.canvas,
                    tint = Color.WHITE,
                    mood = mood,
                    box = RectF(size * 0.32f, size - moodBand, size * 0.68f, size - size * 0.012f),
                )
            }
            icon.bitmap
        }.getOrNull()

    /** Classic calendar page: frame, rings and the mood glyph above a day number that fills the page. */
    private fun calendarPageBitmap(context: Context, dayDigits: String, mood: NotificationMood, sizeDp: Float): Bitmap? =
        runCatching {
            val icon = squareIcon(context, sizeDp)
            val size = icon.size
            val unit = size / 108f
            val white = Color.WHITE

            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                style = Paint.Style.STROKE
                strokeWidth = 5f * unit
            }
            icon.canvas.drawRoundRect(
                RectF(9f * unit, 16f * unit, 99f * unit, 100f * unit),
                14f * unit,
                14f * unit,
                frame,
            )

            val rings = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                strokeWidth = 7f * unit
                strokeCap = Paint.Cap.ROUND
            }
            icon.canvas.drawLine(31f * unit, 7f * unit, 31f * unit, 23f * unit, rings)
            icon.canvas.drawLine(77f * unit, 7f * unit, 77f * unit, 23f * unit, rings)

            if (mood != NotificationMood.QUIET) {
                val mark = 26f * unit
                drawMoodMark(
                    canvas = icon.canvas,
                    tint = white,
                    mood = mood,
                    box = RectF(size / 2f - mark / 2f, 22f * unit, size / 2f + mark / 2f, 22f * unit + mark),
                )
            }

            // The page body keeps almost all of its 108-unit grid for the number itself.
            drawDayNumber(
                canvas = icon.canvas,
                text = dayDigits,
                box = RectF(15f * unit, 50f * unit, 93f * unit, 95f * unit),
                typeface = vazirTypeface(context, 800),
                color = white,
            )
            icon.bitmap
        }.getOrNull()

    /** A transparent square bitmap whose density makes it exactly [sizeDp] dp wide. */
    private class SquareIcon(val bitmap: Bitmap, val canvas: Canvas, val size: Float)

    private fun squareIcon(context: Context, sizeDp: Float): SquareIcon {
        val metrics = context.resources.displayMetrics
        val pixels = (sizeDp * metrics.density).roundToInt().coerceIn(16, 1024)
        val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
        // Matching the density is what turns the generated pixels into 1:1 status-bar pixels.
        bitmap.density = metrics.densityDpi
        return SquareIcon(bitmap, Canvas(bitmap), pixels.toFloat())
    }

    /** The tight ink box of the number at the text size that fits [box], plus that text size. */
    private class DayNumber(val textSize: Float, val ink: RectF)

    /**
     * Draws [text] so its ink fills [box]: the tighter axis is fitted, and the axis that is still
     * free is stretched by at most [MAX_ANISOTROPY]. Without that stretch a two-digit day would use
     * barely half of the icon height, because two digits side by side are much wider than tall.
     */
    private fun drawDayNumber(canvas: Canvas, text: String, box: RectF, typeface: Typeface, color: Int) {
        if (text.isBlank() || box.width() <= 0f || box.height() <= 0f) return
        val fitted = fitDayNumber(text, typeface, box.width(), box.height())
        val ink = fitted.ink
        val stretchX = (box.width() / ink.width().coerceAtLeast(1f)).coerceIn(1f, MAX_ANISOTROPY)
        val stretchY = (box.height() / ink.height().coerceAtLeast(1f)).coerceIn(1f, MAX_ANISOTROPY)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.typeface = typeface
            textSize = fitted.textSize
            textAlign = Paint.Align.LEFT
            style = Paint.Style.FILL_AND_STROKE
            // Thickening the outline keeps hairline Persian strokes visible at 18dp.
            strokeWidth = fitted.textSize * 0.085f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val centerX = box.centerX()
        val centerY = box.centerY()
        val drawX = centerX - ink.centerX()
        val baseline = centerY - (ink.top + ink.bottom) / 2f
        canvas.save()
        // Scale about the box centre so the measured ink lands exactly inside the box.
        canvas.scale(stretchX, stretchY, centerX, centerY)
        canvas.drawText(text, drawX, baseline, paint)
        canvas.restore()
    }

    /**
     * Largest text size at which the ink of [text] still fits [boxWidth] by [boxHeight]. Measuring
     * the ink rather than the font's line box is what lets "8" and "28" fill the icon equally.
     */
    private fun fitDayNumber(text: String, typeface: Typeface, boxWidth: Float, boxHeight: Float): DayNumber {
        val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = PROBE_TEXT_SIZE
        }
        val probeBounds = Rect()
        probe.getTextBounds(text, 0, text.length, probeBounds)
        val inkWidth = probeBounds.width().coerceAtLeast(1).toFloat() / PROBE_TEXT_SIZE
        val inkHeight = probeBounds.height().coerceAtLeast(1).toFloat() / PROBE_TEXT_SIZE
        val textSize = min(boxWidth / inkWidth, boxHeight / inkHeight)
        val bounds = Rect()
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSize
        }.getTextBounds(text, 0, text.length, bounds)
        return DayNumber(textSize, RectF(bounds))
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
