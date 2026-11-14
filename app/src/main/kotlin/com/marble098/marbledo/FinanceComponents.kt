package com.marble098.marbledo

import android.content.Context
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import com.marble098.marbledo.ui.GreetingHeadline
import com.marble098.marbledo.ui.GreetingSky
import com.marble098.marbledo.ui.FocusOrbit
import com.marble098.marbledo.ui.auroraBrush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marbledo.core.designsystem.LocalReduceMotion
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleMotion
import com.marbledo.core.designsystem.MarbleSpacing
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.CountdownTheme
import com.marbledo.domain.model.DashboardViewMode
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.enabledOccasionCultures
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.OccasionCatalog
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionIndex
import com.marbledo.feature.calendar.OccasionPlanRequest
import com.marbledo.feature.calendar.OccasionPlanner
import com.marbledo.feature.calendar.OccasionTemplates
import com.marbledo.feature.calendar.OccasionPlanSheet
import com.marbledo.feature.calendar.OccasionRadarCard
import com.marbledo.feature.calendar.OccasionRecommendation
import com.marbledo.feature.calendar.UpcomingOccasion
import com.marbledo.feature.calendar.PersianDateUtils
import com.marbledo.feature.calendar.categoryColor
import com.marbledo.feature.countdown.CountdownCreateSheet
import com.marbledo.feature.countdown.CountdownFace
import com.marbledo.feature.countdown.CountdownMiniCard
import com.marbledo.feature.countdown.ThemeSwatchRow
import com.marbledo.feature.countdown.countdownThemeLabel as themeLabelOf
import com.marbledo.feature.tasks.SectionHeader
import com.marbledo.feature.tasks.TaskEditorSheet
import com.marbledo.feature.tasks.TaskFilter
import com.marbledo.feature.tasks.TaskQuickAddSheet
import com.marbledo.feature.tasks.TaskRowCard
import com.marbledo.feature.tasks.TaskSort
import com.marbledo.feature.tasks.TasksViewModel
import com.marbledo.feature.tasks.localizedTaskDate
import com.marbledo.feature.tasks.localizedTaskDateTime
import com.marbledo.feature.tasks.priorityColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/** Finance section UI components */
@Composable
fun FinanceMonitoringCard(
    debts: List<com.marbledo.domain.model.Debt>,
    checks: List<com.marbledo.domain.model.Check>,
    onAddDebt: () -> Unit,
    onAddCheck: () -> Unit,
    onToggleDebtPaid: (Long) -> Unit,
    onToggleCheckCleared: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numeralMode = LocalNumeralMode.current
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = MarbleSpacing.lg, vertical = MarbleSpacing.md),
        shape = shape,
        color = colors.surfaceVariant.copy(alpha = 0.55f),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MarbleSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    stringResource(R.string.finance_monitoring_title),
                    style = MarbleTextStyles.sectionTitle,
                    modifier = Modifier.weight(1f),
                )
                Row {
                    IconButton(onClick = onAddDebt) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(MarbleSpacing.xs))
                    Text(stringResource(R.string.finance_add_debt), style = MarbleTextStyles.metaLabel, color = colors.primary)
                }
                Spacer(Modifier.width(MarbleSpacing.sm))
                Row {
                    IconButton(onClick = onAddCheck) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(MarbleSpacing.xs))
                    Text(stringResource(R.string.finance_add_check), style = MarbleTextStyles.metaLabel, color = colors.tertiary)
                }
            }

            // Debts Section
            if (debts.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.finance_debts_title), debts.size)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = MarbleSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
                    ) {
                        items(debts) { debt ->
                            FinanceItemCard(
                                item = debt,
                                accent = colors.primary,
                                isPaid = debt.isPaid,
                                onToggle = { onToggleDebtPaid(debt.id) },
                                onEdit = {}, // TODO: Implement edit sheet
                                onLongPress = {}, // TODO: Implement long press
                                modifier = Modifier,
                                isDebt = true,
                            )
                        }
                    }
                }
            }

            // Checks Section
            if (checks.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.finance_checks_title), checks.size)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = MarbleSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
                    ) {
                        items(checks) { check ->
                            FinanceItemCard(
                                item = check,
                                accent = colors.tertiary,
                                isPaid = check.isCleared,
                                onToggle = { onToggleCheckCleared(check.id) },
                                onEdit = {}, // TODO: Implement edit sheet
                                onLongPress = {}, // TODO: Implement long press
                                modifier = Modifier,
                                isDebt = false,
                            )
                        }
                    }
                }
            }

            if (debts.isEmpty() && checks.isEmpty()) {
                Text(
                    stringResource(R.string.finance_empty_title),
                    style = MarbleTextStyles.itemTitle,
                    textAlign = TextAlign.Center,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FinanceItemCard(
    item: Any, // Debt or Check
    accent: Color,
    isPaid: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
    isDebt: Boolean,
) {
    val title = if (isDebt) (item as com.marbledo.domain.model.Debt).title else (item as com.marbledo.domain.model.Check).title
    val amount = if (isDebt) (item as com.marbledo.domain.model.Debt).amount else (item as com.marbledo.domain.model.Check).amount
    val dueAt = if (isDebt) (item as com.marbledo.domain.model.Debt).dueAtEpochMillis else (item as com.marbledo.domain.model.Check).dueAtEpochMillis
    val numeralMode = LocalNumeralMode.current

    Card(
        modifier = modifier.combinedClickable(onClick = onEdit, onLongClick = onLongPress),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isPaid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MarbleSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .toggleable(value = isPaid, role = Role.Checkbox) { onToggle() }
                        .semantics(mergeDescendants = true) { contentDescription = title },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPaid) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isPaid) accent else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (isPaid) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            
            // Amount and Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
            ) {
                Text(
                    TextNormalizer.formatDigits(amount.toString(), numeralMode),
                    style = MarbleTextStyles.cardTitle,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(MarbleSpacing.xs))
                Text(
                    stringResource(R.string.finance_currency_label, if (isDebt) (item as com.marbledo.domain.model.Debt).currency else (item as com.marbledo.domain.model.Check).currency),
                    style = MarbleTextStyles.metaLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (dueAt != null) {
                    Text(
                        localizedTaskDate(dueAt),
                        style = MarbleTextStyles.metaLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        stringResource(R.string.finance_no_date),
                        style = MarbleTextStyles.metaLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Redesigned Notifications following Material Expressive */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ExpressiveNotificationBanner(
    title: String,
    subtitle: String,
    mood: String = "TASKS_DUE", // "HOLIDAY", "RELIGIOUS", "OCCASION", "TASKS_DUE", "QUIET"
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current
    
    // Material 3 Expressive Banner: rounded corners, expressive spring motion, and distinct mood accent
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceVariant.copy(alpha = 0.8f))
            .padding(MarbleSpacing.lg),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceVariant.copy(alpha = 0.8f),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
        ) {
            // Mood accent rule at the top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        when (mood) {
                            "HOLIDAY" -> colors.tertiaryContainer
                            "RELIGIOUS" -> colors.tertiaryContainer
                            "OCCASION" -> colors.tertiaryContainer
                            "TASKS_DUE" -> colors.primaryContainer
                            "QUIET" -> colors.outlineVariant
                            else -> colors.primaryContainer
                        }
                    ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
            ) {
                Icon(
                    when (mood) {
                        "HOLIDAY", "RELIGIOUS", "OCCASION" -> Icons.Outlined.StarBorder
                        "TASKS_DUE" -> Icons.Outlined.Timer
                        "QUIET" -> Icons.Outlined.AccessTime
                        else -> Icons.Outlined.AccessTime
                    },
                    contentDescription = null,
                    tint = when (mood) {
                        "HOLIDAY", "RELIGIOUS", "OCCASION" -> colors.tertiary
                        "TASKS_DUE" -> colors.primary
                        "QUIET" -> colors.outline
                        else -> colors.primary
                    },
                    modifier = Modifier.size(18.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MarbleTextStyles.itemTitle,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MarbleTextStyles.metaLabel,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Expressive pill button
                Surface(
                    shape = CircleShape,
                    color = when (mood) {
                        "HOLIDAY", "RELIGIOUS", "OCCASION" -> colors.tertiaryContainer
                        "TASKS_DUE" -> colors.primaryContainer
                        "QUIET" -> colors.outlineVariant
                        else -> colors.primaryContainer
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = {})
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = null,
                            tint = when (mood) {
                                "HOLIDAY", "RELIGIOUS", "OCCASION" -> colors.onTertiaryContainer
                                "TASKS_DUE" -> colors.onPrimaryContainer
                                "QUIET" -> colors.onSurfaceVariant
                                else -> colors.onPrimaryContainer
                            },
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(R.string.dash_share_today),
                            style = MarbleTextStyles.metaLabel,
                            color = when (mood) {
                                "HOLIDAY", "RELIGIOUS", "OCCASION" -> colors.onTertiaryContainer
                                "TASKS_DUE" -> colors.onPrimaryContainer
                                "QUIET" -> colors.onSurfaceVariant
                                else -> colors.onPrimaryContainer
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
