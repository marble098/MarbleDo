package com.marble098.marbledo

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleBox
import com.marbledo.core.designsystem.MarbleBoxBorder
import com.marbledo.core.designsystem.MarbleSpacing
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.Check
import com.marbledo.domain.model.Debt
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.FinanceAmountParser
import com.marbledo.domain.util.FinanceDueState
import com.marbledo.domain.util.FinanceInsights
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.SchedulePickerDialog
import com.marbledo.feature.calendar.SchedulePickerMode
import com.marbledo.feature.calendar.formatScheduleSummary
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

private enum class FinanceListKind { ALL, DEBTS, CHECKS }
private enum class FinanceStatusFilter { ALL, OPEN, OVERDUE, SETTLED }
private enum class FinanceEditorKind { DEBT, CHECK }

private data class FinanceRecord(
    val id: Long,
    val isDebt: Boolean,
    val title: String,
    val amount: Double,
    val currency: String,
    val dueAtEpochMillis: Long?,
    val settled: Boolean,
    val detail: String,
    val accountNumber: String,
    val description: String,
)

/**
 * The finance stop is a real local ledger, not a placeholder: balances are never mixed across
 * currencies, dates are grouped by the user's local day, and debts/cheques can be added, edited,
 * settled or removed without leaving the dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceMonitoringCard(
    debts: List<Debt>,
    checks: List<Check>,
    settings: AppSettings,
    today: LocalDate,
    onCalendarDisplaySelected: (CalendarDisplayMode) -> Unit,
    onSaveDebt: suspend (Debt) -> Unit,
    onSaveCheck: suspend (Check) -> Unit,
    onDeleteDebt: suspend (Long) -> Unit,
    onDeleteCheck: suspend (Long) -> Unit,
    onToggleDebtPaid: suspend (Long) -> Unit,
    onToggleCheckCleared: suspend (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val numeralMode = LocalNumeralMode.current
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val insights = remember(debts, checks, today, zone) { FinanceInsights.build(debts, checks, today, zone) }
    val records = remember(debts, checks) {
        buildList {
            debts.forEach { debt ->
                add(
                    FinanceRecord(
                        id = debt.id,
                        isDebt = true,
                        title = debt.title,
                        amount = debt.amount,
                        currency = debt.currency,
                        dueAtEpochMillis = debt.dueAtEpochMillis,
                        settled = debt.isPaid,
                        detail = debt.creditor,
                        accountNumber = "",
                        description = debt.description,
                    ),
                )
            }
            checks.forEach { check ->
                add(
                    FinanceRecord(
                        id = check.id,
                        isDebt = false,
                        title = check.title,
                        amount = check.amount,
                        currency = check.currency,
                        dueAtEpochMillis = check.dueAtEpochMillis,
                        settled = check.isCleared,
                        detail = check.bankName,
                        accountNumber = check.accountNumber,
                        description = check.description,
                    ),
                )
            }
        }.sortedWith(
            compareBy<FinanceRecord> { it.settled }
                .thenBy { it.dueAtEpochMillis == null }
                .thenBy { it.dueAtEpochMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.id },
        )
    }

    var listKindName by rememberSaveable { mutableStateOf(FinanceListKind.ALL.name) }
    var statusFilterName by rememberSaveable { mutableStateOf(FinanceStatusFilter.ALL.name) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var editorKindName by rememberSaveable { mutableStateOf<String?>(null) }
    var editingDebtId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingCheckId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editingDebt = remember(debts, editingDebtId) { editingDebtId?.let { id -> debts.firstOrNull { it.id == id } } }
    val editingCheck = remember(checks, editingCheckId) { editingCheckId?.let { id -> checks.firstOrNull { it.id == id } } }
    var showAllRecords by rememberSaveable { mutableStateOf(false) }

    val listKind = FinanceListKind.entries.firstOrNull { it.name == listKindName } ?: FinanceListKind.ALL
    val statusFilter = FinanceStatusFilter.entries.firstOrNull { it.name == statusFilterName } ?: FinanceStatusFilter.ALL
    val query = remember(searchQuery) { TextNormalizer.searchKey(searchQuery) }
    val matchingRecords = remember(records, listKind, statusFilter, query, today, zone) {
        records.filter { record ->
            val typeMatches = when (listKind) {
                FinanceListKind.ALL -> true
                FinanceListKind.DEBTS -> record.isDebt
                FinanceListKind.CHECKS -> !record.isDebt
            }
            val dueState = FinanceInsights.dueState(record.dueAtEpochMillis, record.settled, today, zone)
            val statusMatches = when (statusFilter) {
                FinanceStatusFilter.ALL -> true
                FinanceStatusFilter.OPEN -> !record.settled
                FinanceStatusFilter.OVERDUE -> dueState == FinanceDueState.OVERDUE
                FinanceStatusFilter.SETTLED -> record.settled
            }
            val text = TextNormalizer.searchKey(
                listOf(record.title, record.detail, record.currency, record.description, record.accountNumber).joinToString(" "),
            )
            typeMatches && statusMatches && (query.isBlank() || query in text)
        }
    }
    val visibleRecords = if (showAllRecords) matchingRecords else matchingRecords.take(5)
    val nextDue = remember(records, today, zone) {
        records.asSequence()
            .filter { !it.settled && it.dueAtEpochMillis != null }
            .filter { FinanceInsights.dueState(it.dueAtEpochMillis, false, today, zone) != FinanceDueState.OVERDUE }
            .minByOrNull { it.dueAtEpochMillis ?: Long.MAX_VALUE }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
                Text(
                    stringResource(R.string.finance_monitoring_title),
                    style = MarbleTextStyles.screenTitle,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.finance_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = CircleShape,
                color = colors.surfaceContainerHigh,
                contentColor = colors.onSurfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.finance_local_badge), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }

        MarbleBox(padding = MarbleSpacing.sm, gap = MarbleSpacing.sm) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
        ) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    editingDebtId = null
                    editingCheckId = null
                    editorKindName = FinanceEditorKind.DEBT.name
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = MarbleSpacing.md, vertical = 12.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(MarbleSpacing.xs))
                Text(stringResource(R.string.finance_add_debt), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    editingDebtId = null
                    editingCheckId = null
                    editorKindName = FinanceEditorKind.CHECK.name
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = MarbleSpacing.md, vertical = 12.dp),
            ) {
                Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(MarbleSpacing.xs))
                Text(stringResource(R.string.finance_add_check), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        }

        FinanceOverviewPanel(insights, settings.languageTag, numeralMode)

        if (insights.overdueCount > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable(role = Role.Button) { statusFilterName = FinanceStatusFilter.OVERDUE.name },
                shape = MaterialTheme.shapes.large,
                color = colors.errorContainer,
                contentColor = colors.onErrorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = MarbleSpacing.lg, vertical = MarbleSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                ) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.finance_overdue_alert_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            TextNormalizer.formatDigits(
                                stringResource(R.string.finance_overdue_alert_body, insights.overdueCount),
                                numeralMode,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(stringResource(R.string.finance_review), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        } else if (nextDue != null) {
            FinanceNextDueCard(nextDue, today, zone, settings, numeralMode)
        }

        if (records.isEmpty()) {
            FinanceEmptyState(
                onAddDebt = {
                    editingDebtId = null
                    editingCheckId = null
                    editorKindName = FinanceEditorKind.DEBT.name
                },
                onAddCheck = {
                    editingDebtId = null
                    editingCheckId = null
                    editorKindName = FinanceEditorKind.CHECK.name
                },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.finance_activity_title),
                        style = MarbleTextStyles.sectionTitle,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        TextNormalizer.formatDigits(matchingRecords.size.toString(), numeralMode),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }

                MarbleBox(padding = MarbleSpacing.sm, gap = MarbleSpacing.sm) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.finance_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = MaterialTheme.shapes.large,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                ) {
                    FinanceListKind.entries.forEach { option ->
                        FilterChip(
                            selected = listKind == option,
                            onClick = { listKindName = option.name; showAllRecords = false },
                            label = { Text(stringResource(listKindLabel(option))) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                ) {
                    FinanceStatusFilter.entries.forEach { option ->
                        FilterChip(
                            selected = statusFilter == option,
                            onClick = { statusFilterName = option.name; showAllRecords = false },
                            label = { Text(stringResource(statusFilterLabel(option))) },
                        )
                    }
                }
                }

                if (matchingRecords.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = colors.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.finance_no_results),
                            modifier = Modifier.padding(MarbleSpacing.lg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                } else {
                    visibleRecords.forEach { record ->
                        FinanceRecordCard(
                            record = record,
                            today = today,
                            zone = zone,
                            settings = settings,
                            numeralMode = numeralMode,
                            onOpen = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (record.isDebt) {
                                    editingDebtId = record.id
                                    editingCheckId = null
                                    editorKindName = FinanceEditorKind.DEBT.name
                                } else {
                                    editingCheckId = record.id
                                    editingDebtId = null
                                    editorKindName = FinanceEditorKind.CHECK.name
                                }
                            },
                            onToggle = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    runCatching {
                                        if (record.isDebt) onToggleDebtPaid(record.id)
                                        else onToggleCheckCleared(record.id)
                                    }
                                }
                            },
                        )
                    }
                    if (matchingRecords.size > 5) {
                        TextButton(
                            onClick = { showAllRecords = !showAllRecords },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                stringResource(if (showAllRecords) R.string.finance_show_less else R.string.finance_show_all),
                            )
                        }
                    }
                }
            }
        }
    }

    val editorKind = editorKindName?.let { name -> FinanceEditorKind.entries.firstOrNull { it.name == name } }
    if (editorKind != null) {
        FinanceEditorSheet(
            kind = editorKind,
            debt = editingDebt,
            check = editingCheck,
            settings = settings,
            onCalendarDisplaySelected = onCalendarDisplaySelected,
            onSave = { debt, check ->
                when {
                    debt != null -> onSaveDebt(debt)
                    check != null -> onSaveCheck(check)
                }
            },
            onDelete = {
                when {
                    editingDebt != null -> onDeleteDebt(editingDebt.id)
                    editingCheck != null -> onDeleteCheck(editingCheck.id)
                    else -> Unit
                }
            },
            onDismiss = {
                editorKindName = null
                editingDebtId = null
                editingCheckId = null
            },
        )
    }
}

@Composable
private fun FinanceOverviewPanel(
    insights: FinanceInsights,
    languageTag: String,
    numeralMode: NumeralMode,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MarbleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm)) {
                Surface(shape = CircleShape, color = colors.onPrimaryContainer.copy(alpha = 0.08f)) {
                    Icon(
                        Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.finance_overview_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.finance_currency_note), style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer.copy(alpha = 0.76f))
                }
            }
            HorizontalDivider(color = colors.onPrimaryContainer.copy(alpha = 0.14f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.md),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
                    Text(stringResource(R.string.finance_open_debts), style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer.copy(alpha = 0.75f))
                    FinanceCurrencyTotals(insights.outstandingDebts, languageTag, numeralMode, horizontal = false)
                }
                Surface(
                    modifier = Modifier.width(1.dp).height(56.dp),
                    color = colors.onPrimaryContainer.copy(alpha = 0.12f),
                ) {}
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
                    Text(stringResource(R.string.finance_pending_checks), style = MaterialTheme.typography.labelMedium, color = colors.onPrimaryContainer.copy(alpha = 0.75f))
                    FinanceCurrencyTotals(insights.pendingChecks, languageTag, numeralMode, horizontal = false)
                }
            }
            HorizontalDivider(color = colors.onPrimaryContainer.copy(alpha = 0.14f))
            Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.finance_settled_this_month_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.weight(1f),
                    )
                    Surface(shape = CircleShape, color = colors.onPrimaryContainer.copy(alpha = 0.10f)) {
                        Text(
                            if (insights.settledThisMonthCount > 0) {
                                TextNormalizer.formatDigits(
                                    stringResource(R.string.finance_settled_this_month_items, insights.settledThisMonthCount),
                                    numeralMode,
                                )
                            } else {
                                stringResource(R.string.finance_settled_this_month_empty)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = MarbleSpacing.sm, vertical = MarbleSpacing.xs),
                        )
                    }
                }
                if (insights.settledThisMonthAmounts.isNotEmpty()) {
                    FinanceCurrencyTotals(insights.settledThisMonthAmounts, languageTag, numeralMode, horizontal = true)
                }
            }
            if (insights.overdueAmounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
                    Text(
                        stringResource(R.string.finance_overdue_amount_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    FinanceCurrencyTotals(insights.overdueAmounts, languageTag, numeralMode, horizontal = true)
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
    ) {
        FinanceMetricCard(
            label = stringResource(R.string.finance_metric_overdue),
            value = insights.overdueCount,
            icon = Icons.Outlined.WarningAmber,
            accent = colors.error,
            surface = colors.errorContainer,
            content = colors.onErrorContainer,
            modifier = Modifier.weight(1f),
            numeralMode = numeralMode,
        )
        FinanceMetricCard(
            label = stringResource(R.string.finance_metric_due_soon),
            value = insights.dueTodayCount + insights.dueSoonCount,
            icon = Icons.Outlined.CalendarMonth,
            accent = colors.tertiary,
            surface = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
            modifier = Modifier.weight(1f),
            numeralMode = numeralMode,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
    ) {
        FinanceMetricCard(
            label = stringResource(R.string.finance_metric_open_debts),
            value = insights.openDebtCount,
            icon = Icons.Outlined.AccountBalanceWallet,
            accent = colors.primary,
            surface = colors.primaryContainer,
            content = colors.onPrimaryContainer,
            modifier = Modifier.weight(1f),
            numeralMode = numeralMode,
        )
        FinanceMetricCard(
            label = stringResource(R.string.finance_metric_open_checks),
            value = insights.openCheckCount,
            icon = Icons.Outlined.ReceiptLong,
            accent = colors.secondary,
            surface = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
            modifier = Modifier.weight(1f),
            numeralMode = numeralMode,
        )
    }
}

@Composable
private fun FinanceCurrencyTotals(
    totals: Map<String, BigDecimal>,
    languageTag: String,
    numeralMode: NumeralMode,
    horizontal: Boolean,
) {
    if (totals.isEmpty()) {
        Text("—", style = MarbleTextStyles.cardTitle, color = MaterialTheme.colorScheme.onPrimaryContainer)
        return
    }
    val rowModifier = if (horizontal) Modifier.horizontalScroll(rememberScrollState()) else Modifier
    Column(modifier = rowModifier, verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
        totals.forEach { (currency, amount) ->
            Row(
                modifier = Modifier.padding(end = if (horizontal) MarbleSpacing.sm else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
            ) {
                Text(
                    currencyName(currency),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                )
                Text(
                    formatFinanceAmount(amount, currency, languageTag, numeralMode),
                    modifier = Modifier.weight(1f),
                    style = MarbleTextStyles.cardTitle,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FinanceMetricCard(
    label: String,
    value: Int,
    icon: ImageVector,
    accent: Color,
    surface: Color,
    content: Color,
    modifier: Modifier,
    numeralMode: NumeralMode,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = surface,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MarbleSpacing.md, vertical = MarbleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
                Text(TextNormalizer.formatDigits(value.toString(), numeralMode), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.76f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FinanceNextDueCard(
    record: FinanceRecord,
    today: LocalDate,
    zone: ZoneId,
    settings: AppSettings,
    numeralMode: NumeralMode,
) {
    val colors = MaterialTheme.colorScheme
    val due = record.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today
    val dueLabel = when {
        due == today -> stringResource(R.string.finance_due_today)
        else -> TextNormalizer.formatDigits(
            stringResource(R.string.finance_due_in_days, java.time.temporal.ChronoUnit.DAYS.between(today, due).toInt()),
            numeralMode,
        )
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().border(1.dp, MarbleBoxBorder(colors.surfaceContainerLow), MaterialTheme.shapes.large),
    ) {
        Row(
            modifier = Modifier.padding(MarbleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
        ) {
            Surface(shape = CircleShape, color = colors.tertiaryContainer) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.padding(9.dp).size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
                Text(stringResource(R.string.finance_next_due), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(record.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dueLabel, style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
            }
            Text(
                formatFinanceAmount(record.amount, record.currency, settings.languageTag, numeralMode) + " " + currencyName(record.currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FinanceRecordCard(
    record: FinanceRecord,
    today: LocalDate,
    zone: ZoneId,
    settings: AppSettings,
    numeralMode: NumeralMode,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dueState = FinanceInsights.dueState(record.dueAtEpochMillis, record.settled, today, zone)
    val accent = when (dueState) {
        FinanceDueState.OVERDUE -> colors.error
        FinanceDueState.DUE_TODAY, FinanceDueState.DUE_SOON -> colors.tertiary
        FinanceDueState.SETTLED -> colors.secondary
        else -> colors.primary
    }
    val statusSurface = when (dueState) {
        FinanceDueState.OVERDUE -> colors.errorContainer
        FinanceDueState.DUE_TODAY, FinanceDueState.DUE_SOON -> colors.tertiaryContainer
        FinanceDueState.SETTLED -> colors.secondaryContainer
        else -> colors.surfaceVariant
    }
    val statusContent = when (dueState) {
        FinanceDueState.OVERDUE -> colors.onErrorContainer
        FinanceDueState.DUE_TODAY, FinanceDueState.DUE_SOON -> colors.onTertiaryContainer
        FinanceDueState.SETTLED -> colors.onSecondaryContainer
        else -> colors.onSurfaceVariant
    }
    val dueLabel = when (dueState) {
        FinanceDueState.SETTLED -> stringResource(R.string.finance_status_settled)
        FinanceDueState.OVERDUE -> {
            val date = record.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            val dateText = date?.let { formatScheduleSummary(it, null, settings.countdownCalendar, settings.languageTag, numeralMode, zone) }
                ?: stringResource(R.string.finance_no_date)
            stringResource(R.string.finance_status_overdue_since, dateText)
        }
        FinanceDueState.DUE_TODAY -> stringResource(R.string.finance_due_today)
        FinanceDueState.DUE_SOON -> {
            val date = record.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today
            TextNormalizer.formatDigits(
                stringResource(R.string.finance_due_in_days, java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()),
                numeralMode,
            )
        }
        FinanceDueState.SCHEDULED -> record.dueAtEpochMillis?.let { millis ->
            formatScheduleSummary(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), null, settings.countdownCalendar, settings.languageTag, numeralMode, zone)
        } ?: stringResource(R.string.finance_no_date)
        FinanceDueState.UNDATED -> stringResource(R.string.finance_no_date)
    }
    val typeLabel = stringResource(if (record.isDebt) R.string.finance_debts_title else R.string.finance_checks_title)
    val actionDescription = stringResource(
        if (record.settled) R.string.finance_mark_open else R.string.finance_mark_settled,
    )
    val accountLabel = record.accountNumber.takeIf { it.isNotBlank() }?.let(::maskAccountNumber)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MarbleBoxBorder(if (record.settled) colors.surfaceContainerLow else colors.surface),
                MaterialTheme.shapes.large,
            )
            .clickable(role = Role.Button, onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (record.settled) colors.surfaceContainerLow else colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = MarbleSpacing.md, top = MarbleSpacing.md, end = MarbleSpacing.xs, bottom = MarbleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = accent.copy(alpha = 0.12f),
            ) {
                Icon(
                    if (record.isDebt) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.AccountBalance,
                    contentDescription = typeLabel,
                    tint = accent,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
                    Text(record.title, style = MarbleTextStyles.itemTitle, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Surface(shape = CircleShape, color = statusSurface) {
                        Text(
                            dueLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusContent,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                val secondary = listOfNotNull(
                    record.detail.takeIf { it.isNotBlank() },
                    accountLabel,
                    record.description.takeIf { it.isNotBlank() },
                ).take(2).joinToString(" · ")
                if (secondary.isNotBlank()) {
                    Text(secondary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
                    Text(
                        formatFinanceAmount(record.amount, record.currency, settings.languageTag, numeralMode),
                        style = MaterialTheme.typography.titleSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(currencyName(record.currency), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text("·", color = colors.outline)
                    Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.semantics { contentDescription = actionDescription },
            ) {
                Icon(
                    if (record.settled) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (record.settled) colors.secondary else colors.outline,
                )
            }
        }
    }
}

@Composable
private fun FinanceEmptyState(onAddDebt: () -> Unit, onAddCheck: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MarbleSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
        ) {
            Surface(shape = CircleShape, color = colors.primaryContainer) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.padding(14.dp).size(26.dp))
            }
            Text(stringResource(R.string.finance_empty_title), style = MarbleTextStyles.sectionTitle, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                stringResource(R.string.finance_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm)) {
                Button(onClick = onAddDebt) { Text(stringResource(R.string.finance_add_debt)) }
                OutlinedButton(onClick = onAddCheck) { Text(stringResource(R.string.finance_add_check)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceEditorSheet(
    kind: FinanceEditorKind,
    debt: Debt?,
    check: Check?,
    settings: AppSettings,
    onCalendarDisplaySelected: (CalendarDisplayMode) -> Unit,
    onSave: suspend (Debt?, Check?) -> Unit,
    onDelete: suspend () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val numeralMode = LocalNumeralMode.current
    val editorKey = when {
        debt != null -> "debt:${debt.id}:${debt.updatedAtEpochMillis}"
        check != null -> "check:${check.id}:${check.updatedAtEpochMillis}"
        else -> "new:${kind.name}"
    }
    var title by rememberSaveable(editorKey) { mutableStateOf(debt?.title ?: check?.title.orEmpty()) }
    var amountText by rememberSaveable(editorKey) {
        mutableStateOf((debt?.amount ?: check?.amount)?.let(::amountInputText).orEmpty())
    }
    var currency by rememberSaveable(editorKey) { mutableStateOf(debt?.currency ?: check?.currency ?: "IRT") }
    var detail by rememberSaveable(editorKey) { mutableStateOf(debt?.creditor ?: check?.bankName.orEmpty()) }
    var accountNumber by rememberSaveable(editorKey) { mutableStateOf(check?.accountNumber.orEmpty()) }
    var description by rememberSaveable(editorKey) { mutableStateOf(debt?.description ?: check?.description.orEmpty()) }
    val initialDueDate = remember(editorKey, zone) {
        (debt?.dueAtEpochMillis ?: check?.dueAtEpochMillis)?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }
    }
    var dueDateIso by rememberSaveable(editorKey) { mutableStateOf(initialDueDate) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    var showSaveError by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val existingCurrency = debt?.currency ?: check?.currency
    val currencyOptions = remember(existingCurrency) {
        // Toman is the only Iranian currency: legacy Rial records arrive already folded into
        // Toman by the repository, so Rial is gone from the picker for good.
        (listOf("IRT", "USD", "EUR") + listOfNotNull(existingCurrency))
            .distinct()
    }
    val amount = remember(amountText) { FinanceAmountParser.parse(amountText) }
    val canSave = title.isNotBlank() && amount != null && !isSaving

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MarbleSpacing.lg)
                .padding(bottom = MarbleSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm)) {
                Surface(shape = CircleShape, color = if (kind == FinanceEditorKind.DEBT) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(
                        if (kind == FinanceEditorKind.DEBT) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = if (kind == FinanceEditorKind.DEBT) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            when (kind) {
                                FinanceEditorKind.DEBT -> if (debt == null) R.string.finance_add_debt else R.string.finance_edit_debt
                                FinanceEditorKind.CHECK -> if (check == null) R.string.finance_add_check else R.string.finance_edit_check
                            },
                        ),
                        style = MarbleTextStyles.sectionTitle,
                    )
                    Text(stringResource(R.string.finance_editor_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.finance_title_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.take(24).filter { char -> char.isDigit() || char in "۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩.,٬٫'’ " } },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.finance_amount_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    // Banking apps group the typed number three digits at a time while the field
                    // keeps the raw text the parser reads, so the caret never lies.
                    visualTransformation = remember(numeralMode) { FinanceAmountGroupingTransformation(numeralMode) },
                    isError = amountText.isNotBlank() && amount == null,
                    supportingText = if (amountText.isNotBlank() && amount == null) {
                        { Text(stringResource(R.string.finance_invalid_amount)) }
                    } else {
                        { Text(stringResource(R.string.finance_amount_hint)) }
                    },
                    shape = MaterialTheme.shapes.large,
                )
                // The resting label is aligned with the text row of the field beside it, not with
                // a guessed top padding, so it stays centred when the font scale changes.
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        currencyName(currency),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
            ) {
                currencyOptions.forEach { code ->
                    FilterChip(
                        selected = currency == code,
                        onClick = { currency = code },
                        label = {
                            Text(
                                listOfNotNull(
                                    currencyLabel(code)?.let { stringResource(it) },
                                    code.takeIf { code != "IRT" },
                                ).joinToString(" · "),
                            )
                        },
                    )
                }
            }

            OutlinedTextField(
                value = detail,
                onValueChange = { detail = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(stringResource(if (kind == FinanceEditorKind.DEBT) R.string.finance_counterparty else R.string.finance_bank_name))
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            if (kind == FinanceEditorKind.CHECK) {
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.finance_account_number)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(240) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.finance_description)) },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.large,
            )

            val date = dueDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val dateText = date?.let {
                formatScheduleSummary(it, null, settings.countdownCalendar, settings.languageTag, numeralMode, zone)
            } ?: stringResource(R.string.finance_due_not_set)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MarbleBoxBorder(MaterialTheme.colorScheme.surfaceContainerLow), MaterialTheme.shapes.large)
                    .clickable(role = Role.Button) { showDatePicker = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = MarbleSpacing.md, vertical = MarbleSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.finance_due_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dateText, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (date != null) {
                        TextButton(onClick = { dueDateIso = null }) { Text(stringResource(R.string.finance_clear_date)) }
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = MarbleSpacing.md, vertical = MarbleSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.finance_local_storage_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (showSaveError) {
                Text(stringResource(R.string.finance_save_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (debt != null || check != null) {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(MarbleSpacing.xs))
                        Text(stringResource(R.string.finance_delete))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(R.string.settings_close)) }
                Button(
                    onClick = {
                        val parsedAmount = amount ?: return@Button
                        if (title.isBlank()) return@Button
                        val dueAt = dueDateIso?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() }
                        isSaving = true
                        showSaveError = false
                        scope.launch {
                            runCatching {
                                val now = System.currentTimeMillis()
                                if (kind == FinanceEditorKind.DEBT) {
                                    onSave(
                                        Debt(
                                            id = debt?.id ?: 0L,
                                            title = title.trim(),
                                            amount = parsedAmount,
                                            currency = currency,
                                            dueAtEpochMillis = dueAt,
                                            isPaid = debt?.isPaid ?: false,
                                            creditor = detail.trim(),
                                            description = description.trim(),
                                            createdAtEpochMillis = debt?.createdAtEpochMillis ?: now,
                                            updatedAtEpochMillis = now,
                                        ),
                                        null,
                                    )
                                } else {
                                    onSave(
                                        null,
                                        Check(
                                            id = check?.id ?: 0L,
                                            title = title.trim(),
                                            amount = parsedAmount,
                                            currency = currency,
                                            dueAtEpochMillis = dueAt,
                                            isCleared = check?.isCleared ?: false,
                                            bankName = detail.trim(),
                                            accountNumber = accountNumber.trim(),
                                            description = description.trim(),
                                            createdAtEpochMillis = check?.createdAtEpochMillis ?: now,
                                            updatedAtEpochMillis = now,
                                        ),
                                    )
                                }
                            }.onSuccess {
                                onDismiss()
                            }.onFailure {
                                isSaving = false
                                showSaveError = true
                            }
                        }
                    },
                    enabled = canSave,
                ) {
                    if (isSaving) {
                        Text(stringResource(R.string.finance_saving))
                    } else {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(MarbleSpacing.xs))
                        Text(stringResource(R.string.finance_save))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        SchedulePickerDialog(
            title = stringResource(R.string.finance_due_date),
            mode = SchedulePickerMode.DATE_ONLY,
            initialDate = dueDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            initialTime = null,
            languageTag = settings.languageTag,
            numeralMode = numeralMode,
            weekStartsSaturday = settings.weekStartsSaturday,
            calendarMode = settings.countdownCalendar,
            onCalendarModeChange = onCalendarDisplaySelected,
            onConfirm = { selectedDate, _ ->
                dueDateIso = selectedDate.toString()
                showDatePicker = false
            },
            onClear = {
                dueDateIso = null
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            allowAnyTime = false,
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.finance_delete)) },
            text = { Text(stringResource(R.string.finance_delete_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        scope.launch {
                            runCatching { onDelete() }
                                .onSuccess { onDismiss() }
                                .onFailure { showSaveError = true }
                        }
                    },
                ) { Text(stringResource(R.string.finance_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text(stringResource(R.string.settings_close)) }
            },
        )
    }
}

private fun listKindLabel(kind: FinanceListKind): Int = when (kind) {
    FinanceListKind.ALL -> R.string.finance_filter_all
    FinanceListKind.DEBTS -> R.string.finance_filter_debts
    FinanceListKind.CHECKS -> R.string.finance_filter_checks
}

private fun statusFilterLabel(filter: FinanceStatusFilter): Int = when (filter) {
    FinanceStatusFilter.ALL -> R.string.finance_filter_all_status
    FinanceStatusFilter.OPEN -> R.string.finance_filter_open
    FinanceStatusFilter.OVERDUE -> R.string.finance_filter_overdue
    FinanceStatusFilter.SETTLED -> R.string.finance_filter_settled
}

private fun currencyLabel(currency: String): Int? = when (currency) {
    "IRT" -> R.string.finance_currency_irt
    "USD" -> R.string.finance_currency_usd
    "EUR" -> R.string.finance_currency_eur
    else -> null
}

private fun formatFinanceAmount(amount: BigDecimal, currency: String, languageTag: String, numeralMode: NumeralMode): String {
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag(languageTag)).apply {
        // Banking style: every three digits get a grouping mark, from the left of the number.
        isGroupingUsed = true
        minimumFractionDigits = 0
        maximumFractionDigits = if (currency == "IRT") 0 else 2
    }
    return TextNormalizer.formatDigits(formatter.format(amount), numeralMode)
}

private fun formatFinanceAmount(amount: Double, currency: String, languageTag: String, numeralMode: NumeralMode): String =
    formatFinanceAmount(BigDecimal.valueOf(amount), currency, languageTag, numeralMode)

private fun amountInputText(amount: Double): String = BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()

private fun maskAccountNumber(value: String): String =
    if (value.length <= 4) "••••" else "•••• ${value.takeLast(4)}"

/**
 * The currency's localized name — codes like "IRT" read as English in a Persian ledger.
 *
 * A name is the whole label here: Toman is the app's only Iranian currency (legacy Rial records
 * are folded into Toman by the repository), and the shaded "تومان (IRT)" form repeated the same
 * fact in two scripts next to an amount that was already denominated in Toman. The picker still
 * shows the code for foreign currencies, where USD and EUR genuinely need disambiguating.
 */
@Composable
private fun currencyName(currency: String): String =
    currencyLabel(currency)?.let { stringResource(it) } ?: currency

/**
 * Shows a typed amount the way banking apps do — one grouping mark every three digits of the
 * integer part, counted from the left — while the field keeps the raw text the parser reads,
 * so editing and the caret keep working.
 */
private class FinanceAmountGroupingTransformation(
    private val numeralMode: NumeralMode,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val display = FinanceAmountParser.groupedDisplay(text.text, numeralMode)
        return TransformedText(
            text = AnnotatedString(display.text),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    display.boundary.getOrElse(offset) { display.text.length }

                override fun transformedToOriginal(offset: Int): Int {
                    var low = 0
                    var high = display.boundary.size - 1
                    while (low < high) {
                        val mid = (low + high + 1) ushr 1
                        if (display.boundary[mid] <= offset) low = mid else high = mid - 1
                    }
                    return low
                }
            },
        )
    }
}
