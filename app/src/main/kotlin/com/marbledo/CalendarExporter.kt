package com.marble098.marbledo

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.marbledo.feature.calendar.CalendarExportRequest
import com.marbledo.feature.calendar.CalendarIcs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class CalendarExporter(val busy: Boolean, val export: (CalendarExportRequest) -> Unit)

// Old and recreated Activities must not truncate the same destination at the same time.
private val calendarExportMutex = Mutex()

@Composable
internal fun rememberCalendarExporter(): CalendarExporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context.cacheDir) { CalendarExportStore(context.cacheDir) }
    var preparing by remember { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        pickerOpen = false
        if (uri != null) destination = uri.toString() else scope.launch {
            calendarExportMutex.withLock { withContext(Dispatchers.IO) { store.discard() } }
        }
    }

    // Keep the URI and snapshot until the copy finishes. Cancellation on rotation is not an
    // export failure: the recreated Activity can retry the same immutable snapshot with "wt".
    LaunchedEffect(destination) {
        val target = destination ?: return@LaunchedEffect
        calendarExportMutex.withLock {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(Uri.parse(target), "wt")?.use(store::copyTo)
                        ?: error("Could not open calendar destination")
                }
                Toast.makeText(context, R.string.calendar_export_saved, Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, R.string.calendar_export_failed, Toast.LENGTH_LONG).show()
            }
            withContext(Dispatchers.IO) { store.discard() }
            destination = null
        }
    }

    val busy = preparing || pickerOpen || destination != null
    return CalendarExporter(busy) { request ->
        if (!preparing && !pickerOpen && destination == null) {
            preparing = true
            scope.launch {
                try {
                    val content = withContext(Dispatchers.Default) { CalendarIcs.encode(request.entries) }
                    calendarExportMutex.withLock { withContext(Dispatchers.IO) { store.stage(content) } }
                    pickerOpen = true
                    launcher.launch(request.filename)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    pickerOpen = false
                    calendarExportMutex.withLock { withContext(Dispatchers.IO) { store.discard() } }
                    Toast.makeText(context, R.string.calendar_export_failed, Toast.LENGTH_LONG).show()
                } finally {
                    preparing = false
                }
            }
        }
    }
}
