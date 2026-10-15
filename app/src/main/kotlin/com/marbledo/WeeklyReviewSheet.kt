package com.marble098.marbledo

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.ReviewAdvice
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.domain.util.WeeklyReviewPlanner
import com.marbledo.feature.calendar.PersianDateUtils
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeeklyReviewSheet(
    tasks: List<Task>,
    now: Instant,
    settings: AppSettings,
    onOpenTask: (Task) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val review = remember(tasks, now, zone) { WeeklyReviewPlanner.review(tasks, now, zone) }
    fun digits(text: String) = TextNormalizer.formatDigits(text, settings.numeralMode)
    fun dateLabel(date: java.time.LocalDate) = PersianDateUtils.fullDate(date.atStartOfDay(zone).toInstant().toEpochMilli(), settings.languageTag, settings.numeralMode, zone)
    val title = stringResource(R.string.review_title)
    val range = "${dateLabel(review.recent.start)} – ${dateLabel(review.recent.end)}"
    val summary = digits(stringResource(R.string.review_summary, review.recent.completed, review.recent.scheduled))
    val advice = stringResource(adviceLabel(review.advice))
    val shareText = "$title\n$range\n$summary\n$advice"
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MarbleTextStyles.sectionTitle)
            Text(range, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.review_semantics), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(summary, style = MarbleTextStyles.cardTitle)
                    val percent = review.recent.completionPercent
                    if (percent == null) Text(stringResource(R.string.review_empty), style = MaterialTheme.typography.bodyMedium)
                    else {
                        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text(digits(stringResource(R.string.review_percent, percent)), style = MaterialTheme.typography.labelLarge)
                    }
                    Text(review.changePercentagePoints?.let { digits(context.getString(R.string.review_trend, it)) }
                        ?: stringResource(R.string.review_no_baseline), style = MaterialTheme.typography.bodySmall)
                    Text(digits(stringResource(R.string.review_focus, review.recent.plannedFocusMinutes)), style = MaterialTheme.typography.bodySmall)
                }
            }
            review.days.forEach { day ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dateLabel(day.date), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(digits("${day.completed} / ${day.completed + day.open}"), style = MaterialTheme.typography.labelMedium)
                    }
                    val total = day.completed + day.open
                    LinearProgressIndicator(progress = { if (total == 0) 0f else day.completed.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                }
            }
            Text(digits(stringResource(R.string.review_next, review.nextWeekOpen, review.overdueTasks.size, review.unscheduledTasks.size)),
                style = MaterialTheme.typography.bodyMedium)
            Text(advice, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            val actionTask = review.overdueTasks.firstOrNull() ?: review.unscheduledTasks.firstOrNull()
            if (actionTask != null) Button(onClick = { onOpenTask(actionTask); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_take_action))
            }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText)
                }, null)) }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.review_share)) }
        }
    }
}

private fun adviceLabel(advice: ReviewAdvice): Int = when (advice) {
    ReviewAdvice.START_PLANNING -> R.string.review_advice_start
    ReviewAdvice.CLEAR_BACKLOG -> R.string.review_advice_backlog
    ReviewAdvice.SCHEDULE_UNDATED -> R.string.review_advice_undated
    ReviewAdvice.REDUCE_LOAD -> R.string.review_advice_reduce
    ReviewAdvice.KEEP_RHYTHM -> R.string.review_advice_keep
}
