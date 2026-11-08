package com.marble098.marbledo

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.MarbleMotion
import com.marbledo.core.designsystem.MarbleSpacing
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.AgingSummary
import com.marbledo.domain.util.BudgetVerdict
import com.marbledo.domain.util.DailyFocusPlanner
import com.marbledo.domain.util.FocusBudget
import com.marbledo.domain.util.FocusBudgetPlanner
import com.marbledo.domain.util.FocusPick
import com.marbledo.domain.util.FocusReason
import com.marbledo.domain.util.GapFinder
import com.marbledo.domain.util.TaskAging
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.domain.util.TimeGap
import com.marbledo.domain.util.WeekBalancePlanner
import com.marbledo.feature.calendar.PersianDateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The day, made small enough to start.
 *
 * Five everyday planning tools live in this one calm card instead of five new screens: the day's
 * three picks (with the reason each one earned its place), a budget that compares the planned
 * effort with the minutes the day has left, the next free gap and the task that fits it, and a
 * gentle note about work that has been waiting too long. The week's balance lives on the Week
 * stop, where the rest of the week already is.
 *
 * Collapsed it is one line, so the task list — the reason the screen exists — is never pushed
 * down by advice the user did not ask for.
 */
@Composable
internal fun DailyPlanCard(
    tasks: List<Task>,
    now: Instant,
    zone: ZoneId,
    numeralMode: NumeralMode,
    onStart: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Minute resolution: the card is about the shape of the day, not about a ticking clock.
    val nowMinute = now.epochSecond / 60L
    val picks = remember(tasks, nowMinute, zone) { DailyFocusPlanner.top(tasks, now, zone, limit = 3) }
    val budget = remember(tasks, nowMinute, zone) { FocusBudgetPlanner.today(tasks, now, zone) }
    val gap = remember(tasks, nowMinute, zone) { GapFinder.nextGap(tasks, now, zone) }
    val fitting = remember(tasks, gap, nowMinute, zone) { gap?.let { GapFinder.fitting(tasks, it, now, zone) } }
    val aging = remember(tasks, nowMinute) { TaskAging.summary(tasks, now) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MarbleMotion.spatial(),
        label = "dailyPlanChevron",
    )
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = MarbleMotion.spatial())
                .padding(horizontal = MarbleSpacing.lg, vertical = MarbleSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable(
                        onClickLabel = stringResource(if (expanded) R.string.dash_plan_collapse else R.string.dash_plan_expand),
                        role = Role.Button,
                        onClick = { expanded = !expanded },
                    )
                    .padding(vertical = MarbleSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (picks.isEmpty()) colors.outlineVariant else colors.primary),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
                    Text(
                        stringResource(R.string.dash_plan_title),
                        style = MarbleTextStyles.itemTitle,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(
                            R.string.dash_plan_collapsed,
                            digits(picks.size, numeralMode),
                            durationLabel(budget.freeMinutes, numeralMode),
                        ),
                        style = MarbleTextStyles.microLabel,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(chevron),
                )
            }

            if (expanded) {
                HorizontalDivider(color = colors.outline.copy(alpha = 0.14f))
                if (picks.isNotEmpty()) {
                    PlanSectionLabel(stringResource(R.string.dash_plan_start_here))
                    picks.forEach { pick ->
                        FocusPickRow(
                            pick = pick,
                            now = now,
                            numeralMode = numeralMode,
                            onStart = { onStart(pick.task) },
                        )
                    }
                }
                BudgetBlock(budget, numeralMode)
                GapBlock(gap, fitting, numeralMode, onStart)
                AgingBlock(aging, numeralMode, onEdit)
            }
        }
    }
}

/** One pick: the task, the reason it is here, and the effort it costs. */
@Composable
private fun FocusPickRow(
    pick: FocusPick,
    now: Instant,
    numeralMode: NumeralMode,
    onStart: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (pick.reason == FocusReason.OVERDUE) colors.error else colors.primary),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
            Text(
                pick.task.title,
                style = MarbleTextStyles.itemTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                reasonLabel(pick, now, numeralMode),
                style = MarbleTextStyles.microLabel,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(R.string.dash_plan_minutes, digits(pick.minutes, numeralMode)),
            style = MarbleTextStyles.metaLabel,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
        TextButton(onClick = onStart) {
            Text(stringResource(R.string.dash_plan_start), maxLines = 1)
        }
    }
}

@Composable
private fun PlanSectionLabel(text: String) {
    Text(
        text,
        style = MarbleTextStyles.metaLabel,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
    )
}

