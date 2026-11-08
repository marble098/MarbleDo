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
 * icon belongs to the same notification, the icon disappears too — the exact symptom of the fourth
 * design ("banner").
 *
 * Two independent defects produced it, so there are two guards:
 *
 *  1. **Missing ids.** The banner's collapsed layout declared `notification_accent_rule` but its
 *     expanded layouts did not, while the builder tinted that id in both states.
 *  2. **Widget classes outside the RemoteViews allowlist.** Every banner layout was built from bare
 *     `<View>` elements (one accent rule, two hairlines) — the only design that was. RemoteViews
 *     documents a fixed widget list that does not contain `View`.
 *
 * The first test parses the builder source itself — so a newly added `R.id` is covered without
 * anyone remembering to update a list here — works out which branch guards each reference, and
 * asserts that every layout a design can be rendered from declares all of them. The remaining tests
 * check the widget classes, and that the banner's accent rule is the ImageView the builder paints.
 */
class NotificationLayoutContractTest {

    private val designs = listOf("classic", "minimal", "editorial", "banner")
    private val knownGuards = setOf("", "banner", "expanded", "expanded-else")

    @Test
    fun `every view id the builder targets exists in every layout it can render`() {
        val references = referencesByGuard(builderSource())
        assertTrue(references.isNotEmpty(), "no R.id references found in CalendarNotificationViews")

        for (design in designs) {
            for (expanded in listOf(false, true)) {
                val required = requiredIds(references, design, expanded)
                for (file in layouts(design, expanded)) {
                    val missing = required - declaredIds(file)
                    assertTrue(
                        missing.isEmpty(),
                        "$design/${layoutKind(expanded)}: ${file.name} is missing $missing, " +
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

        // Completeness of the analysis itself: with the banner guard satisfied and both sides of
        // the expanded split walked, no reference may be dropped on the floor.
        val bannerBothSides = requiredIds(references, "banner", false) +
            requiredIds(references, "banner", true)
        assertEquals(
            references.keys,
            bannerBothSides,
            "the branch analysis lost track of some R.id references",
        )

        // Every guard the analysis produced must be one this test knows how to interpret, and the
        // banner guard must really have been seen — otherwise this test would pass vacuously.
        val guards = references.values.flatten().flatten().toSet()
        assertTrue(
            guards.all { it in knownGuards },
            "unknown branch guard(s) in CalendarNotifications: ${guards - knownGuards}",
        )
        assertTrue(
            "banner" in guards && "expanded" in guards && "expanded-else" in guards,
            "expected branch guards were not recognised; the parser is out of step with " +
                "CalendarNotificationViews (saw $guards)",
        )
        assertTrue(
            references.values.any { sites -> sites.any { guards -> guards.isEmpty() } },
            "no unconditional R.id reference was found; the guard parser is out of step",
        )
    }

    /**
     * Every `R.id` in the builder paired with the guards open at its reference site. The same id
     * can be referenced from several sites, so each id keeps one entry per site.
     */
    private fun referencesByGuard(source: String): Map<String, List<Set<String>>> {
        val references = LinkedHashMap<String, MutableList<Set<String>>>()
        var depth = 0
        val openGuards = LinkedHashMap<Int, String>()

        fun record(id: String) {
            references.getOrPut(id) { mutableListOf() }.add(openGuards.values.toSet())
        }

        for (rawLine in source.lineCommentAndBlockCommentFree().lineSequence()) {
            // Guards and ids are matched before strings are removed, because the guard itself
            // contains a string literal ("banner"); only brace counting needs the strings gone.
            // Block comments carry prose, not actions, so a documented id must not count.
            val code = rawLine.substringBefore("//")
            ID.findAll(code).forEach { record(it.groupValues[1]) }
            when {
                code.contains(BANNER_GUARD) -> {
                    depth++
                    openGuards[depth] = "banner"
                }
                code.contains(EXPANDED_GUARD) -> {
                    depth++
                    openGuards[depth] = "expanded"
                }
                code.contains(ELSE_GUARD) -> {
                    // `} else {` closes the `if (!expanded)` block and reopens the same depth in
                    // the other branch: depth is unchanged, only the guard flips.
                    openGuards.remove(depth)
                    depth--
                    depth++
                    openGuards[depth] = "expanded-else"
                }
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

    /**
     * Strips block comments (the slash-star … star-slash form) so documentation that mentions a
     * view id cannot be mistaken for a reference the builder actually makes.
     */
    private fun String.lineCommentAndBlockCommentFree(): String =
        BLOCK_COMMENT.replace(this, "")

    /** An id is required when at least one of its reference sites is reachable. */
    private fun requiredIds(
        references: Map<String, List<Set<String>>>,
        design: String,
        expanded: Boolean,
    ): Set<String> = references.filter { (_, sites) ->
        sites.any { guards ->
            guards.all { guard ->
                when (guard) {
                    "" -> true
                    "banner" -> design == "banner"
                    "expanded" -> !expanded
                    "expanded-else" -> expanded
                    else -> error("unknown branch guard '$guard'")
                }
            }
        }
    }.keys

    /**
     * The second half of the same failure: RemoteViews may only inflate the widget classes the
     * documented allowlist names. A plain `<View>` is outside it, and on a SystemUI build that
     * enforces the allowlist the custom view throws while it is being inflated — the row is
     * dropped and the notification leaves the shade *and* the status bar. The banner design was
     * the only one built from `<View>` elements (one accent rule, two hairlines), which is exactly
     * why only the banner vanished; every widget a notification layout uses must be on the list.
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

    /**
     * `CalendarNotificationViews` tints the banner rule with `setImageViewResource`, so the id has
     * to be an ImageView. A `setImageViewResource` on a plain View is the same all-or-nothing
     * apply failure the widget allowlist above guards against.
     */
    @Test
    fun `the banner accent rule is an ImageView in every banner layout`() {
        for (expanded in listOf(false, true)) {
            for (file in layouts("banner", expanded)) {
                val source = file.readText(Charsets.UTF_8)
                val rule = source.substringAfter("@+id/notification_accent_rule", "")
                val owner = source.substring(0, source.indexOf("@+id/notification_accent_rule"))
                    .substringAfterLast('<')
                    .substringBefore('\n')
                    .trim()
                assertEquals("ImageView", owner, "${file.name}: the accent rule must stay an ImageView")
                assertTrue(rule.isNotBlank(), "${file.name}: the accent rule is missing")
            }
        }
    }

    private fun layoutDirectory(): File = resolve(
        "src/main/res/layout",
        "../app/src/main/res/layout",
    )

    private fun layouts(design: String, expanded: Boolean): List<File> {
        val kind = layoutKind(expanded)
        return listOf("ltr", "rtl").map { direction ->
            resolve("src/main/res/layout/notification_${design}_${kind}_$direction.xml")
        }
    }

    private fun layoutKind(expanded: Boolean) = if (expanded) "expanded" else "compact"

    /** Layouts declare views as `@+id/name`, which is what a RemoteViews action resolves against. */
    private fun declaredIds(file: File): Set<String> {
        assertTrue(file.exists(), "notification layout not found: ${file.path}")
        return LAYOUT_ID.findAll(file.readText(Charsets.UTF_8)).map { it.groupValues[1] }.toSet()
    }

    private fun builderSource(): String = resolve(
        "src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt",
        "../app/src/main/kotlin/com/marbledo/app/notifications/CalendarNotificationViews.kt",
    ).readText(Charsets.UTF_8)

    /** Falls back across candidate roots the way the catalog test does, so the test is location safe. */
    private fun resolve(vararg candidates: String): File = candidates
        .map(::File)
        .firstOrNull(File::exists)
        ?: File(candidates.first())

    private companion object {
        /**
         * The widget classes RemoteViews documents as inflatable. A bare `View` is deliberately
         * absent: it is the class that took the banner design off the status bar.
         */
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
        const val BANNER_GUARD = "if (design == \"banner\") {"
        const val EXPANDED_GUARD = "if (!expanded) {"
        const val ELSE_GUARD = "} else {"
    }
}
