package com.biosecure.app.workers

import android.content.Context
import androidx.work.*
import com.biosecure.app.data.model.Shift
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ShiftNotificationScheduler {

    private const val WORK_NAME = "biosecure_shift_reminder"
    private const val MINUTES_BEFORE = 15

    fun schedule(context: Context, shifts: List<Shift>, shiftName: String?) {
        val target = if (!shiftName.isNullOrEmpty())
            shifts.find { it.name.equals(shiftName, ignoreCase = true) } ?: shifts.firstOrNull()
        else
            shifts.firstOrNull()
        target ?: return

        val delayMs = delayUntilMs(target.checkInStart) ?: return

        val data = workDataOf(
            ShiftReminderWorker.KEY_SHIFT_NAME to target.name,
            ShiftReminderWorker.KEY_CHECK_IN_START to target.checkInStart
        )

        val request = OneTimeWorkRequestBuilder<ShiftReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    // Returns milliseconds until (checkInStart - MINUTES_BEFORE), scheduling for tomorrow if already past.
    private fun delayUntilMs(checkInStart: String): Long? {
        val parts = checkInStart.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) return null

        val now = Calendar.getInstance()
        val fire = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0])
            set(Calendar.MINUTE, parts[1])
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -MINUTES_BEFORE)
        }

        if (!fire.after(now)) fire.add(Calendar.DAY_OF_YEAR, 1)

        val delay = fire.timeInMillis - now.timeInMillis
        return if (delay > 0) delay else null
    }
}
