package com.marbledo.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleTextStyles

/**
 * Turns an occasion into something actionable in one tap: a preparation task, a task on the day
 * itself, a live countdown, or a favourite that later drives the pre-occasion reminder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccasionPlanSheet(
    occasion: Occasion,
    epochMillis: Long,
    languageTag: String,
    isFavorite: Boolean,
    prepDays: Int,
    onPlan: (OccasionPlanRequest) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val days = prepDays.coerceAtLeast(1)
    val numeralMode = LocalNumeralMode.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(categoryColor(occasion.category)),
                )
                Text(
                    occasion.title(languageTag),
                    style = MarbleTextStyles.cardTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(R.string.calendar_plan_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlanAction(
                icon = Icons.Outlined.EventAvailable,
                title = stringResource(R.string.calendar_plan_task_on_day),
                subtitle = PersianDateUtils.fullDate(epochMillis, languageTag, numeralMode),
                onClick = { onPlan(OccasionPlanner.planRequest(occasion, epochMillis, languageTag)) },
            )
            PlanAction(
                icon = Icons.Outlined.Alarm,
                title = stringResource(R.string.calendar_plan_prepare, days),
                subtitle = stringResource(R.string.calendar_plan_prepare_hint),
                onClick = {
                    onPlan(OccasionPlanner.planRequest(occasion, epochMillis, languageTag, daysBefore = days))
                },
            )
            PlanAction(
                icon = Icons.Outlined.AddTask,
                title = stringResource(R.string.calendar_plan_countdown),
                subtitle = stringResource(R.string.calendar_plan_countdown_hint),
                onClick = {
                    onPlan(OccasionPlanner.planRequest(occasion, epochMillis, languageTag, asCountdown = true))
                },
            )
            PlanAction(
                icon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                title = stringResource(if (isFavorite) R.string.calendar_favorite_remove else R.string.calendar_favorite_add),
                subtitle = stringResource(R.string.calendar_favorite_hint),
                onClick = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun PlanAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MarbleTextStyles.itemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
