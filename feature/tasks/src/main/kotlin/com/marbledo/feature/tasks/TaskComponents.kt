package com.marbledo.feature.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.util.TextNormalizer

/** Colour used for a priority across rows, chips and rails. */
@Composable
fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.LOW -> MaterialTheme.colorScheme.tertiary
    TaskPriority.NORMAL -> MaterialTheme.colorScheme.primary
    TaskPriority.HIGH -> Color(0xFFDB8C27)
    TaskPriority.URGENT -> MaterialTheme.colorScheme.error
}

@Composable
fun priorityLabel(priority: TaskPriority): String = when (priority) {
    TaskPriority.LOW -> stringResource(R.string.tasks_priority_low)
    TaskPriority.NORMAL -> stringResource(R.string.tasks_priority_normal)
    TaskPriority.HIGH -> stringResource(R.string.tasks_priority_high)
    TaskPriority.URGENT -> stringResource(R.string.tasks_priority_urgent)
}

@Composable
fun repeatLabel(frequency: com.marbledo.domain.model.RepeatFrequency): String = when (frequency) {
    com.marbledo.domain.model.RepeatFrequency.NONE -> stringResource(R.string.tasks_repeat_none)
    com.marbledo.domain.model.RepeatFrequency.DAILY -> stringResource(R.string.tasks_repeat_daily)
    com.marbledo.domain.model.RepeatFrequency.WEEKLY -> stringResource(R.string.tasks_repeat_weekly)
    com.marbledo.domain.model.RepeatFrequency.MONTHLY -> stringResource(R.string.tasks_repeat_monthly)
    com.marbledo.domain.model.RepeatFrequency.YEARLY -> stringResource(R.string.tasks_repeat_yearly)
    com.marbledo.domain.model.RepeatFrequency.CUSTOM -> stringResource(R.string.tasks_repeat_weekly)
}

@Composable
fun localizedTaskDate(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val numeralMode = LocalNumeralMode.current
    return remember(epochMillis, locale, numeralMode) { TaskDateLabels.tripleDate(epochMillis, locale, numeralMode) }
}

@Composable
fun SectionHeader(title: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    TextNormalizer.formatDigits(count.toString(), LocalNumeralMode.current),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * Task row used by the dashboard: priority rail, inline completion, countdown switch and a menu
 * with pin/archive/delete. Long-press toggles multi-selection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRowCard(
    task: Task,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenCountdown: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onToggleCountdown: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val railColor = priorityColor(task.priority)
    val hasCountdown = task.dueAtEpochMillis != null && task.countdownEnabled
    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(onClick = onEdit, onLongClick = onLongPress),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(railColor),
            )
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                modifier = Modifier.align(Alignment.CenterVertically).semantics { contentDescription = task.title },
            )
            Column(
                Modifier.weight(1f).align(Alignment.CenterVertically).padding(vertical = 10.dp, horizontal = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (task.isPinned) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = stringResource(R.string.tasks_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 5.dp).size(15.dp),
                        )
                    }
                    if (task.recurrence != null) {
                        Icon(
                            Icons.Outlined.Timer,
                            contentDescription = stringResource(R.string.tasks_repeat_daily),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 5.dp).size(14.dp),
                        )
                    }
                }
                Text(
                    text = task.dueAtEpochMillis?.let { stringResource(R.string.tasks_due_at, localizedTaskDate(it)) }
                        ?: stringResource(R.string.tasks_no_due_date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (task.category.isNotBlank() || task.tags.isNotEmpty()) {
                    val labels = listOfNotNull(
                        task.category.takeIf { it.isNotBlank() },
                        task.tags.joinToString(" · ").takeIf { task.tags.isNotEmpty() },
                    )
                    Text(
                        labels.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onToggleCountdown, enabled = task.dueAtEpochMillis != null) {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = stringResource(if (hasCountdown) R.string.tasks_countdown_off else R.string.tasks_countdown_on),
                    tint = when {
                        !hasCountdown -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
            Box(Modifier.align(Alignment.CenterVertically)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.tasks_more))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_edit_title)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_open_countdown)) },
                        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                        enabled = task.dueAtEpochMillis != null,
                        onClick = { menuExpanded = false; onOpenCountdown() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (hasCountdown) R.string.tasks_countdown_off else R.string.tasks_countdown_on)) },
                        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                        enabled = task.dueAtEpochMillis != null,
                        onClick = { menuExpanded = false; onToggleCountdown() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (task.isPinned) R.string.tasks_unpin else R.string.tasks_pin)) },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                        onClick = { menuExpanded = false; onPin() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_archive)) },
                        leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                        onClick = { menuExpanded = false; onArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** Compact chip used across the quick-add sheet and the editor. */
@Composable
fun LabelChip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, maxLines = 1) },
    )
}

/** Row of nothing-state hints used when a list is empty. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
