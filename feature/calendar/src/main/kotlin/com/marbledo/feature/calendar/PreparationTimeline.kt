package com.marbledo.feature.calendar

import com.marbledo.domain.model.ChecklistItem
import com.marbledo.domain.model.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Stable keys make creating the same stage twice detectable even after its title is edited. */
enum class PreparationStage(val key: String, val daysBefore: Int, val focusMinutes: Int) {
    PLAN("plan", 14, 25), COORDINATE("coordinate", 7, 25), PREPARE("prepare", 1, 45), DAY("day", 0, 0);
}
data class PreparationStep(
    val stage: PreparationStage,
    val title: String,
    val date: LocalDate,
    val originalDate: LocalDate,
    val alreadyExists: Boolean,
    val isPast: Boolean,
    val workingDateFound: Boolean = true,
)

object PreparationTimeline {
    fun stageTag(occasionId: String, eventDate: LocalDate, stage: PreparationStage): String =
        "occasion-step:$occasionId:$eventDate:${stage.key}"

    fun suggest(
        occasion: Occasion,
        eventDate: LocalDate,
        today: LocalDate,
        tasks: List<Task>,
        languageTag: String,
        holidays: Set<LocalDate> = emptySet(),
        avoidNonWorkingDays: Boolean = true,
        thursdayClosed: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PreparationStep> = PreparationStage.entries.map { stage ->
        val nominal = eventDate.minusDays(stage.daysBefore.toLong())
        val candidate = if (avoidNonWorkingDays && stage != PreparationStage.DAY) {
            (0L..14L).asSequence().map(nominal::minusDays).firstOrNull {
                it !in holidays && it.dayOfWeek != DayOfWeek.FRIDAY && (!thursdayClosed || it.dayOfWeek != DayOfWeek.THURSDAY)
            }
        } else nominal
        val date = candidate ?: nominal
        val fa = languageTag == "fa"
        val prefix = when (stage) {
            PreparationStage.PLAN -> if (fa) "برنامه‌ریزی" else "Plan"
            PreparationStage.COORDINATE -> if (fa) "هماهنگی" else "Coordinate"
            PreparationStage.PREPARE -> if (fa) "آماده‌سازی نهایی" else "Final preparation"
            PreparationStage.DAY -> if (fa) "روز مناسبت" else "Occasion day"
        }
        PreparationStep(stage, "$prefix · ${occasion.title(languageTag)}", date, nominal,
            tasks.any { !it.isArchived && stageTag(occasion.id, eventDate, stage) in it.tags } ||
                (stage == PreparationStage.DAY && OccasionIntelligence.isPlanned(occasion, eventDate, tasks.filter { task ->
                    task.tags.none { it.startsWith("occasion-step:") } && task.dueAtEpochMillis?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == eventDate
                    } == true
                }, zone)), date < today, candidate != null)
    }

    fun draft(occasion: Occasion, eventDate: LocalDate, step: PreparationStep, languageTag: String, zone: ZoneId): Task = Task(
        title = step.title.trim(),
        dueAtEpochMillis = step.date.atStartOfDay(zone).toInstant().toEpochMilli(),
        isAllDay = true,
        countdownEnabled = step.stage == PreparationStage.DAY,
        focusMinutes = step.stage.focusMinutes,
        description = occasion.note(languageTag),
        tags = listOf(OccasionIntelligence.planningTag(occasion.id, eventDate), stageTag(occasion.id, eventDate, step.stage)),
        checklist = if (step.stage == PreparationStage.PREPARE) OccasionTemplates.checklist(PreparationTemplate.SIMPLE, languageTag)
            .mapIndexed { i, text -> ChecklistItem("${step.stage.key}-$i", text) } else emptyList(),
    )
}
