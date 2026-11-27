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
import com.biosecure.app.data.network.RetrofitInstance
import com.biosecure.app.data.prefs.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget delgado: lee el estado desde la API MongoDB (sin Firebase). */
class BioSecureWidget : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(context, manager, id)
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val session = SessionStore(context)
        if (session.token.isNullOrBlank()) {
            val views = RemoteViews(context.packageName, R.layout.biosecure_widget_employee)
            views.setTextViewText(R.id.widget_status_text, "Inicia sesión en BioSecure")
            views.setViewVisibility(R.id.widget_btn_scan, View.GONE)
            click(context, views)
            manager.updateAppWidget(id, views)
            return
        }

        scope.launch {
            try {
                val api = RetrofitInstance.mongoApi
                val auth = session.authHeader()
                val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                if (session.role == "admin") {
                    val list = withContext(Dispatchers.IO) { api.getAttendances(auth, companyId = session.companyId).data }.orEmpty()
                    val todayList = list.filter { it.date == today }
                    val punctual = todayList.count { it.status == "PUNTUAL" || it.status == "EXITOSO" }
                    val late = todayList.count { it.status == "TARDANZA" }
                    val absent = todayList.count { it.status == "INASISTENCIA" }
                    val views = RemoteViews(context.packageName, R.layout.biosecure_widget_admin)
                    views.setTextViewText(R.id.widget_count_punctual, punctual.toString())
                    views.setTextViewText(R.id.widget_count_late, late.toString())
                    views.setTextViewText(R.id.widget_count_absent, absent.toString())
                    views.setTextViewText(R.id.widget_admin_sync, "Hoy ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")
                    click(context, views)
                    manager.updateAppWidget(id, views)
                } else {
                    val list = withContext(Dispatchers.IO) { api.getAttendances(auth, uid = session.uid).data }.orEmpty()
                    val todayAtt = list.firstOrNull { it.date == today }
                    val companyName = withContext(Dispatchers.IO) {
                        api.getCompany(auth, session.companyId).data?.name
                    } ?: session.companyId

                    val views = RemoteViews(context.packageName, R.layout.biosecure_widget_employee)
                    views.setTextViewText(R.id.widget_employee_name, session.name.split(" ").firstOrNull() ?: session.name)
                    views.setTextViewText(R.id.widget_company_name, companyName.uppercase())
                    views.setTextViewText(R.id.widget_shift_info, "Turno asignado")
                    if (todayAtt != null) {
                        views.setTextViewText(R.id.widget_status_text, "✅ ${todayAtt.status ?: "REGISTRADO"} ${todayAtt.checkIn ?: ""}")
                        views.setViewVisibility(R.id.widget_btn_scan, View.GONE)
                    } else {
                        views.setTextViewText(R.id.widget_status_text, "Pendiente de marcar")
                        views.setViewVisibility(R.id.widget_btn_scan, View.VISIBLE)
                    }
                    views.setTextViewText(R.id.widget_weekly_summary, "${list.size} asistencias")
                    click(context, views)
                    manager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                val views = RemoteViews(context.packageName, R.layout.biosecure_widget_employee)
                views.setTextViewText(R.id.widget_status_text, "Sincronizando...")
                click(context, views)
                manager.updateAppWidget(id, views)
            }
        }
    }

    private fun click(context: Context, views: RemoteViews) {
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        if (views.layoutId == R.layout.biosecure_widget_employee) {
            views.setOnClickPendingIntent(R.id.widget_btn_scan, pi)
        }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        job.cancel()
    }
}
