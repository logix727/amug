package dev.logix.amug

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    DRINKS("Drinks", Icons.Rounded.Coffee),
    LIGHTING("Lighting", Icons.Rounded.Palette),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmugApp(
    state: BleState,
    preferences: UserPreferences,
    mugs: List<MugEntity>,
    selectedMug: MugEntity?,
    presets: List<PresetEntity>,
    sessions: List<MugSessionEntity>,
    historyRetentionDays: Int,
    suggestion: TemperatureSuggestion?,
    scan: () -> Unit,
    connect: (MugDevice) -> Unit,
    setTemperature: (Double) -> Unit,
    applyPreset: (PresetEntity) -> Unit,
    applySuggestion: (TemperatureSuggestion) -> Unit,
    saveSuggestion: (TemperatureSuggestion) -> Unit,
    resetLearning: () -> Unit,
    setHeating: (Boolean) -> Unit,
    setGear: (Int) -> Unit,
    setUnit: (TemperatureUnit) -> Unit,
    setTemperatureLed: (Boolean) -> Unit,
    setLedColor: (Int, Int) -> Unit,
    resetLedPalette: () -> Unit,
    setSafetyWait: (Int) -> Unit,
    setMusicMode: (Int?) -> Unit,
    setHoldLight: (Boolean) -> Unit,
    setChargeLight: (Boolean) -> Unit,
    setSleepTimer: (Int?) -> Unit,
    refresh: () -> Unit,
    selectMug: (Long) -> Unit,
    renameMug: (Long, String) -> Unit,
    forgetMug: (Long) -> Unit,
    clearHistory: () -> Unit,
    setHistoryRetention: (Int) -> Unit,
) {
    var showMugs by remember { mutableStateOf(false) }
    AnimatedContent(state.stage == ConnectionStage.READY, label = "connection screen") { connected ->
        if (!connected) {
            DiscoveryScreen(state, scan, connect) { showMugs = true }
        } else {
            ConnectedScreen(
                state, preferences, presets, suggestion, setTemperature, applyPreset, applySuggestion, saveSuggestion, resetLearning, setHeating, setGear, setUnit,
                setTemperatureLed, setLedColor, resetLedPalette, setSafetyWait, setMusicMode,
                setHoldLight, setChargeLight, setSleepTimer, refresh,
            ) { showMugs = true }
        }
    }
    if (showMugs) {
        MugManagerDialog(
            mugs, selectedMug?.id, sessions, historyRetentionDays, selectMug, renameMug,
            forgetMug, clearHistory, setHistoryRetention,
        ) { showMugs = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedScreen(
    state: BleState,
    preferences: UserPreferences,
    presets: List<PresetEntity>,
    suggestion: TemperatureSuggestion?,
    setTemperature: (Double) -> Unit,
    applyPreset: (PresetEntity) -> Unit,
    applySuggestion: (TemperatureSuggestion) -> Unit,
    saveSuggestion: (TemperatureSuggestion) -> Unit,
    resetLearning: () -> Unit,
    setHeating: (Boolean) -> Unit,
    setGear: (Int) -> Unit,
    setUnit: (TemperatureUnit) -> Unit,
    setTemperatureLed: (Boolean) -> Unit,
    setLedColor: (Int, Int) -> Unit,
    resetLedPalette: () -> Unit,
    setSafetyWait: (Int) -> Unit,
    setMusicMode: (Int?) -> Unit,
    setHoldLight: (Boolean) -> Unit,
    setChargeLight: (Boolean) -> Unit,
    setSleepTimer: (Int?) -> Unit,
    refresh: () -> Unit,
    manageMugs: () -> Unit,
) {
    var destination by remember { mutableStateOf(Destination.HOME) }
    var showTechnical by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (showTechnical) "Technical" else if (destination == Destination.HOME) state.connectedName ?: "AMUG" else destination.label, fontWeight = FontWeight.Bold)
                            if (!showTechnical && destination == Destination.HOME) Text(connectionLabel(state.stage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        if (showTechnical) IconButton(onClick = { showTechnical = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    actions = {
                        IconButton(onClick = refresh) { Icon(Icons.Rounded.Refresh, "Refresh mug status") }
                    },
                )
            },
            bottomBar = {
                if (!wide && !showTechnical) NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide && !showTechnical) NavigationRail(Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(12.dp))
                    Destination.entries.forEach { item ->
                        NavigationRailItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (showTechnical) TechnicalDestination(state) else when (destination) {
                        Destination.HOME -> HomeDestination(state, preferences.unit, suggestion, setTemperature, applySuggestion, saveSuggestion, setHeating, setGear)
                        Destination.DRINKS -> DrinksDestination(state, preferences.unit, presets, applyPreset)
                        Destination.LIGHTING -> LightingDestination(state, preferences, setTemperatureLed, setLedColor, resetLedPalette, setMusicMode, setHoldLight, setChargeLight)
                        Destination.SETTINGS -> SettingsDestination(state, preferences.unit, setUnit, setSafetyWait, manageMugs, resetLearning) { showTechnical = true }
                    }
                }
            }
        }
    }
}

private fun connectionLabel(stage: ConnectionStage) = when (stage) {
    ConnectionStage.READY -> "Connected"
    ConnectionStage.RECONNECTING -> "Reconnecting"
    ConnectionStage.INITIALIZING -> "Reading mug"
    else -> stage.name.lowercase().replaceFirstChar(Char::uppercaseChar)
}

@Composable
private fun DestinationList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun HomeDestination(
    state: BleState,
    unit: TemperatureUnit,
    suggestion: TemperatureSuggestion?,
    setTemperature: (Double) -> Unit,
    applySuggestion: (TemperatureSuggestion) -> Unit,
    saveSuggestion: (TemperatureSuggestion) -> Unit,
    setHeating: (Boolean) -> Unit,
    setGear: (Int) -> Unit,
) {
    val status = state.status
    val insight = remember(state.telemetry) { ThermalIntelligence.analyze(state.telemetry) }
    var target by remember(status?.targetC, unit) { mutableDoubleStateOf(unit.display(status?.targetC ?: 57.0)) }
    var showTemperatureEntry by remember { mutableStateOf(false) }
    DestinationList {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Current temperature", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(status?.let { "${unit.display(it.currentC).roundToInt()}°" } ?: "--°", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    val delta = status?.let { unit.display(it.currentC) - unit.display(it.targetC) }
                    Text(
                        status?.let { "Target ${unit.display(it.targetC).roundToInt()}${unit.symbol} · ${formatDelta(delta!!, unit)}" } ?: "Waiting for live status",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(if (status?.maintenanceEnabled == true) "  Hold on" else "  Hold off", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (status?.charging == true) Icon(Icons.Rounded.BatteryChargingFull, "Charging", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(status?.batteryPercent?.let { " $it%" } ?: "Battery --", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .25f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Temperature hold", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                            Text("Maintain ${status?.let { unit.display(it.targetC).roundToInt() } ?: "--"}${unit.symbol}", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
                        }
                        Switch(checked = status?.maintenanceEnabled == true, enabled = status != null && !status.empty && !status.nightLightEnabled, onCheckedChange = setHeating, modifier = Modifier.semantics { contentDescription = "Temperature hold" })
                    }
                }
            }
        }
        if (status?.empty == true) item { WarningCard("Mug empty", "Temperature hold stopped. Add liquid before turning hold on.") }
        if (status?.nightLightEnabled == true) item { WarningCard("Lighting is active", "Ambient lighting and temperature hold cannot run together.") }
        insight?.let { smart ->
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp)) {
                    ListItem(
                        headlineContent = { Text(smart.explanation, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("${smart.trend} · ${smart.confidence.name.lowercase().replaceFirstChar(Char::uppercaseChar)} confidence · calculated on this device") },
                        leadingContent = { Icon(Icons.Rounded.Info, null) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        suggestion?.let { learned ->
            item {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        val celsius = learned.targetCentiC / 100.0
                        val display = unit.display(celsius).roundToInt()
                        Text("A temperature you often choose", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("$display${unit.symbol} ${learned.context} · ${learned.uses} uses across ${learned.distinctDays} days", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("Learned locally from confirmed choices. AMUG never applies it automatically.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { applySuggestion(learned) }, modifier = Modifier.weight(1f)) { Text("Apply") }
                            OutlinedButton(onClick = { saveSuggestion(learned) }, modifier = Modifier.weight(1f)) { Text("Save preset") }
                        }
                    }
                }
            }
        }
        if ((unit == TemperatureUnit.FAHRENHEIT && target >= 140) || (unit == TemperatureUnit.CELSIUS && target >= 60)) {
            item { WarningCard("Very hot", "Sip carefully and keep the mug away from children.") }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Target temperature", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Adjust in exact 1${unit.symbol} steps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showTemperatureEntry = true }) { Text("${target.roundToInt()}${unit.symbol}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    }
                    if (state.profile == MugProfile.S6_PLUS) {
                        val range = if (unit == TemperatureUnit.FAHRENHEIT) 120f..150f else 48f..66f
                        Slider(
                            value = target.toFloat().coerceIn(range),
                            onValueChange = { target = it.toDouble() },
                            onValueChangeFinished = { setTemperature(unit.toCelsius(target)) },
                            valueRange = range,
                            steps = if (unit == TemperatureUnit.FAHRENHEIT) 29 else 17,
                            modifier = Modifier.semantics { contentDescription = "Target temperature ${target.roundToInt()} ${unit.symbol}" },
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { target = (target - 1).coerceAtLeast(range.start.toDouble()); setTemperature(unit.toCelsius(target)) },
                                modifier = Modifier.size(48.dp),
                            ) { Icon(Icons.Rounded.Remove, "Decrease target by one degree") }
                            Text("${range.start.roundToInt()}° to ${range.endInclusive.roundToInt()}°", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(
                                onClick = { target = (target + 1).coerceAtMost(range.endInclusive.toDouble()); setTemperature(unit.toCelsius(target)) },
                                modifier = Modifier.size(48.dp),
                            ) { Icon(Icons.Rounded.Add, "Increase target by one degree") }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Low", "Medium", "High").forEachIndexed { index, label ->
                                Button(onClick = { setGear(index + 1) }, modifier = Modifier.weight(1f).height(48.dp)) { Text(label) }
                            }
                        }
                    }
                    Text(
                        state.commandMessage ?: "Commands are confirmed by mug readback",
                        color = when (state.commandState) {
                            CommandState.FAILED -> MaterialTheme.colorScheme.error
                            CommandState.CONFIRMED -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
    if (showTemperatureEntry) TemperatureEntryDialog(target, unit, { showTemperatureEntry = false }) { value ->
        target = value
        setTemperature(unit.toCelsius(value))
        showTemperatureEntry = false
    }
}

private fun formatDelta(delta: Double, unit: TemperatureUnit): String = when {
    abs(delta) < 0.5 -> "at target"
    delta < 0 -> "${abs(delta).roundToInt()}${unit.symbol} below"
    else -> "${delta.roundToInt()}${unit.symbol} above"
}

@Composable
private fun WarningCard(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

private val drinkCategories = linkedMapOf(
    "Coffee" to listOf("Coffee", "Espresso", "Latte"),
    "Tea" to listOf("Green tea", "White tea", "Oolong", "Black tea", "Herbal"),
    "Other" to listOf("Cocoa", "Hot"),
)

private val drinkDescriptions = mapOf(
    "Coffee" to "Balanced warmth for everyday coffee.",
    "Espresso" to "Keeps a short coffee warm without masking flavor.",
    "Latte" to "Comfortable warmth for milk-based coffee.",
    "Green tea" to "Gentle holding temperature for delicate tea.",
    "White tea" to "Soft warmth for subtle aromas.",
    "Oolong" to "Warm enough to open layered flavors.",
    "Black tea" to "Full warmth for robust tea.",
    "Herbal" to "A cozy temperature for herbal infusions.",
    "Cocoa" to "Comfortably warm for cocoa and chocolate drinks.",
    "Hot" to "The hottest supported holding temperature.",
)

private val approvedFahrenheit = mapOf(
    "Green tea" to 125, "White tea" to 125,
    "Oolong" to 130, "Cocoa" to 130,
    "Coffee" to 135, "Espresso" to 135, "Latte" to 135,
    "Black tea" to 135, "Herbal" to 135,
    "Hot" to 140,
)

@Composable
private fun DrinksDestination(state: BleState, unit: TemperatureUnit, presets: List<PresetEntity>, applyPreset: (PresetEntity) -> Unit) {
    var selectedCategory by remember { mutableStateOf("Coffee") }
    DestinationList {
        item {
            Text("Choose a holding temperature", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("These are comfortable holding and drinking temperatures, not brewing temperatures.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            PrimaryTabRow(selectedTabIndex = drinkCategories.keys.indexOf(selectedCategory)) {
                drinkCategories.keys.forEach { category ->
                    Tab(selected = selectedCategory == category, onClick = { selectedCategory = category }, text = { Text(category) })
                }
            }
        }
        drinkCategories.getValue(selectedCategory).forEach { name ->
            val preset = presets.firstOrNull { it.name == name }
            if (preset != null) item(key = preset.id) { DrinkPresetCard(preset, state.status?.targetC, unit, applyPreset) }
        }
    }
}

@Composable
private fun DrinkPresetCard(preset: PresetEntity, currentTargetC: Double?, unit: TemperatureUnit, applyPreset: (PresetEntity) -> Unit) {
    val celsius = preset.temperatureCentiC / 100.0
    val fahrenheit = approvedFahrenheit[preset.name] ?: (celsius * 9 / 5 + 32).roundToInt()
    val selected = currentTargetC != null && abs(currentTargetC - celsius) < .05
    Surface(shape = RoundedCornerShape(20.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        ListItem(
            headlineContent = { Text(preset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Column {
                    Text(if (unit == TemperatureUnit.FAHRENHEIT) "$fahrenheit°F · ${celsius.roundToInt()}°C" else "${celsius.roundToInt()}°C · $fahrenheit°F", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(drinkDescriptions[preset.name].orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected) Text("Matches current target", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            leadingContent = { Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Coffee, null, tint = MaterialTheme.colorScheme.onTertiaryContainer) } } },
            trailingContent = { FilledTonalButton(onClick = { applyPreset(preset) }, enabled = !selected, modifier = Modifier.height(48.dp)) { if (selected) Icon(Icons.Rounded.Check, "Matches target") else Text("Apply") } },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun LightingDestination(
    state: BleState,
    preferences: UserPreferences,
    setTemperatureLed: (Boolean) -> Unit,
    setLedColor: (Int, Int) -> Unit,
    resetLedPalette: () -> Unit,
    setMusicMode: (Int?) -> Unit,
    setHoldLight: (Boolean) -> Unit,
    setChargeLight: (Boolean) -> Unit,
) {
    val status = state.status
    val unit = preferences.unit
    var editingLedStop by remember { mutableStateOf<Int?>(null) }
    DestinationList {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(20.dp)) {
                Text("Ambient temperature color and temperature hold are mutually exclusive. Music effects require hold to be on. Hold and charge indicators can be used independently.", color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(18.dp))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ambient temperature palette", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("The mug glows with the live temperature", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = preferences.temperatureLed, onCheckedChange = setTemperatureLed, modifier = Modifier.semantics { contentDescription = "Ambient temperature palette" })
                    }
                    val currentColor = status?.let { MugProtocol.temperatureColor(it.currentC, preferences.ledPalette) }
                    if (status != null && currentColor != null) {
                        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            ColorSwatch(currentColor, "Current ambient color")
                            Text("${unit.display(status.currentC).roundToInt()}${unit.symbol} · #${"%06X".format(currentColor)}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 18.dp).height(18.dp).clip(CircleShape).background(Brush.horizontalGradient(preferences.ledPalette.map { Color(0xff000000.toInt() or it.color) })))
                    Text("Select a color stop to customize it", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                    preferences.ledPalette.forEachIndexed { index, stop ->
                        val label = ledLabels[index]
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { editingLedStop = index }.padding(vertical = 10.dp).semantics { contentDescription = "$label color, ${unit.display(stop.celsius).roundToInt()}${unit.symbol}, #${"%06X".format(stop.color)}" },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorSwatch(stop.color, null)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(label, fontWeight = FontWeight.Bold)
                                Text("${unit.display(stop.celsius).roundToInt()}${unit.symbol} anchor", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("#${"%06X".format(stop.color)}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(onClick = resetLedPalette, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Reset default colors") }
                }
            }
        }
        if (state.profile == MugProfile.S6_PLUS) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Music effects", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Available while temperature hold is on", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (0..5).chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { mode ->
                                FilterChip(selected = status?.lightMode == mode, onClick = { setMusicMode(mode) }, label = { Text("${mode + 1}") }, modifier = Modifier.weight(1f).height(48.dp), enabled = status?.maintenanceEnabled == true, leadingIcon = if (status?.lightMode == mode) {{ Icon(Icons.Rounded.Check, null) }} else null)
                            }
                        }
                    }
                    FilterChip(selected = status?.lightMode in listOf(0x06, 0x15, 0x16), onClick = { setMusicMode(null) }, label = { Text("Off") }, enabled = status != null, modifier = Modifier.height(48.dp))
                }
            }
        }
        if (state.profile == MugProfile.S6_PLUS) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
                Column {
                    LightSwitchItem("Hold light", "Indicate active temperature hold", status?.holdLightMode == 1, status != null, setHoldLight)
                    HorizontalDivider()
                    LightSwitchItem("Charging light", "Indicate charging on the coaster", status?.chargeLightMode == 1, status != null, setChargeLight)
                }
            }
        }
    }
    editingLedStop?.let { index ->
        LedColorEditor(preferences.ledPalette[index], ledLabels[index], unit, { editingLedStop = null }) { color ->
            setLedColor(index, color)
            editingLedStop = null
        }
    }
}

private val ledLabels = listOf("Cool", "Lukewarm", "Warm", "Ready", "Hot", "Very hot")

@Composable
private fun ColorSwatch(color: Int, description: String?) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xff000000.toInt() or color)).semantics { if (description != null) contentDescription = description })
}

@Composable
private fun LightSwitchItem(label: String, supporting: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(supporting) },
        trailingContent = { Switch(checked, change, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDestination(
    state: BleState,
    unit: TemperatureUnit,
    setUnit: (TemperatureUnit) -> Unit,
    setSafetyWait: (Int) -> Unit,
    manageMugs: () -> Unit,
    resetLearning: () -> Unit,
    openTechnical: () -> Unit,
) {
    DestinationList {
        item { Text("Display", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
        item {
            Column {
                ListItem(headlineContent = { Text("Temperature units") }, supportingContent = { Text("Used throughout AMUG") }, colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    TemperatureUnit.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = unit == option,
                            onClick = { setUnit(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, TemperatureUnit.entries.size),
                            label = { Text(if (option == TemperatureUnit.FAHRENHEIT) "Fahrenheit °F" else "Celsius °C") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item { HorizontalDivider() }
        item { Text("Safety", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
        if (state.profile == MugProfile.S6_PLUS) item {
            Column {
                ListItem(headlineContent = { Text("Firmware auto-off") }, supportingContent = { Text("Stored by mug firmware · physically validated") }, colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4).forEach { hours ->
                        val selected = state.status?.safetyWaitHours == hours
                        if (selected) Button(onClick = { setSafetyWait(hours) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("$hours hours") }
                        else OutlinedButton(onClick = { setSafetyWait(hours) }, modifier = Modifier.weight(1f).height(48.dp), enabled = state.status != null) { Text("$hours hours") }
                    }
                }
            }
        }
        item { HorizontalDivider() }
        item { Text("Device & data", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
        item {
            Surface(onClick = manageMugs, color = Color.Transparent) {
                ListItem(
                    headlineContent = { Text("Mug manager", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Rename, select, or forget mugs and manage local history") },
                    leadingContent = { Icon(Icons.Rounded.Devices, null) },
                    trailingContent = { Text("Open", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        item {
            Surface(onClick = openTechnical, color = Color.Transparent) {
                ListItem(headlineContent = { Text("Technical & diagnostics", fontWeight = FontWeight.Bold) }, supportingContent = { Text("Protocol, firmware, validation and BLE event log") }, leadingContent = { Icon(Icons.Rounded.Info, null) }, trailingContent = { Text("Open", color = MaterialTheme.colorScheme.primary) }, colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
        item {
            ListItem(headlineContent = { Text("Reset learned suggestions") }, supportingContent = { Text("Clear locally learned temperature choices for this mug") }, trailingContent = { TextButton(onClick = resetLearning) { Text("Reset") } }, colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent))
        }
    }
}

@Composable
private fun SettingsCard(title: String, supporting: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun TechnicalDestination(state: BleState) {
    val profile = state.profile
    DestinationList {
        item { TechnicalCard("Connection") {
            TechnicalRow("Device", state.connectedName ?: "Not connected")
            TechnicalRow("Profile", profile?.name ?: "Unknown")
            TechnicalRow("State", state.stage.name)
            TechnicalRow("Firmware", state.version?.firmware ?: "Unknown")
            TechnicalRow("Hardware", state.version?.hardware ?: "Not reported")
        } }
        item { TechnicalCard("GATT UUIDs") {
            TechnicalRow("Service", profile?.service?.toString() ?: "Unknown")
            TechnicalRow("Write", profile?.write?.toString() ?: "Unknown")
            TechnicalRow("Notify", profile?.notify?.toString() ?: "Unknown")
            TechnicalRow("OTA", profile?.ota?.toString() ?: "Unknown")
        } }
        item { TechnicalCard("Status flags") {
            TechnicalRow("Hold", state.status?.maintenanceEnabled.toString())
            TechnicalRow("Empty", state.status?.empty.toString())
            TechnicalRow("Charging", state.status?.charging.toString())
            TechnicalRow("Night light", state.status?.nightLightEnabled.toString())
            TechnicalRow("Auto-off", state.status?.safetyWaitHours?.let { "$it hours" } ?: "Unknown")
            TechnicalRow("Battery voltage", state.status?.batteryMillivolts?.let { "$it mV" } ?: "Unknown")
        } }
        item { TechnicalCard("Validated matrix") {
            Text("Validated: live status and version; 130→131→130°F; 2h↔4h auto-off; hold and charge lights; Android hold enable and heating trend.")
        } }
        item { TechnicalCard("Roadmap") {
            Text("Complete: reliable BLE/readback, Fahrenheit and presets, alerts and timers, lighting, tile and widget, multi-mug history.")
            Text("Remaining: ambient/music physical checks and a stable signed release.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        } }
        item { Text("Recent TX/RX events", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        if (state.events.isEmpty()) item { Text("No BLE events in this session", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.events.asReversed().take(30)) { event ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(event.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

@Composable
private fun TechnicalCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun TechnicalRow(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryScreen(state: BleState, scan: () -> Unit, connect: (MugDevice) -> Unit, manageMugs: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AMUG", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = manageMugs) { Icon(Icons.Rounded.Devices, "Mug manager") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Coffee, held\nexactly right.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("Private Bluetooth control. No login. No cloud.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
            item {
                Button(onClick = scan, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.BluetoothSearching, null)
                    Text(if (state.stage == ConnectionStage.SCANNING) "Scanning nearby" else "Find my mug", modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
                }
            }
            state.error?.let { error -> item { WarningCard("Bluetooth issue", error) } }
            if (state.stage in setOf(ConnectionStage.CONNECTING, ConnectionStage.RECONNECTING, ConnectionStage.INITIALIZING)) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        when (state.stage) {
                            ConnectionStage.CONNECTING -> "Connecting to ${state.connectedName}"
                            ConnectionStage.RECONNECTING -> "Connection lost. Reconnecting…"
                            else -> "Connected. Reading mug status…"
                        },
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
            if (state.devices.isEmpty() && state.stage == ConnectionStage.SCANNING) item {
                Text("Keep the mug nearby and awake. Devices appear here as they are found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.devices, key = MugDevice::address) { device ->
                Card(onClick = { connect(device) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(22.dp)) {
                    ListItem(
                        headlineContent = { Text(device.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("Signal ${device.rssi} dBm") },
                        leadingContent = {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Coffee, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
                            }
                        },
                        trailingContent = { Text("Connect", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
        }
    }
}

@Composable
private fun MugManagerDialog(
    mugs: List<MugEntity>,
    selectedId: Long?,
    sessions: List<MugSessionEntity>,
    historyRetentionDays: Int,
    select: (Long) -> Unit,
    rename: (Long, String) -> Unit,
    forget: (Long) -> Unit,
    clearHistory: () -> Unit,
    setHistoryRetention: (Int) -> Unit,
    dismiss: () -> Unit,
) {
    Dialog(onDismissRequest = dismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            LazyColumn(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Mug manager", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                if (mugs.isEmpty()) item { Text("No remembered mugs yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(mugs, key = MugEntity::id) { mug ->
                    var name by remember(mug.id, mug.name) { mutableStateOf(mug.name) }
                    OutlinedCard {
                        Column(Modifier.padding(14.dp)) {
                            OutlinedTextField(name, { name = it }, label = { Text("Mug name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text(mug.bleAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { rename(mug.id, name) }, modifier = Modifier.height(48.dp)) { Text("Rename") }
                                TextButton(onClick = { forget(mug.id) }, modifier = Modifier.height(48.dp)) { Text("Forget") }
                                Button(onClick = { select(mug.id); dismiss() }, enabled = selectedId != mug.id, modifier = Modifier.height(48.dp)) { Text(if (selectedId == mug.id) "Selected" else "Select") }
                            }
                        }
                    }
                }
                item {
                    Text("Session history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Stored only on this phone", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7, 30, 90).forEach { days ->
                            OutlinedButton(onClick = { setHistoryRetention(days) }, modifier = Modifier.weight(1f).height(48.dp), enabled = historyRetentionDays != days) { Text("$days days") }
                        }
                    }
                }
                items(sessions.take(10), key = MugSessionEntity::id) { session ->
                    val end = session.endedAt ?: System.currentTimeMillis()
                    val minutes = ((end - session.startedAt).coerceAtLeast(0) / 60_000)
                    Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.startedAt))} · $minutes min · ${session.endReason ?: "active"}")
                }
                if (sessions.isNotEmpty()) item { TextButton(onClick = clearHistory, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Clear selected mug history") } }
                item { TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Close") } }
            }
        }
    }
}

@Composable
private fun TemperatureEntryDialog(current: Double, unit: TemperatureUnit, onDismiss: () -> Unit, onApply: (Double) -> Unit) {
    var value by remember { mutableStateOf(current.roundToInt().toString()) }
    val range = if (unit == TemperatureUnit.FAHRENHEIT) 120.0..150.0 else 48.0..66.0
    val parsed = value.toDoubleOrNull()
    val valid = parsed != null && parsed in range
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Set exact temperature", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${range.start.roundToInt()}–${range.endInclusive.roundToInt()}${unit.symbol}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value, { value = it.filter { char -> char.isDigit() || char == '.' }.take(5) },
                    label = { Text("Temperature ${unit.symbol}") }, singleLine = true,
                    isError = value.isNotEmpty() && !valid, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onApply(parsed!!) }, enabled = valid, modifier = Modifier.height(48.dp)) { Text("Set temperature") }
                }
            }
        }
    }
}

@Composable
private fun LedColorEditor(stop: LedColorStop, label: String, unit: TemperatureUnit, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val initial = remember(stop.color) { FloatArray(3).also { android.graphics.Color.colorToHSV(0xff000000.toInt() or stop.color, it) } }
    var hue by remember(stop.color) { mutableFloatStateOf(initial[0]) }
    var saturation by remember(stop.color) { mutableFloatStateOf(initial[1]) }
    var brightness by remember(stop.color) { mutableFloatStateOf(initial[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xffffff
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("$label color", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Anchor: ${unit.display(stop.celsius).roundToInt()}${unit.symbol}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp).height(72.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xff000000.toInt() or color)).semantics { contentDescription = "Selected color #${"%06X".format(color)}" })
                Text("Hue", fontWeight = FontWeight.Bold)
                Slider(hue, { hue = it }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = "Hue" })
                Text("Saturation", fontWeight = FontWeight.Bold)
                Slider(saturation, { saturation = it }, valueRange = 0f..1f, modifier = Modifier.semantics { contentDescription = "Saturation" })
                Text("Brightness", fontWeight = FontWeight.Bold)
                Slider(brightness, { brightness = it }, valueRange = .1f..1f, modifier = Modifier.semantics { contentDescription = "Brightness" })
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onApply(color) }, modifier = Modifier.height(48.dp)) { Text("Apply color") }
                }
            }
        }
    }
}
