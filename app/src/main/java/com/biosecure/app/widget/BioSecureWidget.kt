package com.biosecure.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.biosecure.app.MainActivity
import com.biosecure.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class BioSecureWidget : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            val views = RemoteViews(context.packageName, R.layout.biosecure_widget_employee)
            views.setTextViewText(R.id.widget_status_text, "Sesión no iniciada")
            views.setViewVisibility(R.id.widget_btn_scan, View.GONE)
            setOnClickPendingIntent(context, views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        scope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                // Usamos Source.DEFAULT para que si no hay internet use la caché y no de error
                val doc = db.collection("users").document(user.uid).get().await()
                val role = doc.getString("role") ?: "employee"
                val name = doc.getString("name") ?: "Usuario"
                val companyId = doc.getString("companyId") ?: ""

                if (role == "admin") {
                    updateAdminWidget(context, appWidgetManager, appWidgetId, companyId)
                } else {
                    val shiftId = doc.getString("shiftId") ?: ""
                    updateEmployeeWidget(context, appWidgetManager, appWidgetId, user.uid, name, companyId, shiftId)
                }
            } catch (e: Exception) {
                val views = RemoteViews(context.packageName, R.layout.biosecure_widget_employee)
                views.setTextViewText(R.id.widget_status_text, "Sincronizando...")
                setOnClickPendingIntent(context, views)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private suspend fun updateAdminWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, companyId: String) {
        val views = RemoteViews(context.packageName, R.layout.biosecure_widget_admin)
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        try {
            val db = FirebaseFirestore.getInstance()
            val attendances = db.collection("attendances")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("date", today)
                .get().await()

            val punctual = attendances.documents.count { it.getString("status") == "PUNTUAL" || it.getString("status") == "EXITOSO" }
            val late = attendances.documents.count { it.getString("status") == "TARDANZA" }
            val absent = attendances.documents.count { it.getString("status") == "INASISTENCIA" }

            views.setTextViewText(R.id.widget_count_punctual, punctual.toString())
            views.setTextViewText(R.id.widget_count_late, late.toString())
            views.setTextViewText(R.id.widget_count_absent, absent.toString())
            views.setTextViewText(R.id.widget_admin_sync, "Hoy $time")
        } catch (e: Exception) {
            views.setTextViewText(R.id.widget_admin_sync, "!")
        }

        setOnClickPendingIntent(context, views)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private suspend fun updateEmployeeWidget(
        context: Context, 
        appWidgetManager: AppWidgetManager, 
        appWidgetId: Int, 
        uid: String, 
        name: String,
        companyId: String,
        shiftId: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.biosecure_widget_employee)
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        
        views.setTextViewText(R.id.widget_employee_name, name.split(" ").firstOrNull() ?: name)

        try {
            val db = FirebaseFirestore.getInstance()

            // 1. Nombre de Empresa (Caché ok)
            if (companyId.isNotEmpty()) {
                try {
                    val companyDoc = db.collection("companies").document(companyId).get().await()
                    val companyName = companyDoc.getString("name") ?: companyId
                    views.setTextViewText(R.id.widget_company_name, companyName.uppercase())
                } catch (_: Exception) {}
            }

            // 2. Información de Turno
            if (shiftId.isNotEmpty()) {
                try {
                    val shiftDoc = db.collection("companies").document(companyId).collection("shifts").document(shiftId).get().await()
                    val sName = shiftDoc.getString("name") ?: "Turno"
                    val sStart = shiftDoc.getString("startTime") ?: "--:--"
                    views.setTextViewText(R.id.widget_shift_info, "$sName: $sStart")
                } catch (_: Exception) {
                    views.setTextViewText(R.id.widget_shift_info, "Turno asignado")
                }
            } else {
                views.setTextViewText(R.id.widget_shift_info, "Sin turno fijo")
            }

            // 3. Asistencia de Hoy (Doc ID directo es rápido)
            val docId = "${uid}_${today.replace("/", "-")}"
            try {
                val doc = db.collection("attendances").document(docId).get().await()
                if (doc.exists()) {
                    val status = doc.getString("status") ?: "REGISTRADO"
                    val time = doc.getString("checkIn") ?: ""
                    views.setTextViewText(R.id.widget_status_text, "✅ $status $time")
                    views.setViewVisibility(R.id.widget_btn_scan, View.GONE)
                } else {
                    views.setTextViewText(R.id.widget_status_text, "Pendiente de marcar")
                    views.setViewVisibility(R.id.widget_btn_scan, View.VISIBLE)
                }
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_status_text, "Estado actual")
            }

            // 4. Resumen Semanal (Simplificado para evitar errores de índice)
            try {
                val weekly = db.collection("attendances")
                    .whereEqualTo("userId", uid)
                    .limit(10)
                    .get().await()
                val count = weekly.size()
                views.setTextViewText(R.id.widget_weekly_summary, "$count asistencias")
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_weekly_summary, "-- asistencias")
            }

        } catch (e: Exception) {
            // Error general
        }

        setOnClickPendingIntent(context, views)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun setOnClickPendingIntent(context: Context, views: RemoteViews) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        
        if (views.layoutId == R.layout.biosecure_widget_employee) {
            views.setOnClickPendingIntent(R.id.widget_btn_scan, pendingIntent)
        }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        job.cancel()
    }
}
