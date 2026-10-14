package com.marble098.marbledo.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import com.marble098.marbledo.R
import com.marble098.marbledo.core.designsystem.R as DesignR
import com.marbledo.domain.model.StatusBarIconStyle
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Text size the ink boxes are measured at; ink ratios are size independent. */
private const val PROBE_TEXT_SIZE = 100f

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
 * The status bar gives an icon a strip of roughly eighteen pixels, and that budget is what the
 * artwork is designed around:
 *
 *  * the day number is measured and fitted by its **ink box**, so "۸" and "۲۸" fill the icon to the
 *    same height instead of the digits being stretched along one axis to fake a larger size, which
 *    is what made the first generation of this icon look like a blob;
 *  * the number is drawn at a single uniform scale, with no stroke baked on top of it, and the
 *    bitmap is generated at the exact pixel size for the display density and then supersampled, so
 *    the system renders crisp digits instead of a blurry resample;
 *  * every look reduces the mood of the day to one small mark that never steals space from the
 *    digits, and the looks that fill their shape punch the number out of it for the strongest
 *    possible silhouette.
 *
 * [StatusBarIconStyle] selects the look; the drawing itself lives in the `drawXxx` helpers below and
 * stays resolution independent (all geometry is a fraction of the icon side).
 */
object NotificationArtwork {

    /** The status bar draws its small icons at roughly this size. */
    const val STATUS_ICON_DP = 24f

    /** Extra resolution used for the in-app previews, where a crisp bitmap is worth the pixels. */
    const val PREVIEW_ICON_DP = 48f

    /** Unit square every mood glyph is authored in, before it is mapped onto its box. */
    private val UNIT_RECT = RectF(0f, 0f, 1f, 1f)

    /** Weight of the day number: heavy enough to survive the status bar, light enough to stay clean. */
    private const val DIGIT_WEIGHT = 700

    /** Space between two digits of a day number, in ems. */
    private const val DIGIT_GAP_EM = 0.06f

    /** Working bitmap budget and cap; the artwork is drawn larger and downsampled onto the icon. */
    private const val MAX_WORK_PIXELS = 512
    private const val MAX_SUPERSAMPLE = 4

    /** Fraction of the icon height the mood mark may take in the plain [StatusBarIconStyle.NUMERAL]. */
    private const val NUMERAL_MOOD_BAND = 0.24f

    /** Footprint of a mood mark relative to the icon side. */
    private const val MARK_FRACTION = 0.23f

