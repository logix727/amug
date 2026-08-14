package dev.logix.amug

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext

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
                val suggestion by viewModel.suggestion.collectAsState()
                AmugApp(
                    state = state,
                    preferences = preferences,
                    mugs = mugs,
                    selectedMug = selectedMug,
                    presets = presets,
                    sessions = sessions,
                    historyRetentionDays = globalPreferences.historyRetentionDays,
                    suggestion = suggestion,
                    scan = ::startScan,
                    connect = viewModel::connect,
                    setTemperature = viewModel::setTemperature,
                    applyPreset = viewModel::applyPreset,
                    applySuggestion = viewModel::applySuggestion,
                    saveSuggestion = viewModel::saveSuggestionAsPreset,
                    resetLearning = viewModel::resetLearning,
                    savePersonalPreset = viewModel::savePersonalPreset,
                    deletePersonalPreset = viewModel::deletePersonalPreset,
                    setHeating = viewModel::setMaintenanceEnabled,
                    setGear = viewModel::setGear,
                    setUnit = viewModel::setUnit,
                    setTemperatureLed = viewModel::setTemperatureLed,
                    setLedColor = viewModel::setLedColor,
                    resetLedPalette = viewModel::resetLedPalette,
                    setSafetyWait = viewModel::setSafetyWait,
                    setMusicMode = viewModel::setMusicMode,
                    setHoldLight = viewModel::setHoldLight,
                    setChargeLight = viewModel::setChargeLight,
                    setSleepTimer = viewModel::setSleepTimer,
                    refresh = viewModel::refresh,
                    reconnect = { viewModel.disconnect(); viewModel.connectLast() },
                    disconnect = viewModel::disconnect,
                    clearEvents = viewModel::clearEvents,
                    exportDiagnostics = { text ->
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AMUG diagnostics", text))
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share AMUG diagnostics"))
                    },
                    selectMug = viewModel::selectMug,
                    renameMug = viewModel::renameMug,
                    forgetMug = viewModel::forgetMug,
                    clearHistory = viewModel::clearHistory,
                    setHistoryRetention = viewModel::setHistoryRetention,
                    alertPreferences = globalPreferences,
                    setAlert = { kind, enabled ->
                        if (enabled) requestNotificationPermission()
                        viewModel.setAlert(kind, enabled)
                    },
                )
            }
        }
    }

    private fun startScan() {
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
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        content = content,
    )
}
