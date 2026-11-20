package com.marbledo

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the contract between `CalendarNotificationViews` and the notification layouts it builds.
 *
 * A custom notification view fails **all or nothing**: `RemoteViews` resolves every `R.id` and every
 * widget class when SystemUI *applies* the layout, not when the notification is posted, and one
 * action that cannot run aborts the whole apply. The row is then dropped, and because the status-bar
 * icon belongs to the same notification, the icon disappears too.
 *
 * The builder therefore only touches an id when the look's flag allows it (`if (look.badge) { … }`)
 * or when the card is expanded (everything after `if (!expanded) return@apply`). This test parses
 * the builder source — so a newly added `R.id` is covered without anyone remembering to update a
 * list here — works out which guards are open at each reference, and asserts that every layout a
 * look can be rendered from declares all the ids reachable for it. The remaining tests check the
 * widget classes and the plate.
 */
class NotificationLayoutContractTest {

    /** The anatomy table; `NotificationLook.kt` carries the same flags and the test checks that. */
    private val looks: Map<String, Map<String, Boolean>> = mapOf(
        "classic" to mapOf("badge" to true, "chipPill" to true, "band" to false, "headerDivider" to false),
        "minimal" to mapOf("badge" to false, "chipPill" to false, "band" to false, "headerDivider" to false),
        "editorial" to mapOf("badge" to false, "chipPill" to false, "band" to false, "headerDivider" to true),
        "banner" to mapOf("badge" to false, "chipPill" to false, "band" to true, "headerDivider" to false),
    )

    @Test
    fun `the look table matches NotificationLook`() {
        val source = lookSource()
        for ((key, flags) in looks) {
            val line = Regex("""${key.uppercase()}\("$key",([^\n]*)\),""").find(source)
            assertTrue(line != null, "NotificationLook.${key.uppercase()} is missing")
            for ((flag, value) in flags) {
                assertTrue(
                    line!!.groupValues[1].contains("$flag = $value"),
                    "NotificationLook.${key.uppercase()}: expected $flag = $value",
                )
            }
        }
    }

    @Test
    fun `every view id the builder targets exists in every layout it can render`() {
        val references = referencesByGuard(builderSource())
        assertTrue(references.isNotEmpty(), "no R.id references found in CalendarNotificationViews")

        for ((look, flags) in looks) {
            for (expanded in listOf(false, true)) {
                val required = requiredIds(references, flags, expanded)
                for (file in layouts(look, expanded)) {
                    val missing = required - declaredIds(file)
                    assertTrue(
                        missing.isEmpty(),
                        "$look/${layoutKind(expanded)}: ${file.name} is missing $missing, " +
                            "which CalendarNotificationViews sets — RemoteViews throws when " +
                            "SystemUI applies it and the notification disappears",
                    )
                }
            }
        }
    }

    @Test
    fun `the branch analysis covers every id the builder references`() {
        val references = referencesByGuard(builderSource())

        // With every look walked in both sizes, no reference may be dropped on the floor.
        val everyBranch = looks.values.flatMap { flags ->
            requiredIds(references, flags, false) + requiredIds(references, flags, true)
        }.toSet()
        assertEquals(references.keys, everyBranch, "the branch analysis lost track of some R.id references")

        val guards = references.values.flatMap { sites -> sites.flatMap { it.guards } }.toSet()
        val known = looks.values.first().keys
        assertTrue(guards.all { it in known }, "unknown look guard(s) in CalendarNotificationViews: ${guards - known}")
        assertTrue(guards.containsAll(known), "not every look flag guards a reference; saw $guards")
        assertTrue(references.values.any { sites -> sites.any { it.expandedOnly } }, "no expanded-only reference was found")
        assertTrue(references.values.any { sites -> sites.any { it.guards.isEmpty() && !it.expandedOnly } }, "no unconditional reference was found")
    }

    private data class Site(val guards: Set<String>, val expandedOnly: Boolean)

    /** Every `R.id` in the builder paired with the guards open at each of its reference sites. */
    private fun referencesByGuard(source: String): Map<String, List<Site>> {
        val references = LinkedHashMap<String, MutableList<Site>>()
        var depth = 0
        var expandedOnly = false
        val openGuards = LinkedHashMap<Int, String>()

        for (rawLine in BLOCK_COMMENT.replace(source, "").lineSequence()) {
            val code = rawLine.substringBefore("//")
            ID.findAll(code).forEach { match ->
                references.getOrPut(match.groupValues[1]) { mutableListOf() }.add(Site(openGuards.values.toSet(), expandedOnly))
            }
            val guard = LOOK_GUARD.find(code)
            when {
                guard != null -> {
                    depth++
                    openGuards[depth] = guard.groupValues[1]
                }
                code.contains(EXPANDED_SPLIT) -> expandedOnly = true
                else -> STRING.replace(code, "").forEach { char ->
                    when (char) {
                        '{' -> depth++
                        '}' -> {
                            openGuards.remove(depth)
                            depth--
                        }
                    }
                }
            }
        }
        return references
    }

    /** An id is required when at least one of its reference sites is reachable for the look. */
    private fun requiredIds(references: Map<String, List<Site>>, flags: Map<String, Boolean>, expanded: Boolean): Set<String> =
        references.filter { (_, sites) ->
            sites.any { site ->
                (!site.expandedOnly || expanded) && site.guards.all { guard -> flags[guard] == true }
            }
        }.keys

