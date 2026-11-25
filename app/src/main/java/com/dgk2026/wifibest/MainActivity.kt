package com.dgk2026.wifibest

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val frequency: Int,
    val rssi: Int,
    val signal: Int
)

class MainActivity : ComponentActivity() {

    private lateinit var wifiManager: WifiManager

    var networks by mutableStateOf<List<WifiNetwork>>(emptyList())
        private set

    var message by mutableStateOf("Нажмите кнопку для поиска сетей")
        private set

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                return
            }

            if (!intent.getBooleanExtra(
                    WifiManager.EXTRA_RESULTS_UPDATED, false
                )
            ) {
                message = "Сканирование не обновилось. Попробуйте ещё раз"
                return
            }

            try {
                networks = wifiManager.scanResults
                    .filter { it.SSID.isNotBlank() }
                    .sortedByDescending { it.level }
                    .map {
                        WifiNetwork(
                            ssid = it.SSID,
                            bssid = it.BSSID ?: "—",
                            frequency = it.frequency,
                            rssi = it.level,
                            signal = ((it.level + 100) * 2).coerceIn(0, 100)
                        )
                    }

                message = if (networks.isEmpty()) {
                    "Сети не найдены"
                } else {
                    "Найдено сетей: ${networks.size}. Самая сильная: ${networks.first().ssid}"
                }
            } catch (_: SecurityException) {
                message = "Нет разрешения на сканирование Wi-Fi"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wifiManager = applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        setContent {
            MaterialTheme {
                WifiScreen(this)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        ContextCompat.registerReceiver(
            this,
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(scanReceiver)
        super.onStop()
    }

    fun scanWifi() {
        try {
            message = "Сканирование Wi-Fi…"

            val started = wifiManager.startScan()

            if (!started) {
                message = "Android не запустил сканирование. Подождите и попробуйте снова"
            }
        } catch (_: SecurityException) {
            message = "Нет разрешения на сканирование Wi-Fi"
        }
    }

    fun openWifiSettings() {
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}

@Composable
fun WifiScreen(activity: MainActivity) {
    val context = activity

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                activity.scanWifi()
            } else {
                activity.message = "Разрешите доступ к местоположению"
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "WiFiBest",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = activity.message,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val permissions = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )

                val granted = permissions.all {
                    ContextCompat.checkSelfPermission(
                        context, it
                    ) == PackageManager.PERMISSION_GRANTED
                }

                if (granted) {
                    activity.scanWifi()
                } else {
                    permissionLauncher.launch(permissions)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Найти лучшую сеть")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { activity.openWifiSettings() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Открыть настройки Wi-Fi")
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = activity.networks,
                key = { it.bssid }
            ) { network ->
                ListItem(
                    modifier = Modifier.clickable {
                        activity.openWifiSettings()
                    },
                    headlineContent = {
                        Text(network.ssid)
                    },
                    supportingContent = {
                        Text(
                            "Сигнал: ${network.signal}% (${network.rssi} dBm)\n" +
                            "${network.frequency} МГц • ${network.bssid}"
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
