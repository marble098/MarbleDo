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
import android.graphics.PorterDuffColorFilter
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
    private val tintCache = LruCache<String, Bitmap>(24)
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

    /**
     * The plate behind the mood mark on the date badge.
     *
     * The badge itself is painted in the app theme's accent, so the mood chip is drawn in its own
     * fixed colour instead of a blend of the two: a blend of a pale dark-theme accent with a pale
     * mood colour produced a plate that was lighter than the white mark on it, which is what made
     * the small glyph disappear. These plates all clear 9:1 with white.
     */
    private fun moodChipColor(context: Context, mood: NotificationMood): Int = when (mood) {
        NotificationMood.HOLIDAY -> color(context, R.color.notification_badge_chip_holiday)
        NotificationMood.RELIGIOUS -> color(context, R.color.notification_badge_chip_religious)
        NotificationMood.OCCASION -> color(context, R.color.notification_badge_chip_occasion)
        NotificationMood.TASKS_DUE -> color(context, R.color.notification_badge_chip_tasks)
        NotificationMood.QUIET -> color(context, R.color.notification_badge_chip_quiet)
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

    /**
     * A plain icon drawable recoloured to the theme accent.
     *
     * The two large notification icons are shipped with the old indigo baked in. They are only
     * used as fallbacks (when the generated badge cannot be drawn), but a fallback that keeps a
     * colour the user's theme does not have is exactly the "some parts never change colour"
     * complaint, so the indigo plate is repainted in the accent the notification already wears
     * while the white glyph on top of it is left alone.
     */
    fun tintedFallbackIcon(context: Context, drawableRes: Int, accentColor: Int): Bitmap? = runCatching {
        val key = "$drawableRes:$accentColor"
        synchronized(tintCache) { tintCache.get(key)?.takeIf { !it.isRecycled }?.let { return@runCatching it } }
        val drawable = ResourcesCompat.getDrawable(context.resources, drawableRes, context.theme) ?: return@runCatching null
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        // The plate is the only saturated colour in these assets; the glyph is pure white.
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val alpha = pixel ushr 24
            if (alpha == 0) continue
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            val isGlyph = red > 0xE0 && green > 0xE0 && blue > 0xE0
            if (!isGlyph) {
                pixels[index] = (alpha shl 24) or (accentColor and 0x00FFFFFF)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.density = context.resources.displayMetrics.densityDpi
        synchronized(tintCache) { tintCache.put(key, bitmap) }
        bitmap
    }.getOrNull()

    /**
     * Colored large icon: the date badge. The plate is painted in the **app theme's accent** — the
     * same palette the screens use — with the day's mood carried in its own chip at the foot of the
     * badge, so one theme switch repaints the shade with it instead of leaving indigo behind.
     */
    fun dateBadge(
        context: Context,
        dayDigits: String,
        monthLabel: String,
        mood: NotificationMood,
        accentColor: Int = 0,
    ): Bitmap? =
        runCatching {
            val themeAccent = if (accentColor != 0) accentColor else accentColor(context, mood)
            val moodColor = accentColor(context, mood)
            val cacheKey = "$dayDigits:$monthLabel:${mood.name}:$themeAccent:$moodColor"
            synchronized(dateBadgeCache) {
                dateBadgeCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let { return@runCatching it }
            }
            val size = 256
            val side = size.toFloat()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // The gradient comes from the theme, exactly like the app's own surfaces: a lifted top
            // edge and a deep foot, so the badge has the same light as a Material card.
            val deep = ColorUtils.blendARGB(themeAccent, Color.BLACK, 0.32f)
            val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    side,
                    ColorUtils.blendARGB(themeAccent, Color.WHITE, 0.22f),
                    deep,
                    Shader.TileMode.CLAMP,
                )
            }
            val outerRect = RectF(0f, 0f, side, side)
            val radius = side * 0.26f
            canvas.drawRoundRect(outerRect, radius, radius, plate)

            // Subtle top calendar header band + crisp inner rim for modern depth.
            val headerClip = Path().apply {
                addRoundRect(outerRect, radius, radius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(headerClip)
            val headerBand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 28
            }
            canvas.drawRect(0f, 0f, side, side * 0.31f, headerBand)
            val headerDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 36
                strokeWidth = 2f
            }
            canvas.drawLine(0f, side * 0.31f, side, side * 0.31f, headerDivider)
            canvas.restore()

            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 44
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(RectF(1.5f, 1.5f, side - 1.5f, side - 1.5f), radius - 1.5f, radius - 1.5f, rimPaint)

            val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 240
                textAlign = Paint.Align.CENTER
                typeface = vazirTypeface(context, 700)
            }
            monthPaint.textSize = fitTextSize(monthLabel, monthPaint, side * 0.74f, side * 0.15f)
            drawInkCentered(canvas, monthLabel, monthPaint, side / 2f, side * 0.175f)

            // The same ink-filling layout the status icon uses, so the two always agree.
            val run = DigitRun(vazirTypeface(context, DIGIT_WEIGHT), dayDigits, DIGIT_GAP_EM)
            val dayBox = RectF(side * 0.14f, side * 0.34f, side * 0.86f, side * 0.78f)
            run.draw(
                canvas = canvas,
                textSize = run.fittedSize(dayBox.width(), dayBox.height()),
                centerX = dayBox.centerX(),
                centerY = dayBox.centerY(),
                color = Color.WHITE,
            )
            // The mood lives in a chip of its own colour at the foot of the badge: the meaning
            // (holiday, religious, occasion, tasks) stays semantic while the plate follows theme.
            if (mood != NotificationMood.QUIET) {
                val chipHeight = side * 0.15f
                val chipWidth = side * 0.36f
                val chipRect = RectF(
                    (side - chipWidth) / 2f,
                    side * 0.815f,
                    (side + chipWidth) / 2f,
                    side * 0.815f + chipHeight,
                )
                val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = moodChipColor(context, mood)
                }
                canvas.drawRoundRect(chipRect, chipHeight / 2f, chipHeight / 2f, chip)
                drawMoodMark(
                    canvas = canvas,
                    mood = mood,
                    centerX = side / 2f,
                    centerY = chipRect.centerY(),
                    size = side * 0.085f,
                    color = Color.WHITE,
                )
            }
            synchronized(dateBadgeCache) {
                dateBadgeCache.put(cacheKey, bitmap)
            }
            bitmap
        }.getOrNull()

    // ---------------------------------------------------------------------------------------
    // Themed notification chrome. The RemoteViews card may not run reflective tint calls, so the
    // few shapes that must carry the *theme accent* (which no fixed drawable can know) arrive as
    // ready-painted bitmaps; each one is applied with a first-class `setImageViewBitmap()`.
    //
    // Only shapes that survive stretching are bitmaps: the banner's flat fill, its two caps (which
    // are never scaled, see `scaleType="matrix"` in the layout) and the editorial rule (a plain
    // rectangle). Anything with a corner that has to scale — the card plate, the task panel, the
    // state pill, the hairlines — is a shape drawable chosen with `setImageViewResource()`.
    // ---------------------------------------------------------------------------------------

    /** The dp width of a banner cap in the layout; the bitmap is drawn at exactly this width. */
    const val BAND_CAP_DP = 20f

    /**
     * One cap of the banner band: a [BAND_CAP_DP]-wide strip whose *outer* top corner is rounded
     * at the card radius. Tall on purpose — the ImageView draws it unscaled from the top and clips
     * the rest, so the corner stays a circle at any band height. [leading] is the cap on the left
     * edge of the screen; the right one is its mirror.
     */
    fun bandCap(context: Context, accentColor: Int, leading: Boolean): Bitmap? = runCatching {
        val metrics = context.resources.displayMetrics
        val width = (BAND_CAP_DP * metrics.density).roundToInt().coerceAtLeast(4)
        val height = (BAND_CAP_HEIGHT_DP * metrics.density).roundToInt()
        val key = "bandcap:$accentColor:$leading:$width:$height"
        synchronized(tintCache) { tintCache.get(key)?.takeIf { !it.isRecycled }?.let { return@runCatching it } }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        val radius = width.toFloat()
        // A rounded rectangle twice as wide as the cap, positioned so that only the outer corner
        // falls inside the bitmap; the inner edge is therefore straight and meets the fill.
        val left = if (leading) 0f else -radius
        canvas.drawRoundRect(RectF(left, 0f, left + 2 * radius, height + radius), radius, radius, paint)
        bitmap.density = metrics.densityDpi
        synchronized(tintCache) { tintCache.put(key, bitmap) }
        bitmap
    }.getOrNull()

    /** The flat middle of the banner band: a solid colour, which `fitXY` cannot distort. */
    fun bandFill(context: Context, accentColor: Int): Bitmap? = solid(context, "bandfill", accentColor, 4, 4)

    /**
     * The editorial rule beside the task block: a 3 dp-wide solid rectangle in the theme accent,
     * stretched to the block's height. Square ends on purpose — a rounded end would distort.
     */
    fun verticalRule(context: Context, accentColor: Int): Bitmap? {
        val density = context.resources.displayMetrics.density
        return solid(context, "rule", accentColor, (3f * density).roundToInt().coerceAtLeast(2), (24f * density).roundToInt())
    }

    private fun solid(context: Context, kind: String, color: Int, width: Int, height: Int): Bitmap? = runCatching {
        val key = "$kind:$color:$width:$height"
        synchronized(tintCache) { tintCache.get(key)?.takeIf { !it.isRecycled }?.let { return@runCatching it } }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        bitmap.density = context.resources.displayMetrics.densityDpi
        synchronized(tintCache) { tintCache.put(key, bitmap) }
        bitmap
    }.getOrNull()

    /** Taller than any band the layouts can produce; the ImageView clips the excess. */
    private const val BAND_CAP_HEIGHT_DP = 160f

    /**
     * A vector drawable recoloured to [color] and returned as a bitmap.
     *
     * The card needs a tinted star for the occasion heading, and a `setInt("setColorFilter", …)`
     * is a reflective RemoteViews action that a locked-down SystemUI may reject — which takes the
     * whole row (and the status-bar icon) with it. Painting the drawable here keeps every action
     * on the card first-class.
     */
    fun tintedVector(context: Context, drawableRes: Int, color: Int): Bitmap? = runCatching {
        val key = "vector:$drawableRes:$color"
        synchronized(tintCache) { tintCache.get(key)?.takeIf { !it.isRecycled }?.let { return@runCatching it } }
        val drawable = ResourcesCompat.getDrawable(context.resources, drawableRes, context.theme) ?: return@runCatching null
        val width = (drawable.intrinsicWidth * 4).coerceAtLeast(48)
        val height = (drawable.intrinsicHeight * 4).coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
        canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), paint)
        drawable.draw(canvas)
        canvas.restore()
        bitmap.density = context.resources.displayMetrics.densityDpi
        synchronized(tintCache) { tintCache.put(key, bitmap) }
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
