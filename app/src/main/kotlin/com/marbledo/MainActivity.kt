package com.marble098.marbledo

import com.marble098.marbledo.notifications.PersistentCalendarNotificationWorker
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : AppCompatActivity() {
    private var shareText by mutableStateOf<String?>(null)
    private var openAdd by mutableStateOf(false)
    private var destination by mutableStateOf<String?>(null)
    private var taskId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) acceptIntent(intent)
        setContent {
            MarbleDoApp(
                initialShareText = shareText,
                initialOpenAdd = openAdd,
                initialDestination = destination,
                initialTaskId = taskId,
                onIntentConsumed = {
                    shareText = null
                    openAdd = false
                    destination = null
                    taskId = null
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back to the app is a good moment to correct a stale day number or a changed setting.
        PersistentCalendarNotificationWorker.refreshNow(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    private fun acceptIntent(intent: Intent?) {
        if (intent == null) return
        shareText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        openAdd = intent.getBooleanExtra(EXTRA_OPEN_ADD, false)
        destination = intent.getStringExtra(EXTRA_DESTINATION)
        // Two ways in, both standard: the explicit extra the Quick Settings tile uses, and the
        // `marbledo://task/{id}` URI every notification action sends. Reading the URI here is what
        // makes a notification tap open *that task's* editor instead of the home screen.
        taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L).takeIf { it > 0 }
            ?: intent.data?.takeIf { it.scheme == "marbledo" && it.host == "task" }?.lastPathSegment?.toLongOrNull()?.takeIf { it > 0 }
    }

    companion object {
        const val EXTRA_OPEN_ADD = "com.marble098.marbledo.extra.OPEN_ADD"
        const val EXTRA_DESTINATION = "com.marble098.marbledo.extra.DESTINATION"
        const val EXTRA_TASK_ID = "com.marble098.marbledo.extra.TASK_ID"
    }
}
