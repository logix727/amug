package dev.logix.amug

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MugViewModel>()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) ensureBluetoothThenScan()
    }
    private val bluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.scan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmugTheme {
                val state by viewModel.state.collectAsState()
                val preferences by viewModel.preferences.collectAsState()
                AmugApp(
                    state,
                    preferences,
                    ::startScan,
                    viewModel::connect,
                    viewModel::setTemperature,
                    viewModel::setHeating,
                    viewModel::setGear,
                    viewModel::setUnit,
                    viewModel::setTemperatureLed,
                    viewModel::refresh,
                )
            }
        }
    }

    private fun startScan() {
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        if (permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) ensureBluetoothThenScan()
        else permissionLauncher.launch(permissions)
    }

    private fun ensureBluetoothThenScan() {
        val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
        if (adapter.isEnabled) viewModel.scan()
        else bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}

private val Ink = Color(0xFF17120F)
private val Cream = Color(0xFFFFF4E8)
private val Ember = Color(0xFFFF6B35)
private val Honey = Color(0xFFFFB35C)
private val Espresso = Color(0xFF2B1B15)

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
    scan: () -> Unit,
    connect: (MugDevice) -> Unit,
    setTemperature: (Double) -> Unit,
    setHeating: (Boolean) -> Unit,
    setGear: (Int) -> Unit,
    setUnit: (TemperatureUnit) -> Unit,
    setTemperatureLed: (Boolean) -> Unit,
    refresh: () -> Unit,
) {
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
                if (connected) ControlScreen(state, preferences, setTemperature, setHeating, setGear, setUnit, setTemperatureLed, refresh)
                else DiscoveryScreen(state, scan, connect)
            }
        }
    }
}

@Composable
private fun DiscoveryScreen(state: BleState, scan: () -> Unit, connect: (MugDevice) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
        Text("AMUG", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Honey, letterSpacing = 4.sp)
        Spacer(Modifier.height(10.dp))
        Text("Coffee, held\nexactly right.", fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
        Text("Local Bluetooth control. No login. No cloud.", color = Cream.copy(alpha = .65f), modifier = Modifier.padding(top = 14.dp))
        Button(onClick = scan, modifier = Modifier.padding(top = 28.dp).height(56.dp), shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Rounded.BluetoothSearching, null)
            Text(if (state.stage == ConnectionStage.SCANNING) "  Scanning nearby" else "  Find my mug", fontWeight = FontWeight.Bold)
        }
        state.error?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(top = 14.dp)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 24.dp)) {
            items(state.devices, key = MugDevice::address) { device ->
                Card(onClick = { connect(device) }, colors = CardDefaults.cardColors(containerColor = Espresso.copy(alpha = .9f)), shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Ember.copy(alpha = .18f), modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Coffee, null, tint = Ember, modifier = Modifier.padding(12.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(device.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${device.rssi} dBm", color = Cream.copy(alpha = .55f))
                        }
                        Text("Connect", color = Honey, fontWeight = FontWeight.Bold)
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
    setTemperature: (Double) -> Unit,
    setHeating: (Boolean) -> Unit,
    setGear: (Int) -> Unit,
    setUnit: (TemperatureUnit) -> Unit,
    setTemperatureLed: (Boolean) -> Unit,
    refresh: () -> Unit,
) {
    val status = state.status
    val unit = preferences.unit
    var target by remember(status?.targetC, unit) { mutableDoubleStateOf(unit.display(status?.targetC ?: 57.0)) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AMUG", color = Honey, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text(state.connectedName ?: "S6 Plus", color = Cream.copy(alpha = .6f))
            }
            IconButton(onClick = refresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
        }
      }
      item {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(Brush.linearGradient(listOf(Ember, Color(0xFFB53723)))).padding(28.dp)) {
            Column {
                Text("RIGHT NOW", color = Color.White.copy(alpha = .7f), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(status?.let { "${unit.display(it.currentC).roundToInt()}°" } ?: "--°", fontSize = 92.sp, lineHeight = 102.sp, fontWeight = FontWeight.Black, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color.White)
                    Text(if (status?.heating == true) " Heating to ${unit.display(status.targetC).roundToInt()}${unit.symbol}" else " Holding steady", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.BatteryChargingFull, null, tint = Color.White)
                    Text(status?.batteryPercent?.let { " $it%" } ?: " --", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
      }
      item {
        Card(colors = CardDefaults.cardColors(containerColor = Espresso.copy(alpha = .94f)), shape = RoundedCornerShape(28.dp)) {
            if (state.profile == MugProfile.S6_PLUS) {
                Column(Modifier.padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Target temperature", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text("${target.roundToInt()}${unit.symbol}", color = Honey, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    }
                    val range = if (unit == TemperatureUnit.FAHRENHEIT) 120f..150f else 48f..66f
                    Slider(value = target.toFloat().coerceIn(range), onValueChange = { target = it.toDouble() }, onValueChangeFinished = { setTemperature(unit.toCelsius(target)) }, valueRange = range, steps = if (unit == TemperatureUnit.FAHRENHEIT) 29 else 17)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (unit == TemperatureUnit.FAHRENHEIT) "120°" else "48°", color = Cream.copy(alpha = .5f))
                        Text(if (unit == TemperatureUnit.FAHRENHEIT) "150°" else "66°", color = Cream.copy(alpha = .5f))
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
        Card(colors = CardDefaults.cardColors(containerColor = Espresso.copy(alpha = .94f)), shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Ember.copy(alpha = .18f), modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = Ember, modifier = Modifier.padding(12.dp))
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Heat mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (status?.empty == true) "Mug appears empty" else "Keep this drink warm", color = Cream.copy(alpha = .55f))
                }
                Switch(checked = status?.heating == true, onCheckedChange = setHeating)
            }
        }
      }
      item {
        Card(colors = CardDefaults.cardColors(containerColor = Espresso.copy(alpha = .94f)), shape = RoundedCornerShape(28.dp)) {
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
        Card(colors = CardDefaults.cardColors(containerColor = Espresso.copy(alpha = .94f)), shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color(status?.let { MugProtocol.temperatureColor(it.currentC) } ?: 0x2388FF)))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Temperature glow", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("LED shifts blue → amber → red", color = Cream.copy(alpha = .55f))
                }
                Switch(checked = preferences.temperatureLed, onCheckedChange = setTemperatureLed)
            }
        }
      }
    }
}
