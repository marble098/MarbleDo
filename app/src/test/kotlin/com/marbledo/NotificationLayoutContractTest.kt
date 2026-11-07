package com.marbledo

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the contract between `CalendarNotificationViews` and the notification layouts it builds.
 *
 * `RemoteViews` resolves every `R.id` it targets when SystemUI *applies* the layout, not when the
 * notification is posted, so a view id one design's layout does not declare fails silently: the
 * apply throws, the notification never renders, and it vanishes from the status bar. That is
 * exactly what happened to the fourth design ("banner"), whose collapsed layout declared
 * `notification_accent_rule` but whose expanded layouts did not.
 *
 * The test therefore parses the builder source itself — so a newly added `R.id` is covered without
 * anyone remembering to update a list here — works out which branch guards each reference, and
 * asserts that every layout a design can be rendered from declares all of them.
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

        for (rawLine in source.lineSequence()) {
            // Guards and ids are matched before strings are removed, because the guard itself
            // contains a string literal ("banner"); only brace counting needs the strings gone.
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
        val ID = Regex("""R\.id\.([A-Za-z_][A-Za-z0-9_]*)""")
        val LAYOUT_ID = Regex("""@\+id/([A-Za-z_][A-Za-z0-9_]*)""")
        val STRING = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
        const val BANNER_GUARD = "if (design == \"banner\") {"
        const val EXPANDED_GUARD = "if (!expanded) {"
        const val ELSE_GUARD = "} else {"
    }
}
