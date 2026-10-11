package com.marbledo.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.domain.util.WorkdayPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun WorkdaySuggestionDialog(
    after: LocalDate,
    catalog: OccasionCatalog,
    lunarOffsetDays: Int,
    thursdayClosed: Boolean,
    tasks: List<Task>,
    languageTag: String,
    zone: ZoneId,
    onAdd: (LocalDate) -> Unit,
    onShow: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val numeralMode = LocalNumeralMode.current
    val suggested = remember(after, catalog, lunarOffsetDays, thursdayClosed, zone) {
        val year = PersianDateUtils.of(after.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), zone).year
        // Deliberately ignore visibility filters: hiding religious events must NOT make Eid a workday.
        val years = (year..year + 2).toList()
        val index = OccasionIndex.build(catalog, years, lunarOffsetDays, OccasionCategory.publicCategories, zone)
        val holidays = years.flatMap(index::forYear).filter { it.occasion.isHoliday }.mapNotNull {
            PersianDateUtils.startOfJalaliDay(it.year, it.month, it.day, zone)?.let { epoch ->
                Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
            }
        }.toSet()
        WorkdayPlanner.nextWorkingDate(after, holidays, thursdayClosed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_workday_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (suggested != null) {
                    val epoch = suggested.atStartOfDay(zone).toInstant().toEpochMilli()
                    Text(PersianDateUtils.fullDateWithWeekday(epoch, languageTag, numeralMode, zone), style = MaterialTheme.typography.titleMedium)
                    val count = tasks.count { !it.isArchived && !it.isCompleted && it.dueAtEpochMillis?.let { millis ->
                        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == suggested
                    } == true }
                    Text(TextNormalizer.formatDigits(stringResource(R.string.calendar_workday_tasks, count), numeralMode))
                } else Text(stringResource(R.string.calendar_workday_missing))
                Text(stringResource(if (thursdayClosed) R.string.calendar_workday_thursday else R.string.calendar_workday_friday))
                Text(stringResource(R.string.calendar_workday_caveat), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (suggested != null) TextButton(onClick = { onAdd(suggested) }) { Text(stringResource(R.string.calendar_add_task)) }
        },
        dismissButton = {
            TextButton(onClick = { if (suggested != null) onShow(suggested) else onDismiss() }) {
                Text(stringResource(if (suggested != null) R.string.calendar_workday_show else R.string.picker_cancel))
            }
        },
    )
}
