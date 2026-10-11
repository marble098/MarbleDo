package com.marbledo.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OccasionFilterSheet(
    category: OccasionCategory?,
    calendar: OccasionCalendar?,
    enabledCategories: Set<OccasionCategory>,
    onCategory: (OccasionCategory?) -> Unit,
    onCalendar: (OccasionCalendar?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.calendar_filters_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.calendar_filters_category), style = MaterialTheme.typography.titleSmall)
            FilterChip(selected = category == null, onClick = { onCategory(null) }, label = { Text(stringResource(R.string.calendar_filter_all)) })
            OccasionCategory.entries.filter { it in enabledCategories }.forEach { option ->
                FilterChip(selected = category == option, onClick = { onCategory(option) }, label = { Text(stringResource(categoryLabelRes(option))) })
            }
            Text(stringResource(R.string.calendar_filters_calendar), style = MaterialTheme.typography.titleSmall)
            FilterChip(selected = calendar == null, onClick = { onCalendar(null) }, label = { Text(stringResource(R.string.calendar_filter_all)) })
            OccasionCalendar.entries.forEach { option ->
                FilterChip(selected = calendar == option, onClick = { onCalendar(option) }, label = { Text(stringResource(calendarLabelRes(option))) })
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_confirm)) }
        }
    }
}

internal fun calendarLabelRes(calendar: OccasionCalendar): Int = when (calendar) {
    OccasionCalendar.JALALI -> R.string.picker_calendar_persian
    OccasionCalendar.LUNAR -> R.string.picker_calendar_islamic
    OccasionCalendar.GREGORIAN -> R.string.picker_calendar_gregorian
}