/** Planned effort against the minutes the day has left, with an honest verdict. */
@Composable
private fun BudgetBlock(budget: FocusBudget, numeralMode: NumeralMode) {
    val colors = MaterialTheme.colorScheme
    val target = if (budget.freeMinutes <= 0) {
        if (budget.plannedMinutes > 0) 1f else 0f
    } else {
        (budget.plannedMinutes.toFloat() / budget.freeMinutes.toFloat()).coerceIn(0f, 1f)
    }
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = MarbleMotion.spatial(),
        label = "dailyPlanBudget",
    )
    val tone = when (budget.verdict) {
        BudgetVerdict.OVER -> colors.error
        BudgetVerdict.TIGHT -> colors.tertiary
        else -> colors.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
            Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
            Text(
                stringResource(
                    R.string.dash_plan_budget,
                    durationLabel(budget.plannedMinutes, numeralMode),
                    durationLabel(budget.freeMinutes, numeralMode),
                ),
                style = MarbleTextStyles.metaLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = tone,
            trackColor = colors.surfaceVariant,
        )
        Text(
            stringResource(
                when (budget.verdict) {
                    BudgetVerdict.EMPTY -> R.string.dash_plan_budget_empty
                    BudgetVerdict.FITS -> R.string.dash_plan_budget_fits
                    BudgetVerdict.TIGHT -> R.string.dash_plan_budget_tight
                    BudgetVerdict.OVER -> R.string.dash_plan_budget_over
                },
            ),
            style = MarbleTextStyles.microLabel,
            color = colors.onSurfaceVariant,
        )
        if (budget.unestimatedTasks > 0) {
            Text(
                stringResource(R.string.dash_plan_budget_unestimated, digits(budget.unestimatedTasks, numeralMode)),
                style = MarbleTextStyles.microLabel,
                color = colors.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

/** The free stretch that starts now, and the task that actually fits it. */
@Composable
private fun GapBlock(
    gap: TimeGap?,
    fitting: Task?,
    numeralMode: NumeralMode,
    onStart: (Task) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
            PlanSectionLabel(stringResource(R.string.dash_plan_gap_title))
            Text(
                text = if (gap == null) {
                    stringResource(R.string.dash_plan_gap_none)
                } else {
                    stringResource(R.string.dash_plan_gap, durationLabel(gap.minutes, numeralMode))
                },
                style = MarbleTextStyles.microLabel,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (gap != null) {
            if (fitting == null) {
                Text(
                    stringResource(R.string.dash_plan_gap_none),
                    style = MarbleTextStyles.microLabel,
                    color = colors.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.dash_plan_gap_fits, fitting.title),
                        style = MarbleTextStyles.metaLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onStart(fitting) }) {
                        Text(stringResource(R.string.dash_plan_start), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Work that has been waiting too long, said once and quietly. */
@Composable
private fun AgingBlock(aging: AgingSummary, numeralMode: NumeralMode, onEdit: (Task) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
            PlanSectionLabel(stringResource(R.string.dash_plan_aging_title))
            Text(
                if (aging.nudging) {
                    stringResource(R.string.dash_plan_aging, digits(aging.overWeek, numeralMode))
                } else {
                    stringResource(R.string.dash_plan_aging_none)
                },
                style = MarbleTextStyles.microLabel,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val oldest = aging.oldest
        if (aging.nudging && oldest != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.dash_plan_aging_oldest,
                        oldest.title,
                        digits(aging.oldestDays.toInt(), numeralMode),
                    ),
                    style = MarbleTextStyles.metaLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onEdit(oldest) }) {
                    Text(stringResource(R.string.dash_plan_open), maxLines = 1)
                }
            }
        }
    }
}

/**
 * Week balance: which day of the coming week carries the work, which one is calm, and the one
 * sentence that fixes it. Shown on the Week stop, next to the workload it describes.
 */
@Composable
internal fun WeekBalanceRow(
    tasks: List<Task>,
    now: Instant,
    zone: ZoneId,
    languageTag: String,
    numeralMode: NumeralMode,
    modifier: Modifier = Modifier,
) {
    val nowMinute = now.epochSecond / 60L
    val balance = remember(tasks, nowMinute, zone) { WeekBalancePlanner.balance(tasks, now, zone) }
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = MarbleSpacing.lg, vertical = MarbleSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
        ) {
            PlanSectionLabel(stringResource(R.string.dash_plan_week_title))
            Text(
                stringResource(
                    R.string.dash_plan_week_balance,
                    dayLabel(balance.heaviestDay, zone, languageTag),
                    digits(balance.heaviestCount, numeralMode),
                    dayLabel(balance.lightestDay, zone, languageTag),
                    digits(balance.lightestCount, numeralMode),
                ),
                style = MarbleTextStyles.metaLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (balance.imbalanced) {
                    stringResource(
                        R.string.dash_plan_week_move,
                        dayLabel(balance.heaviestDay, zone, languageTag),
                        dayLabel(balance.lightestDay, zone, languageTag),
                    )
                } else {
                    stringResource(R.string.dash_plan_week_even)
                },
                style = MarbleTextStyles.microLabel,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun reasonLabel(pick: FocusPick, now: Instant, numeralMode: NumeralMode): String = when (pick.reason) {
    FocusReason.OVERDUE -> stringResource(R.string.dash_plan_reason_overdue)
    FocusReason.DUE_TODAY -> stringResource(R.string.dash_plan_reason_today)
    FocusReason.URGENT -> stringResource(R.string.dash_plan_reason_urgent)
    FocusReason.PINNED -> stringResource(R.string.dash_plan_reason_pinned)
    FocusReason.AGING -> stringResource(
        R.string.dash_plan_reason_aging,
        digits(DailyFocusPlanner.waitingDays(pick.task, now).toInt(), numeralMode),
    )
    FocusReason.QUICK_WIN -> stringResource(R.string.dash_plan_reason_quick)
    FocusReason.NEXT_UP -> stringResource(R.string.dash_plan_reason_next)
}

/** `۴۵ دقیقه` or `۱ ساعت و ۳۰ دقیقه`, never `0 دقیقه` for a real amount of time. */
@Composable
private fun durationLabel(minutes: Int, numeralMode: NumeralMode): String {
    val safe = minutes.coerceAtLeast(0)
    return if (safe >= 60) {
        stringResource(
            R.string.dash_plan_hours_minutes,
            digits(safe / 60, numeralMode),
            digits(safe % 60, numeralMode),
        )
    } else {
        stringResource(R.string.dash_plan_minutes, digits(safe, numeralMode))
    }
}

private fun digits(value: Int, numeralMode: NumeralMode): String =
    TextNormalizer.formatDigits(value.toString(), numeralMode)

@Composable
private fun dayLabel(date: LocalDate?, zone: ZoneId, languageTag: String): String {
    if (date == null) return ""
    val epoch = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val persian = PersianDateUtils.of(epoch, zone)
    return PersianDateUtils.weekdayName(persian.weekdayIndex, languageTag)
}
