package com.marbledo.feature.calendar

import android.content.Context
import android.icu.util.Calendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

private enum class CalendarViewMode { MONTH, AGENDA, YEAR }

private val PERSIAN_CALENDAR_LOCALE = ULocale("fa_IR@calendar=persian")
private val ISLAMIC_CIVIL_CALENDAR_LOCALE = ULocale("ar@calendar=islamic-civil")

private data class Holiday(
    val month: Int,
    val day: Int,
    val titleFa: String,
    val titleEn: String,
    val category: String,
)

private data class DayCell(
    val epochMillis: Long,
    val date: LocalDate,
    val dayNumber: Int,
    val inDisplayedMonth: Boolean,
    val isToday: Boolean,
    val taskCount: Int,
    val holiday: Holiday?,
)

@Composable
fun CalendarScreen(
    tasks: List<Task>,
    languageTag: String,
    weekStartsSaturday: Boolean,
    lunarOffsetDays: Int,
    enabledEventCategories: Set<String> = setOf("official", "national", "religious", "personal"),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedIsoDate by rememberSaveable { mutableStateOf(LocalDate.now(zone).toString()) }
    var viewMode by rememberSaveable { mutableStateOf(CalendarViewMode.MONTH) }
    val holidays = remember(context) { loadHolidays(context) }
    val selectedDate = remember(selectedIsoDate) { runCatching { LocalDate.parse(selectedIsoDate) }.getOrDefault(LocalDate.now(zone)) }
    val monthData = remember(monthOffset, tasks, holidays, weekStartsSaturday, zone, enabledEventCategories) {
        buildMonth(monthOffset, tasks, holidays, zone, weekStartsSaturday, enabledEventCategories)
    }
    val monthName = persianMonthName(monthData.monthIndex, languageTag)
    val numeralMode = LocalNumeralMode.current
    val dateInfo = remember(selectedDate, lunarOffsetDays, languageTag, numeralMode) {
        tripleCalendarDate(selectedDate, lunarOffsetDays, languageTag, numeralMode)
    }
    val selectedCell = monthData.cells.firstOrNull { it.date == selectedDate }
    val matchingTasks = tasks.filter { task ->
        task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == selectedDate } == true
    }
    val holidayTitle = selectedCell?.holiday?.let { if (languageTag == "fa") it.titleFa else it.titleEn }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.calendar_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(dateInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            IconButton(onClick = { monthOffset -= 1 }) {
                Icon(
                    if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
                    contentDescription = stringResource(R.string.calendar_previous_month),
                )
            }
            IconButton(onClick = { monthOffset += 1 }) {
                Icon(
                    if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_month),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                TextNormalizer.formatDigits(stringResource(R.string.calendar_month_year, monthName, monthData.year), numeralMode),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.calendar_today),
                modifier = Modifier.clip(CircleShape).clickable {
                    monthOffset = 0
                    val today = LocalDate.now(zone)
                    selectedIsoDate = today.toString()
                }.padding(horizontal = 12.dp, vertical = 7.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(CalendarViewMode.MONTH, CalendarViewMode.AGENDA, CalendarViewMode.YEAR).forEach { mode ->
                FilterChip(
                    selected = viewMode == mode,
                    onClick = { viewMode = mode },
                    label = {
                        Text(
                            when (mode) {
                                CalendarViewMode.MONTH -> stringResource(R.string.calendar_view_month)
                                CalendarViewMode.AGENDA -> stringResource(R.string.calendar_view_agenda)
                                CalendarViewMode.YEAR -> stringResource(R.string.calendar_view_year)
                            },
                        )
                    },
                )
            }
        }

        when (viewMode) {
            CalendarViewMode.MONTH -> {
                WeekdayHeader(weekStartsSaturday)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(monthData.cells, key = { it.date.toString() }) { cell ->
                        DayCellView(
                            cell = cell,
                            selected = cell.date == selectedDate,
                            languageTag = languageTag,
                            onClick = { selectedIsoDate = cell.date.toString() },
                        )
                    }
                }
            }
            CalendarViewMode.AGENDA -> {
                val items = monthData.cells.filter { it.inDisplayedMonth && (it.taskCount > 0 || it.holiday != null) }
                if (items.isEmpty()) {
                    EmptyCalendarMessage(Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items.size) { index ->
                            val cell = items[index]
                            AgendaRow(
                                date = cell.date,
                                taskCount = cell.taskCount,
                                holiday = cell.holiday?.let { if (languageTag == "fa") it.titleFa else it.titleEn },
                                languageTag = languageTag,
                                onClick = { selectedIsoDate = cell.date.toString() },
                            )
                        }
                    }
                }
            }
            CalendarViewMode.YEAR -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                ) {
                    items(12) { index ->
                        Card(
                            modifier = Modifier.padding(5.dp).clickable {
                                val currentMonth = monthData.monthIndex
                                monthOffset += index - currentMonth
                                viewMode = CalendarViewMode.MONTH
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                persianMonthName(index, languageTag),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(dateInfo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                if (!holidayTitle.isNullOrBlank()) {
                    Text(holidayTitle, color = MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    TextNormalizer.formatDigits(stringResource(R.string.calendar_tasks_count, matchingTasks.size), numeralMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                matchingTasks.take(3).forEach { task -> Text("• ${task.title}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(weekStartsSaturday: Boolean) {
    val saturday = listOf(
        R.string.calendar_sat, R.string.calendar_sun, R.string.calendar_mon, R.string.calendar_tue,
        R.string.calendar_wed, R.string.calendar_thu, R.string.calendar_fri,
    )
    val sunday = listOf(
        R.string.calendar_sun, R.string.calendar_mon, R.string.calendar_tue, R.string.calendar_wed,
        R.string.calendar_thu, R.string.calendar_fri, R.string.calendar_sat,
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        (if (weekStartsSaturday) saturday else sunday).forEach { label ->
            Text(
                stringResource(label),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun DayCellView(cell: DayCell, selected: Boolean, languageTag: String, onClick: () -> Unit) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        cell.isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val foreground = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        !cell.inDisplayedMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier.padding(2.dp).size(43.dp).clip(CircleShape).background(background).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(TextNormalizer.formatDigits(cell.dayNumber.toString(), LocalNumeralMode.current), color = foreground, style = MaterialTheme.typography.bodySmall)
        if (cell.taskCount > 0 || cell.holiday != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(minOf(cell.taskCount, 2)) {
                    Box(Modifier.size(3.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary))
                }
                if (cell.holiday != null) {
                    Box(Modifier.size(3.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.error))
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(date: LocalDate, taskCount: Int, holiday: String?, languageTag: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(TextNormalizer.formatDigits(date.format(DateTimeFormatter.ofPattern(if (languageTag == "fa") "d MMMM" else "MMM d", Locale.forLanguageTag(languageTag))), LocalNumeralMode.current))
            Column(horizontalAlignment = Alignment.End) {
                holiday?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                if (taskCount > 0) Text(stringResource(R.string.calendar_tasks_count, taskCount), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyCalendarMessage(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.calendar_no_events), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class MonthData(val monthIndex: Int, val year: Int, val cells: List<DayCell>)

private fun buildMonth(
    monthOffset: Int,
    tasks: List<Task>,
    holidays: List<Holiday>,
    zone: ZoneId,
    weekStartsSaturday: Boolean,
    enabledCategories: Set<String>,
): MonthData {
    val current = Calendar.getInstance(IcuTimeZone.getTimeZone(zone.id), PERSIAN_CALENDAR_LOCALE).apply {
        timeInMillis = System.currentTimeMillis()
        add(Calendar.MONTH, monthOffset)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val month = current.get(Calendar.MONTH)
    val year = current.get(Calendar.YEAR)
    val firstDayOfWeek = current.get(Calendar.DAY_OF_WEEK)
    val leadingCells = if (weekStartsSaturday) firstDayOfWeek % 7 else firstDayOfWeek - 1
    val firstCell = (current.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -leadingCells) }
    val today = LocalDate.now(zone)
    val taskCounts = tasks.mapNotNull { task ->
        task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    }.groupingBy { it }.eachCount()
    val eligibleHolidays = holidays.filter { it.category in enabledCategories }
    val cells = (0 until 42).map { offset ->
        val day = (firstCell.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset) }
        val epoch = day.timeInMillis
        val date = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
        val isDisplayed = day.get(Calendar.MONTH) == month && day.get(Calendar.YEAR) == year
        DayCell(
            epochMillis = epoch,
            date = date,
            dayNumber = day.get(Calendar.DAY_OF_MONTH),
            inDisplayedMonth = isDisplayed,
            isToday = date == today,
            taskCount = taskCounts[date] ?: 0,
            holiday = eligibleHolidays.firstOrNull { it.month == day.get(Calendar.MONTH) + 1 && it.day == day.get(Calendar.DAY_OF_MONTH) },
        )
    }
    return MonthData(month, year, cells)
}

private fun loadHolidays(context: Context): List<Holiday> = runCatching {
    val json = context.assets.open("calendar/holidays-fa.json").bufferedReader().use { it.readText() }
    val array = JSONObject(json).getJSONArray("holidays")
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        Holiday(
            month = item.getInt("month"),
            day = item.getInt("day"),
            titleFa = item.getString("titleFa"),
            titleEn = item.getString("titleEn"),
            category = item.getString("category"),
        )
    }
}.getOrDefault(emptyList())

private fun tripleCalendarDate(date: LocalDate, lunarOffset: Int, languageTag: String, numeralMode: NumeralMode): String {
    val zone = ZoneId.systemDefault()
    val epoch = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    val p = Calendar.getInstance(IcuTimeZone.getTimeZone(zone.id), PERSIAN_CALENDAR_LOCALE).apply { timeInMillis = epoch }
    val g = date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT))
    val i = Calendar.getInstance(IcuTimeZone.getTimeZone(zone.id), ISLAMIC_CIVIL_CALENDAR_LOCALE).apply {
        timeInMillis = epoch
        add(Calendar.DAY_OF_MONTH, lunarOffset)
    }
    val monthsFa = listOf("محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی", "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه")
    val monthsEn = listOf("Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah")
    val lunarMonth = (if (languageTag == "fa") monthsFa else monthsEn).getOrElse(i.get(Calendar.MONTH)) { "" }
    val persian = "${p.get(Calendar.DAY_OF_MONTH)} ${persianMonthName(p.get(Calendar.MONTH), languageTag)} ${p.get(Calendar.YEAR)}"
    val lunar = "${i.get(Calendar.DAY_OF_MONTH)} $lunarMonth ${i.get(Calendar.YEAR)}"
    return TextNormalizer.formatDigits("$persian  ·  $g  ·  $lunar", numeralMode)
}

private fun persianMonthName(month: Int, languageTag: String): String {
    val namesFa = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val namesEn = listOf("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar", "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand")
    return (if (languageTag == "fa") namesFa else namesEn).getOrElse(month) { "" }
}