    /**
     * RemoteViews may only inflate the widget classes the documented allowlist names. A plain
     * `<View>` is outside it, and on a SystemUI build that enforces the allowlist the custom view
     * throws while it is being inflated — the row is dropped and the notification leaves the shade
     * *and* the status bar.
     */
    @Test
    fun `every notification layout only uses widgets RemoteViews can inflate`() {
        val layouts = layoutDirectory().listFiles { file -> file.name.startsWith("notification_") && file.extension == "xml" }
            .orEmpty()
            .sortedBy { it.name }
        assertTrue(layouts.size >= 16, "expected the notification layouts, found ${layouts.size}")

        val unsupported = LinkedHashMap<String, MutableSet<String>>()
        for (file in layouts) {
            for (element in ELEMENT.findAll(file.readText(Charsets.UTF_8)).map { it.groupValues[1] }) {
                if (element !in REMOTEVIEWS_WIDGETS) unsupported.getOrPut(file.name) { linkedSetOf() }.add(element)
            }
        }
        assertTrue(
            unsupported.isEmpty(),
            "these layouts use widget classes RemoteViews will not inflate, which makes the whole " +
                "notification disappear: $unsupported",
        )
    }

    /** The card paints its own plate: an ImageView, and the first child so nothing is drawn under it. */
    @Test
    fun `every layout paints the card plate behind its content`() {
        for (look in looks.keys) {
            for (expanded in listOf(false, true)) {
                for (file in layouts(look, expanded)) {
                    val source = file.readText(Charsets.UTF_8)
                    val plateIndex = source.indexOf("@+id/notification_card_plate")
                    assertTrue(plateIndex > 0, "${file.name}: no card plate")
                    val owner = source.substring(0, plateIndex).substringAfterLast('<').substringBefore('\n').trim()
                    assertEquals("ImageView", owner, "${file.name}: the plate must stay an ImageView")
                    val elements = ELEMENT.findAll(source).map { it.groupValues[1] }.toList()
                    assertEquals("ImageView", elements.getOrNull(1), "${file.name}: the plate is not first")
                }
            }
        }
    }

    /**
     * The banner's caps are drawn unscaled (`matrix`) at exactly the width the bitmap is painted
     * at, which is the only reason the card-radius corner survives any band height. A `fitXY` cap,
     * or a cap of another width, would stretch the corner into an ellipse.
     */
    @Test
    fun `the banner caps are never scaled`() {
        for (expanded in listOf(false, true)) {
            for (file in layouts("banner", expanded)) {
                val source = file.readText(Charsets.UTF_8)
                for (cap in listOf("notification_band_start", "notification_band_end")) {
                    val element = source.substringAfter("@+id/$cap").substringBefore("/>")
                    assertTrue(element.contains("android:scaleType=\"matrix\""), "${file.name}: $cap must use scaleType=matrix")
                    assertTrue(element.contains("android:layout_width=\"20dp\""), "${file.name}: $cap must be 20dp wide")
                }
            }
        }
        assertTrue(artworkSource().contains("const val BAND_CAP_DP = 20f"), "the cap bitmap width must match the layout")
    }

    private fun layoutDirectory(): File = resolve("src/main/res/layout", "../app/src/main/res/layout")

    private fun layouts(look: String, expanded: Boolean): List<File> {
        val kind = layoutKind(expanded)
        return listOf("ltr", "rtl").map { direction ->
            resolve(
                "src/main/res/layout/notification_${look}_${kind}_$direction.xml",
                "../app/src/main/res/layout/notification_${look}_${kind}_$direction.xml",
            )
        }
    }

    private fun layoutKind(expanded: Boolean) = if (expanded) "expanded" else "compact"

    private fun declaredIds(file: File): Set<String> {
        assertTrue(file.exists(), "notification layout not found: ${file.path}")
        return LAYOUT_ID.findAll(file.readText(Charsets.UTF_8)).map { it.groupValues[1] }.toSet()
    }

    private fun builderSource(): String = resolve(
        "src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt",
        "../app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt",
    ).readText(Charsets.UTF_8)

    private fun lookSource(): String = resolve(
        "src/main/kotlin/com/marbledo/app/notifications/NotificationLook.kt",
        "../app/src/main/kotlin/com/marbledo/app/notifications/NotificationLook.kt",
    ).readText(Charsets.UTF_8)

    private fun artworkSource(): String = resolve(
        "src/main/kotlin/com/marbledo/app/notifications/NotificationArtwork.kt",
        "../app/src/main/kotlin/com/marbledo/app/notifications/NotificationArtwork.kt",
    ).readText(Charsets.UTF_8)

    private fun resolve(vararg candidates: String): File = candidates
        .map(::File)
        .firstOrNull(File::exists)
        ?: File(candidates.first())

    private companion object {
        val REMOTEVIEWS_WIDGETS = setOf(
            "LinearLayout", "FrameLayout", "RelativeLayout", "GridLayout", "AbsoluteLayout",
            "TextView", "Button", "ImageButton", "ImageView", "ProgressBar", "Chronometer",
            "AnalogClock", "TextClock", "ViewFlipper", "AdapterViewFlipper", "ListView",
            "GridView", "StackView",
        )
        val ELEMENT = Regex("""<([A-Za-z][A-Za-z0-9_]*)""")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val ID = Regex("""R\.id\.([A-Za-z_][A-Za-z0-9_]*)""")
        val LAYOUT_ID = Regex("""@\+id/([A-Za-z_][A-Za-z0-9_]*)""")
        val STRING = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
        /** `if (look.badge) {` and friends: the only way the builder branches on a look. */
        val LOOK_GUARD = Regex("""if \(look\.([A-Za-z]+)\) \{""")
        /** Everything after this line runs only for the expanded card. */
        const val EXPANDED_SPLIT = "if (!expanded) return@apply"
    }
}