    private val statusBitmapCache = LruCache<String, Bitmap>(28)
    private val dateBadgeCache = LruCache<String, Bitmap>(8)
    private val typefaceCache = ConcurrentHashMap<Int, Typeface>()
    @Volatile private var baseVazirTypeface: Typeface? = null

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
     * Status-bar icon for today. [style] decides how the day number is framed; every style falls
     * back to the mood vector when a device cannot render the generated bitmap.
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
    ): Bitmap? {
        if (style == StatusBarIconStyle.ICON_ONLY) return null
        return runCatching {
            val metrics = context.resources.displayMetrics
            val pixels = (sizeDp * metrics.density).roundToInt().coerceIn(16, 1024)
            val cacheKey = "${metrics.densityDpi}:$pixels:$dayDigits:${mood.name}:${style.name}"
            synchronized(statusBitmapCache) {
                statusBitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let { return@runCatching it }
            }
            // Drawing larger and downsampling is what keeps the small digits smooth: at 24dp a real
            // status bar is only about sixty pixels wide, where plain anti-aliasing aliases.
            val supersample = (MAX_WORK_PIXELS / pixels).coerceIn(1, MAX_SUPERSAMPLE)
            val work = pixels * supersample
            val bitmap = Bitmap.createBitmap(work, work, Bitmap.Config.ARGB_8888)
            val typeface = vazirTypeface(context, DIGIT_WEIGHT)
            val run = DigitRun(typeface, dayDigits, DIGIT_GAP_EM)
            val canvas = Canvas(bitmap)
            val side = work.toFloat()
            when (style) {
                StatusBarIconStyle.NUMERAL -> drawNumeral(canvas, run, mood, side)
                StatusBarIconStyle.FRAME -> drawFrame(canvas, run, mood, side)
                StatusBarIconStyle.TILE -> drawTile(canvas, run, mood, side, radiusFraction = 0.28f, insetFraction = 0.06f)
                StatusBarIconStyle.DISC -> drawTile(canvas, run, mood, side, radiusFraction = 0.5f, insetFraction = 0.045f)
                StatusBarIconStyle.PAGE -> drawPage(canvas, run, mood, side)
                StatusBarIconStyle.ICON_ONLY -> Unit
            }
            val icon = if (supersample == 1) bitmap else Bitmap.createScaledBitmap(bitmap, pixels, pixels, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
            // Matching the density is what turns the generated pixels into 1:1 status-bar pixels.
            icon.density = metrics.densityDpi
            synchronized(statusBitmapCache) {
                statusBitmapCache.put(cacheKey, icon)
            }
            icon
        }.getOrNull()
    }

    /** Colored large icon: gradient date badge with the day number and month name. */
    fun dateBadge(context: Context, dayDigits: String, monthLabel: String, mood: NotificationMood): Bitmap? =
        runCatching {
            val accent = accentColor(context, mood)
            val cacheKey = "$dayDigits:$monthLabel:${mood.name}:$accent"
            synchronized(dateBadgeCache) {
                dateBadgeCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let { return@runCatching it }
            }
            val size = 256
            val side = size.toFloat()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val deep = ColorUtils.blendARGB(accent, Color.BLACK, 0.38f)
            val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    side,
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.18f),
                    deep,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(RectF(0f, 0f, side, side), 56f, 56f, plate)

            val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 232
                textAlign = Paint.Align.CENTER
                typeface = vazirTypeface(context, 600)
            }
            monthPaint.textSize = fitTextSize(monthLabel, monthPaint, side * 0.72f, side * 0.16f)
            drawInkCentered(canvas, monthLabel, monthPaint, side / 2f, side * 0.22f)

            // The same ink-filling layout the status icon uses, so the two always agree.
            val run = DigitRun(vazirTypeface(context, DIGIT_WEIGHT), dayDigits, DIGIT_GAP_EM)
            val dayBox = RectF(side * 0.16f, side * 0.30f, side * 0.84f, side * 0.78f)
            run.draw(
                canvas = canvas,
                textSize = run.fittedSize(dayBox.width(), dayBox.height()),
                centerX = dayBox.centerX(),
                centerY = dayBox.centerY(),
                color = Color.WHITE,
            )
            drawMoodMark(
                canvas = canvas,
                mood = mood,
                centerX = side / 2f,
                centerY = side * 0.865f,
                size = side * 0.135f,
                color = Color.WHITE,
            )
            synchronized(dateBadgeCache) {
                dateBadgeCache.put(cacheKey, bitmap)
            }
            bitmap
        }.getOrNull()

    // ---------------------------------------------------------------------------------------
    // Looks. Every coordinate below is a fraction of the icon side, so the same code draws a
    // 24dp status icon and a 48dp Settings preview.
    // ---------------------------------------------------------------------------------------

    /** Plain number with a mood mark underneath: the largest digits of the set, nothing around them. */
    private fun drawNumeral(canvas: Canvas, run: DigitRun, mood: NotificationMood, side: Float) {
        val inset = 0.10f * side
        val band = moodBand(mood, NUMERAL_MOOD_BAND * side)
        val box = RectF(inset, inset, side - inset, side - inset - band)
        run.draw(
            canvas = canvas,
            textSize = run.fittedSize(box.width(), box.height()),
            centerX = box.centerX(),
            centerY = box.centerY(),
            color = Color.WHITE,
        )
        drawMoodMark(
            canvas = canvas,
            mood = mood,
            centerX = side / 2f,
            centerY = side - inset * 0.6f - band / 2f,
            size = MARK_FRACTION * side,
            color = Color.WHITE,
        )
    }

    /** The number inside a rounded outline, like a stamped tile. */
    private fun drawFrame(canvas: Canvas, run: DigitRun, mood: NotificationMood, side: Float) {
        val outline = 0.055f * side
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 0.07f * side
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawRoundRect(
            RectF(outline, outline, side - outline, side - outline),
            0.30f * side,
            0.30f * side,
            frame,
        )
        val band = moodBand(mood, 0.21f * side)
        val bottomInset = 0.135f * side
        val box = RectF(0.20f * side, 0.175f * side, 0.80f * side, side - bottomInset - band)
        run.draw(
            canvas = canvas,
            textSize = run.fittedSize(box.width(), box.height()),
            centerX = box.centerX(),
            centerY = box.centerY(),
            color = Color.WHITE,
        )
        drawMoodMark(
            canvas = canvas,
            mood = mood,
            centerX = side / 2f,
            centerY = side - bottomInset - band / 2f,
            size = MARK_FRACTION * 0.60f * side,
            color = Color.WHITE,
        )
    }

    /**
     * The number punched out of a filled shape: the strongest silhouette available in a status bar,
     * because the ink is a solid mass and the digits are the transparent part of it.
     */
    private fun drawTile(
        canvas: Canvas,
        run: DigitRun,
        mood: NotificationMood,
        side: Float,
        radiusFraction: Float,
        insetFraction: Float,
    ) {
        val inset = insetFraction * side
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val box = RectF(inset, inset, side - inset, side - inset)
        canvas.drawRoundRect(box, radiusFraction * side, radiusFraction * side, plate)

        val punch = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val pad = 0.185f * side
        val band = moodBand(mood, 0.26f * side)
        val digits = RectF(pad, pad, side - pad, side - pad - band)
        run.draw(
            canvas = canvas,
            textSize = run.fittedSize(digits.width(), digits.height()),
            centerX = digits.centerX(),
            centerY = digits.centerY(),
            paint = punch,
        )
        drawMoodMark(
            canvas = canvas,
            mood = mood,
            centerX = side / 2f,
            centerY = side - pad - band / 2f - 0.005f * side,
            size = MARK_FRACTION * 0.62f * side,
            paint = punch,
        )
    }

    /** Classic calendar page: frame, rings, the mood in the header and the number in the body. */
    private fun drawPage(canvas: Canvas, run: DigitRun, mood: NotificationMood, side: Float) {
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 0.055f * side
        }
        canvas.drawRoundRect(
            RectF(0.085f * side, 0.19f * side, 0.915f * side, 0.955f * side),
            0.16f * side,
            0.16f * side,
            outline,
        )
        val rings = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        listOf(0.28f, 0.72f).forEach { center ->
            canvas.drawRoundRect(
                RectF(
                    center * side - 0.042f * side,
                    0.035f * side,
                    center * side + 0.042f * side,
                    0.245f * side,
                ),
                0.042f * side,
                0.042f * side,
                rings,
            )
        }
        val header = if (mood == NotificationMood.QUIET) 0.31f else 0.38f
        val digits = RectF(0.21f * side, header * side, 0.79f * side, 0.90f * side)
        run.draw(
            canvas = canvas,
            textSize = run.fittedSize(digits.width(), digits.height()),
            centerX = digits.centerX(),
            centerY = digits.centerY(),
            color = Color.WHITE,
        )
        drawMoodMark(
            canvas = canvas,
            mood = mood,
            centerX = side / 2f,
            centerY = 0.325f * side,
            size = 0.145f * side,
            color = Color.WHITE,
        )
    }

    /** Height reserved for the mood mark, or zero for a quiet day that has no mark to draw. */
    private fun moodBand(mood: NotificationMood, band: Float): Float =
        if (mood == NotificationMood.QUIET) 0f else band

    // ---------------------------------------------------------------------------------------
    // Type
    // ---------------------------------------------------------------------------------------

    /**
     * A run of digits measured by the tight ink box of every character, so the number can be fitted
     * to a box on its binding axis alone. Measuring the ink rather than the font's line box is what
     * lets "۸" and "۲۸" fill the icon equally; digits are shapes that never join, so each one can be
     * placed on its own and the pair stays optically centred.
     */
    private class DigitRun(
        private val typeface: Typeface,
        private val text: String,
        private val gapEm: Float,
    ) {
        private val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = PROBE_TEXT_SIZE
            textAlign = Paint.Align.LEFT
        }

        /** Ink box of every character, in ems relative to the drawing origin. */
        private val inks: List<RectF> = text.map { character ->
            val bounds = Rect()
            probe.getTextBounds(character.toString(), 0, 1, bounds)
            RectF(
                bounds.left / PROBE_TEXT_SIZE,
                bounds.top / PROBE_TEXT_SIZE,
                bounds.right / PROBE_TEXT_SIZE,
                bounds.bottom / PROBE_TEXT_SIZE,
            )
        }

        private val widthEm: Float = inks.sumOf { it.width().toDouble() }.toFloat() +
            gapEm * max(0, text.length - 1)
        private val topEm: Float = inks.minOfOrNull { it.top } ?: 0f
        private val bottomEm: Float = inks.maxOfOrNull { it.bottom } ?: 0f

        /** Text size at which the run's ink exactly fills the box on its tighter axis. */
        fun fittedSize(boxWidth: Float, boxHeight: Float): Float {
            val widthEm = widthEm.coerceAtLeast(0.01f)
            val heightEm = (bottomEm - topEm).coerceAtLeast(0.01f)
            return min(boxWidth / widthEm, boxHeight / heightEm)
        }

        fun draw(
            canvas: Canvas,
            textSize: Float,
            centerX: Float,
            centerY: Float,
            color: Int = Color.WHITE,
            paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG),
        ) {
            if (text.isBlank() || textSize <= 0f) return
            paint.typeface = typeface
            paint.textAlign = Paint.Align.LEFT
            paint.isAntiAlias = true
            if (paint.xfermode == null) paint.color = color
            paint.textSize = textSize
            val gap = gapEm * textSize
            // Centre the run on its own ink, which is why a two-digit day is not pushed off centre
            // by the empty side bearings around the glyphs.
            val baseline = centerY - (topEm + bottomEm) / 2f * textSize
            var x = centerX - widthEm * textSize / 2f
            text.forEachIndexed { index, character ->
                val ink = inks[index]
                canvas.drawText(character.toString(), x - ink.left * textSize, baseline, paint)
                x += ink.width() * textSize + gap
            }
        }
    }

    /** Largest text size at which [text] still fits [maxWidth] by [maxHeight] at its ink box. */
    private fun fitTextSize(text: String, paint: Paint, maxWidth: Float, maxHeight: Float): Float {
        if (text.isBlank()) return maxHeight.coerceAtMost(maxWidth)
        paint.textSize = PROBE_TEXT_SIZE
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.isEmpty) return maxHeight.coerceAtMost(maxWidth)
        return min(
            maxWidth / bounds.width() * PROBE_TEXT_SIZE,
            maxHeight / bounds.height() * PROBE_TEXT_SIZE,
        )
    }

    /** Draws [text] so the centre of its ink sits exactly on the given point. */
    private fun drawInkCentered(canvas: Canvas, text: String, paint: Paint, centerX: Float, centerY: Float) {
        if (text.isBlank()) return
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, centerX, centerY - bounds.exactCenterY(), paint)
    }

    // ---------------------------------------------------------------------------------------
    // Mood marks
    // ---------------------------------------------------------------------------------------

    /** One compact monochrome mark that keeps the status icon recognizable for each mood. */
    private fun drawMoodMark(
        canvas: Canvas,
        mood: NotificationMood,
        centerX: Float,
        centerY: Float,
        size: Float,
        color: Int = Color.WHITE,
        paint: Paint? = null,
    ) {
        if (mood == NotificationMood.QUIET || size <= 0f) return
        val box = RectF(centerX - size / 2f, centerY - size / 2f, centerX + size / 2f, centerY + size / 2f)
        val fill = paint ?: Paint(Paint.ANTI_ALIAS_FLAG)
        fill.style = Paint.Style.FILL
        if (fill.xfermode == null) fill.color = color
        when (mood) {
            NotificationMood.QUIET -> Unit
            NotificationMood.TASKS_DUE -> {
                val radius = size * 0.15f
                val step = size * 0.30f
                for (index in -1..1) {
                    canvas.drawCircle(centerX + index * step, centerY, radius, fill)
                }
            }
            NotificationMood.OCCASION -> canvas.drawUnitPath(sparklePath(), box, fill)
            NotificationMood.RELIGIOUS -> canvas.drawUnitPath(crescentPath(), box, fill)
            NotificationMood.HOLIDAY -> {
                val pole = Paint(fill).apply {
                    style = Paint.Style.FILL
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = size * 0.13f
                }
                canvas.drawLine(
                    box.left + size * 0.26f,
                    box.top,
                    box.left + size * 0.26f,
                    box.bottom,
                    pole,
                )
                canvas.drawUnitPath(flagPath(), box, fill)
            }
        }
    }

    /** Maps a path authored in the unit square into [box], preserving its aspect ratio. */
    private fun Canvas.drawUnitPath(path: Path, box: RectF, paint: Paint) {
        val matrix = Matrix().apply { setRectToRect(UNIT_RECT, box, Matrix.ScaleToFit.CENTER) }
        drawPath(Path(path).apply { transform(matrix) }, paint)
    }

    /** Four-pointed sparkle: still legible when it is only four pixels across, unlike a five-point star. */
    private fun sparklePath(): Path {
        val waist = 0.15f
        return Path().apply {
            moveTo(0.5f, 0f)
            lineTo(0.5f + waist, 0.5f - waist)
            lineTo(1f, 0.5f)
            lineTo(0.5f + waist, 0.5f + waist)
            lineTo(0.5f, 1f)
            lineTo(0.5f - waist, 0.5f + waist)
            lineTo(0f, 0.5f)
            lineTo(0.5f - waist, 0.5f - waist)
            close()
        }
    }

    /** Crescent: a disc with a smaller, offset disc subtracted from it. */
    private fun crescentPath(): Path {
        val outer = Path().apply { addCircle(0.5f, 0.5f, 0.5f, Path.Direction.CW) }
        val inner = Path().apply { addCircle(0.69f, 0.29f, 0.49f, Path.Direction.CW) }
        return Path().apply { op(outer, inner, Path.Op.DIFFERENCE) }
    }

    /** Flag body; the pole is drawn separately so it can be a round-capped line. */
    private fun flagPath(): Path = Path().apply {
        moveTo(0.30f, 0.06f)
        lineTo(1.00f, 0.06f)
        lineTo(0.72f, 0.30f)
        lineTo(1.00f, 0.54f)
        lineTo(0.30f, 0.54f)
        close()
    }

    private fun color(context: Context, resId: Int): Int =
        ResourcesCompat.getColor(context.resources, resId, context.theme)

    /**
     * Loads the bundled Vazirmatn face and, on API 28+, picks the requested variable weight so the
     * generated artwork matches the in-app typography ladder.
     */
    private fun vazirTypeface(context: Context, weight: Int): Typeface {
        typefaceCache[weight]?.let { return it }
        val base = baseVazirTypeface ?: (ResourcesCompat.getFont(context.applicationContext, DesignR.font.vazirmatn_variable)
            ?: Typeface.DEFAULT).also { baseVazirTypeface = it }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, weight, false)
        } else {
            base
        }
        typefaceCache[weight] = resolved
        return resolved
    }
}
