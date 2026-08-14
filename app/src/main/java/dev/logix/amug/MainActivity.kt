package dev.logix.amug

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MugViewModel>()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) ensureBluetoothThenScan()
    }
    private val bluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.scan() }
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmugTheme {
                val state by viewModel.state.collectAsState()
                val preferences by viewModel.preferences.collectAsState()
                val mugs by viewModel.mugs.collectAsState()
                val selectedMug by viewModel.selectedMug.collectAsState()
                val presets by viewModel.presets.collectAsState()
                val sessions by viewModel.sessions.collectAsState()
                val globalPreferences by viewModel.globalPreferences.collectAsState()
                AmugApp(
                    state,
                    preferences,
                    mugs,
                    selectedMug,
                    presets,
                    sessions,
                    globalPreferences.historyRetentionDays,
                    ::startScan,
                    viewModel::connect,
                    viewModel::setTemperature,
                    viewModel::setMaintenanceEnabled,
                    viewModel::setGear,
                    viewModel::setUnit,
                    viewModel::setTemperatureLed,
                    viewModel::setLedColor,
                    viewModel::resetLedPalette,
                    viewModel::setSafetyWait,
                    viewModel::setMusicMode,
                    viewModel::setHoldLight,
                    viewModel::setChargeLight,
                    viewModel::setSleepTimer,
                    viewModel::refresh,
                    viewModel::selectMug,
                    viewModel::renameMug,
                    viewModel::forgetMug,
                    viewModel::clearHistory,
                    viewModel::setHistoryRetention,
                )
            }
        }
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
        if (permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } && adapter.isEnabled) requestNotificationPermission()
    }

    private fun startScan() {
        requestNotificationPermission()
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        if (permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) ensureBluetoothThenScan()
        else permissionLauncher.launch(permissions)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensureBluetoothThenScan() {
        val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
        if (adapter.isEnabled) viewModel.scan()
        else bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}

@Composable
private fun AmugTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmugApp(
    state: BleState,
    preferences: UserPreferences,
    mugs: List<MugEntity>,
    selectedMug: MugEntity?,
    presets: List<PresetEntity>,
    sessions: List<MugSessionEntity>,
    historyRetentionDays: Int,
    scan: () -> Unit,
    connect: (MugDevice) -> Unit,
    setTemperature: (Double) -> Unit,
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
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f), MaterialTheme.colorScheme.background),
                    radius = 1100f,
                ),
            ).padding(padding),
        ) {
            AnimatedContent(state.stage == ConnectionStage.READY, label = "screen") { connected ->
                if (connected) ControlScreen(state, preferences, presets, setTemperature, setHeating, setGear, setUnit, setTemperatureLed, setLedColor, resetLedPalette, setSafetyWait, setMusicMode, setHoldLight, setChargeLight, setSleepTimer, refresh, { showMugs = true })
                else DiscoveryScreen(state, scan, connect, { showMugs = true })
            }
        }
    }
    if (showMugs) MugManagerDialog(mugs, selectedMug?.id, sessions, historyRetentionDays, selectMug, renameMug, forgetMug, clearHistory, setHistoryRetention) { showMugs = false }
}

