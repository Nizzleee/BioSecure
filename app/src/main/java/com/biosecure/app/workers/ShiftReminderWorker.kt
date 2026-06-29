package com.biosecure.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biosecure.app.BioSecureMessagingService
import com.biosecure.app.MainActivity

class ShiftReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val shiftName = inputData.getString(KEY_SHIFT_NAME) ?: "tu turno"
        val checkInStart = inputData.getString(KEY_CHECK_IN_START) ?: ""
        postNotification(shiftName, checkInStart)
        return Result.success()
    }

    private fun postNotification(shiftName: String, checkInStart: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannel(
            BioSecureMessagingService.CHANNEL_ID,
            "Asistencia BioSecure",
            NotificationManager.IMPORTANCE_HIGH
        ).also { nm.createNotificationChannel(it) }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = if (checkInStart.isNotEmpty())
            "Tu turno '$shiftName' comienza a las $checkInStart. ¡Es hora de prepararte!"
        else
            "Tu turno '$shiftName' comienza pronto. ¡Prepárate!"

        val notification = NotificationCompat.Builder(applicationContext, BioSecureMessagingService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ Tu turno comienza pronto")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_SHIFT_NAME = "shiftName"
        const val KEY_CHECK_IN_START = "checkInStart"
        const val NOTIFICATION_ID = 1001
    }
}
