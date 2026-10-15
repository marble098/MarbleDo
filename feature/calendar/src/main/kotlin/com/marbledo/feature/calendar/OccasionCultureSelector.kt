package com.marbledo.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marbledo.domain.model.OccasionCulture

/** One persisted selection, used by the calendar, radar, exports, notifications, and widget. */
@Composable
fun OccasionCultureSelector(selectedIds: List<String>, onChange: (List<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.culture_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.culture_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OccasionCulture.entries.forEach { culture ->
                FilterChip(selected = culture.key in selectedIds,
                    onClick = { onChange(if (culture.key in selectedIds) selectedIds - culture.key else selectedIds + culture.key) },
                    label = { Text(stringResource(cultureLabelRes(culture))) })
            }
        }
        if (selectedIds.isEmpty()) Text(stringResource(R.string.culture_none), style = MaterialTheme.typography.bodySmall)
    }
}

fun cultureLabelRes(culture: OccasionCulture): Int = when (culture) {
    OccasionCulture.IRANIAN -> R.string.culture_iranian
    OccasionCulture.GLOBAL -> R.string.culture_global
    OccasionCulture.WESTERN -> R.string.culture_western
    OccasionCulture.NORTH_AMERICA -> R.string.culture_north_america
    OccasionCulture.EUROPE -> R.string.culture_europe
    OccasionCulture.OCEANIA -> R.string.culture_oceania
}