@Composable
private fun DiscoveryScreen(state: BleState, scan: () -> Unit, connect: (MugDevice) -> Unit, manageMugs: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
        Text("AMUG", fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 4.sp)
        Spacer(Modifier.height(10.dp))
        Text("Coffee, held\nexactly right.", fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
        Text("Local Bluetooth control. No login. No cloud.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
        TextButton(onClick = manageMugs) { Icon(Icons.Rounded.Devices, null); Text("  My mugs") }
        Button(onClick = scan, modifier = Modifier.padding(top = 28.dp).height(56.dp), shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.AutoMirrored.Rounded.BluetoothSearching, null)
            Text(if (state.stage == ConnectionStage.SCANNING) "  Scanning nearby" else "  Find my mug", fontWeight = FontWeight.Bold)
        }
        state.error?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(top = 14.dp)) }
        if (state.stage == ConnectionStage.CONNECTING || state.stage == ConnectionStage.RECONNECTING || state.stage == ConnectionStage.INITIALIZING) {
            Text(
                when (state.stage) {
                    ConnectionStage.CONNECTING -> "Connecting to ${state.connectedName}…"
                    ConnectionStage.RECONNECTING -> "Connection lost — reconnecting…"
                    else -> "Connected — reading mug status…"
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 24.dp)) {
            items(state.devices, key = MugDevice::address) { device ->
                Card(onClick = { connect(device) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Coffee, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(device.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${device.rssi} dBm", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Connect", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlScreen(
    state: BleState,
    preferences: UserPreferences,
    presets: List<PresetEntity>,
    setTemperature: (Double) -> Unit,
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
    val status = state.status
    val unit = preferences.unit
    var target by remember(status?.targetC, unit) { mutableDoubleStateOf(unit.display(status?.targetC ?: 57.0)) }
    var editingLedStop by remember { mutableStateOf<Int?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showTemperatureEntry by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AMUG", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text(state.connectedName ?: "S6 Plus", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showDiagnostics = true }) { Icon(Icons.Rounded.Info, "Diagnostics") }
            IconButton(onClick = manageMugs) { Icon(Icons.Rounded.Devices, "My mugs") }
            IconButton(onClick = refresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
        }
      }
      if (state.profile == MugProfile.S6_PLUS) item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Automatic shutoff", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Experimental · stored by mug firmware; physical validation pending", color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(2, 4).forEach { hours ->
                        Button(onClick = { setSafetyWait(hours) }, modifier = Modifier.weight(1f), enabled = status != null) {
                            Text("$hours hours")
                        }
                    }
                }
            }
        }
      }
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Sleep timer", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Phone-supervised · requires AMUG to remain connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.sleepTimerEndsAt?.let { end ->
                    val minutes = ((end - System.currentTimeMillis()).coerceAtLeast(0) / 60_000) + 1
                    Text("About $minutes minutes remaining", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 60).forEach { minutes ->
                        OutlinedButton(onClick = { setSleepTimer(minutes) }, modifier = Modifier.weight(1f), enabled = status != null) { Text("$minutes min") }
                    }
                }
                TextButton(onClick = { setSleepTimer(null) }, enabled = state.sleepTimerEndsAt != null) { Text("Cancel timer") }
            }
        }
      }
      if (state.profile == MugProfile.S6_PLUS) item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Lighting", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Music effects work only while temperature hold is on", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..2).forEach { mode ->
                        OutlinedButton(onClick = { setMusicMode(mode) }, modifier = Modifier.weight(1f), enabled = status?.maintenanceEnabled == true) { Text("Effect ${mode + 1}") }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (3..5).forEach { mode ->
                        OutlinedButton(onClick = { setMusicMode(mode) }, modifier = Modifier.weight(1f), enabled = status?.maintenanceEnabled == true) { Text("Effect ${mode + 1}") }
                    }
                }
                TextButton(onClick = { setMusicMode(null) }, enabled = status != null) { Text("Music lighting off") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hold light", modifier = Modifier.weight(1f))
                    Switch(checked = status?.holdLightMode == 1, enabled = status != null, onCheckedChange = setHoldLight)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Charging light", modifier = Modifier.weight(1f))
                    Switch(checked = status?.chargeLightMode == 1, enabled = status != null, onCheckedChange = setChargeLight)
                }
            }
        }
      }
      item {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))).padding(28.dp)) {
            Column {
                Text("RIGHT NOW", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .7f), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(status?.let { "${unit.display(it.currentC).roundToInt()}°" } ?: "--°", fontSize = 80.sp, lineHeight = 90.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(if (status?.maintenanceEnabled == true) " Temperature hold on · ${unit.display(status.targetC).roundToInt()}${unit.symbol}" else " Temperature hold off", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (status?.charging == true) Icon(Icons.Rounded.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(status?.batteryPercent?.let { " $it%" } ?: " --", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        }
      }
      if (status?.empty == true) item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text("Empty — the mug has stopped temperature hold. Add liquid before turning hold on.", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        }
      }
      if (target >= 140.0 && unit == TemperatureUnit.FAHRENHEIT || target >= 60.0 && unit == TemperatureUnit.CELSIUS) item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text("Very hot · Sip carefully", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Black, modifier = Modifier.padding(18.dp))
        }
      }
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(28.dp)) {
            if (state.profile == MugProfile.S6_PLUS) {
                Column(Modifier.padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Target temperature", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${target.roundToInt()}${unit.symbol}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            modifier = Modifier.clickable { showTemperatureEntry = true },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { target = (target - 1).coerceAtLeast(if (unit == TemperatureUnit.FAHRENHEIT) 120.0 else 48.0); setTemperature(unit.toCelsius(target)) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Remove, "Decrease target") }
                        Text("Exact 1${unit.symbol} steps", modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { target = (target + 1).coerceAtMost(if (unit == TemperatureUnit.FAHRENHEIT) 150.0 else 66.0); setTemperature(unit.toCelsius(target)) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.Add, "Increase target") }
                    }
                    val range = if (unit == TemperatureUnit.FAHRENHEIT) 120f..150f else 48f..66f
                    Slider(value = target.toFloat().coerceIn(range), onValueChange = { target = it.toDouble() }, onValueChangeFinished = { setTemperature(unit.toCelsius(target)) }, valueRange = range, steps = if (unit == TemperatureUnit.FAHRENHEIT) 29 else 17)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (unit == TemperatureUnit.FAHRENHEIT) "120°" else "48°", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (unit == TemperatureUnit.FAHRENHEIT) "150°" else "66°", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(state.commandMessage ?: "Changes are confirmed by mug readback", color = when (state.commandState) { CommandState.FAILED -> MaterialTheme.colorScheme.error; CommandState.CONFIRMED -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant }, modifier = Modifier.padding(top = 10.dp))
                    Text("Drink presets", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { preset ->
                            val celsius = preset.temperatureCentiC / 100.0
                            OutlinedButton(onClick = { target = unit.display(celsius); setTemperature(celsius) }) { Text(preset.name, fontSize = 12.sp) }
                        }
                    }
                }
            } else {
                Column(Modifier.padding(24.dp)) {
                    Text("Heat preset", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Low", "Medium", "High").forEachIndexed { index, label ->
                            Button(onClick = { setGear(index + 1) }, modifier = Modifier.weight(1f)) { Text(label) }
                        }
                    }
                }
            }
        }
      }
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Temperature hold", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (status?.empty == true) "Unavailable while mug is empty" else "Maintain the selected temperature", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = status?.maintenanceEnabled == true, enabled = status != null && !status.empty && !status.nightLightEnabled, onCheckedChange = setHeating)
            }
        }
      }
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Display", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { setUnit(TemperatureUnit.FAHRENHEIT) }, modifier = Modifier.weight(1f), enabled = unit != TemperatureUnit.FAHRENHEIT) { Text("°F") }
                    Button(onClick = { setUnit(TemperatureUnit.CELSIUS) }, modifier = Modifier.weight(1f), enabled = unit != TemperatureUnit.CELSIUS) { Text("°C") }
                }
            }
        }
      }
      if (state.profile == MugProfile.S6_PLUS) item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Temperature glow", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Ambient display mode · turning it on pauses temperature hold", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = preferences.temperatureLed, onCheckedChange = setTemperatureLed)
                }
                val currentColor = status?.let { MugProtocol.temperatureColor(it.currentC, preferences.ledPalette) }
                if (status != null && currentColor != null) {
                    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xff000000.toInt() or currentColor)))
                        Text(
                            "  Right now: ${unit.display(status.currentC).roundToInt()}${unit.symbol}  •  #${"%06X".format(currentColor)}",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Box(
                    Modifier.fillMaxWidth().padding(top = 18.dp).height(18.dp).clip(CircleShape).background(
                        Brush.horizontalGradient(preferences.ledPalette.map { Color(0xff000000.toInt() or it.color) }),
                    ),
                )
                Text("Tap any color to customize it", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                preferences.ledPalette.forEachIndexed { index, stop ->
                    val label = listOf("Cool", "Lukewarm", "Warm", "Ready", "Hot", "Very hot")[index]
                    Row(
                        Modifier.fillMaxWidth().clickable { editingLedStop = index }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xff000000.toInt() or stop.color)))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(label, fontWeight = FontWeight.Bold)
                            Text("${unit.display(stop.celsius).roundToInt()}${unit.symbol} anchor", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("#${"%06X".format(stop.color)}", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = resetLedPalette, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Reset default colors")
                }
            }
        }
      }
    }
    editingLedStop?.let { index ->
        LedColorEditor(
            stop = preferences.ledPalette[index],
            label = listOf("Cool", "Lukewarm", "Warm", "Ready", "Hot", "Very hot")[index],
            unit = unit,
            onDismiss = { editingLedStop = null },
            onApply = { color -> setLedColor(index, color); editingLedStop = null },
        )
    }
    if (showDiagnostics) DiagnosticsDialog(state = state, onDismiss = { showDiagnostics = false })
    if (showTemperatureEntry) TemperatureEntryDialog(
        current = target,
        unit = unit,
        onDismiss = { showTemperatureEntry = false },
        onApply = { value -> target = value; setTemperature(unit.toCelsius(value)); showTemperatureEntry = false },
    )
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
            LazyColumn(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("My mugs", fontSize = 24.sp, fontWeight = FontWeight.Black) }
                if (mugs.isEmpty()) item { Text("No remembered mugs yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(mugs, key = MugEntity::id) { mug ->
                    var name by remember(mug.id, mug.name) { mutableStateOf(mug.name) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(14.dp)) {
                            OutlinedTextField(name, { name = it }, label = { Text("Mug name") }, singleLine = true)
                            Text(mug.bleAddress, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { rename(mug.id, name) }) { Text("Rename") }
                                TextButton(onClick = { forget(mug.id) }) { Text("Forget") }
                                Button(onClick = { select(mug.id); dismiss() }, enabled = selectedId != mug.id) { Text(if (selectedId == mug.id) "Selected" else "Select") }
                            }
                        }
                    }
                }
                item {
                    Text("Session history", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text("Stored only on this phone", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7, 30, 90).forEach { days ->
                            OutlinedButton(onClick = { setHistoryRetention(days) }, modifier = Modifier.weight(1f), enabled = historyRetentionDays != days) { Text("$days days") }
                        }
                    }
                }
                items(sessions.take(10), key = MugSessionEntity::id) { session ->
                    val end = session.endedAt ?: System.currentTimeMillis()
                    val minutes = ((end - session.startedAt).coerceAtLeast(0) / 60_000)
                    Text("${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(session.startedAt))} · ${minutes} min · ${session.endReason ?: "active"}")
                }
                if (sessions.isNotEmpty()) item { TextButton(onClick = clearHistory, modifier = Modifier.fillMaxWidth()) { Text("Clear selected mug history") } }
                item { TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") } }
            }
        }
    }
}

