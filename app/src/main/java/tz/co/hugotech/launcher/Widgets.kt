package tz.co.hugotech.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class WidgetHost(context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, 2048)

    fun start() {
        try {
            host.startListening()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun stop() {
        try {
            host.stopListening()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun providers(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders
    } catch (e: Exception) {
        emptyList()
    }
}

/** Android widgets the user added (weather, clock, notes…), with an add button. */
@Composable
fun WidgetsSection() {
    val s = LocalLauncher.current
    val ctx = LocalContext.current
    val pal = LocalPal.current
    val wh = s.widgetHost
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var pendingId by remember { mutableIntStateOf(-1) }

    fun add(id: Int) {
        s.widgets.value = s.widgets.value + id.toString()
        pendingId = -1
    }

    fun cancel(id: Int) {
        try {
            wh.host.deleteAppWidgetId(id)
        } catch (e: Exception) {
            // ignore
        }
        pendingId = -1
    }

    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingId
        if (id >= 0) {
            if (r.resultCode == Activity.RESULT_OK) add(id) else cancel(id)
        }
    }

    fun configureOrAdd(id: Int) {
        val info = wh.manager.getAppWidgetInfo(id)
        val conf = info?.configure
        if (conf == null) {
            add(id)
            return
        }
        try {
            configure.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(conf)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            )
        } catch (e: Exception) {
            add(id)
        }
    }

    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingId
        if (id >= 0) {
            if (r.resultCode == Activity.RESULT_OK) configureOrAdd(id) else cancel(id)
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(s.tr("Widgets", "Widgets"), Modifier.weight(1f))
            if (s.widgets.value.isNotEmpty()) {
                TextLink(if (editing) s.tr("Done", "Imekamilika") else s.tr("Edit", "Hariri")) { editing = !editing }
                Spacer(Modifier.width(14.dp))
            }
            TextLink(s.tr("Add", "Ongeza")) { picking = true }
        }
        if (s.widgets.value.isEmpty()) {
            Text(
                s.tr("Add weather, notes, calendar or any app widget.", "Ongeza hali ya hewa, notes, kalenda au widget ya app yoyote."),
                color = pal.muted, fontSize = 13.sp
            )
        }
        val density = LocalDensity.current
        s.widgets.value.forEach { idStr ->
            val id = idStr.toIntOrNull() ?: -1
            val info = if (id >= 0) wh.manager.getAppWidgetInfo(id) else null
            if (info != null) {
            val h = with(density) { info.minHeight.toDp() }.coerceIn(80.dp, 320.dp)
            Column {
                AndroidView(
                    factory = { c -> wh.host.createView(c, id, info).apply { setAppWidget(id, info) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(h)
                        .clip(RoundedCornerShape(18.dp))
                )
                if (editing) {
                    TextLink(s.tr("Remove", "Ondoa"), Modifier.padding(top = 6.dp)) {
                        s.widgets.value = s.widgets.value - idStr
                        try {
                            wh.host.deleteAppWidgetId(id)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }
            }
        }
    }

    if (picking) {
        val pm = ctx.packageManager
        val list = remember { wh.providers().sortedBy { it.loadLabel(pm).lowercase() } }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(s.tr("Add widget", "Ongeza widget")) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    list.forEach { info ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picking = false
                                    val id = wh.host.allocateAppWidgetId()
                                    pendingId = id
                                    if (wh.manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
                                        configureOrAdd(id)
                                    } else {
                                        bind.launch(
                                            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                                        )
                                    }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Column {
                                Text(info.loadLabel(pm), fontSize = 15.sp)
                                Text(info.provider.packageName, fontSize = 11.sp, color = pal.muted)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(s.tr("Close", "Funga")) } }
        )
    }
}
