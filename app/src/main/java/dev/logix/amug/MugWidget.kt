package dev.logix.amug

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class MugWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = MugSnapshotStore(context).read()
        provideContent { MugWidgetContent(snapshot, Intent(context, MainActivity::class.java)) }
    }
}

class MugWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MugWidget()
}

@Composable
private fun MugWidgetContent(snapshot: MugSnapshot, openApp: Intent) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(Color(0xff202124)))
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(snapshot.name ?: "AMUG", style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold))
        Text(snapshot.currentC?.let { "${snapshot.unit.display(it).roundToInt()}${snapshot.unit.symbol}" } ?: "--${snapshot.unit.symbol}", style = TextStyle(color = ColorProvider(Color.White)))
        Text(snapshot.targetC?.let { "Target ${snapshot.unit.display(it).roundToInt()}${snapshot.unit.symbol}" } ?: "Target --", style = TextStyle(color = ColorProvider(Color(0xffc4c7c5))))
        Text(
            when { snapshot.empty -> "Empty"; snapshot.maintenanceEnabled -> "Temperature hold on"; else -> "Hold off" },
            style = TextStyle(color = ColorProvider(Color(0xffc4c7c5))),
        )
        val time = snapshot.updatedAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "No reading"
        Text(
            if (snapshot.connected) "Connected - $time" else "Stale - $time",
            style = TextStyle(color = ColorProvider(if (snapshot.connected) Color(0xff8ab4f8) else Color(0xffc4c7c5))),
        )
        Button("Open AMUG", onClick = actionStartActivity(openApp))
    }
}