@Composable
private fun TemperatureEntryDialog(
    current: Double,
    unit: TemperatureUnit,
    onDismiss: () -> Unit,
    onApply: (Double) -> Unit,
) {
    var value by remember { mutableStateOf(current.roundToInt().toString()) }
    val range = if (unit == TemperatureUnit.FAHRENHEIT) 120.0..150.0 else 48.0..66.0
    val parsed = value.toDoubleOrNull()
    val valid = parsed != null && parsed in range
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Set exact temperature", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("${range.start.roundToInt()}–${range.endInclusive.roundToInt()}${unit.symbol}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { char -> char.isDigit() || char == '.' }.take(5) },
                    label = { Text("Temperature ${unit.symbol}") },
                    singleLine = true,
                    isError = value.isNotEmpty() && !valid,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onApply(parsed!!) }, enabled = valid) { Text("Set temperature") }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsDialog(state: BleState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            LazyColumn(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Technical & roadmap", fontSize = 24.sp, fontWeight = FontWeight.Black) }
                item { Text("Device: ${state.connectedName ?: "Not connected"}") }
                item { Text("Profile: ${state.profile ?: "Unknown"}") }
                item { Text("Firmware: ${state.version?.firmware ?: "Unknown"} · Hardware: ${state.version?.hardware ?: "Not reported"}") }
                item { Text("Connection: ${state.stage}") }
                item { Text("Service A300… · Write A301… · Notify A302… · OTA A303…", fontSize = 12.sp) }
                item { Text("Status 03 · Target 04 <C integer> <hundredths> · Hold 06 01 · Off 06 00", fontSize = 12.sp) }
                item { Text("Auto-off: ${state.status?.safetyWaitHours?.let { "$it hours" } ?: "Unknown"}") }
                item { Text("Battery voltage: ${state.status?.batteryMillivolts?.let { "$it mV" } ?: "Unknown"}") }
                item { Text("State flags: hold=${state.status?.maintenanceEnabled} empty=${state.status?.empty} charging=${state.status?.charging} night=${state.status?.nightLightEnabled}", fontSize = 12.sp) }
                item { Text("Hardware validation", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                item { Text("✓ Live status/version  ✓ 130→131→130°F  ✓ 2h↔4h auto-off  ✓ hold/charge lights  ✓ Android hold enable + heating trend") }
                item { Text("Roadmap", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                item { Text("✓ Reliable BLE/readback  ✓ Fahrenheit/presets  ✓ Alerts/timers  ✓ Lighting  ✓ Tile/widget  ✓ Multi-mug/history  ◻ Ambient/music physical checks  ◻ Stable signed release") }
                item { Text("Recent BLE events", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(state.events.asReversed().take(30)) { event -> Text(event.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Close") } }
            }
        }
    }
}

@Composable
private fun LedColorEditor(
    stop: LedColorStop,
    label: String,
    unit: TemperatureUnit,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val initial = remember(stop.color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(0xff000000.toInt() or stop.color, it) }
    }
    var hue by remember(stop.color) { mutableFloatStateOf(initial[0]) }
    var saturation by remember(stop.color) { mutableFloatStateOf(initial[1]) }
    var brightness by remember(stop.color) { mutableFloatStateOf(initial[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xffffff
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp)) {
                Text("$label color", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Anchor: ${unit.display(stop.celsius).roundToInt()}${unit.symbol}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp).height(80.dp).clip(RoundedCornerShape(22.dp))
                        .background(Color(0xff000000.toInt() or color)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("#${"%06X".format(color)}", color = if (brightness > .55f) Color.Black else Color.White, fontWeight = FontWeight.Black)
                }
                Text("Hue", fontWeight = FontWeight.Bold)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Saturation", fontWeight = FontWeight.Bold)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
                Text("Brightness", fontWeight = FontWeight.Bold)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = .1f..1f)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onApply(color) }) { Text("Apply color") }
                }
            }
        }
    }
}
