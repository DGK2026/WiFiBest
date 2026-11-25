package com.dgk2026.wifibest

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

data class WifiNetwork(
    val ssid: String,
    val signal: Int
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                WifiScreen(this)
            }
        }
    }
}

@Composable
fun WifiScreen(activity: MainActivity) {
    val context = activity
    val wifiManager = remember {
        context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    var networks by remember { mutableStateOf(emptyList<WifiNetwork>()) }
    var message by remember {
        mutableStateOf("Нажмите кнопку для поиска сетей")
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                scanWifi(wifiManager) { result, text ->
                    networks = result
                    message = text
                }
            } else {
                message = "Разрешите доступ к местоположению"
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "WiFiBest",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

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
                    scanWifi(wifiManager) { result, text ->
                        networks = result
                        message = text
                    }
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
            onClick = {
                context.startActivity(
                    Intent(Settings.Panel.ACTION_WIFI)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Открыть настройки Wi-Fi")
        }

        Spacer(Modifier.height(20.dp))

        LazyColumn {
            items(networks) { network ->
                ListItem(
                    headlineContent = { Text(network.ssid) },
                    supportingContent = {
                        Text("Сигнал: ${network.signal}%")
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

private fun scanWifi(
    wifiManager: WifiManager,
    callback: (List<WifiNetwork>, String) -> Unit
) {
    try {
        val started = wifiManager.startScan()

        val results = wifiManager.scanResults
            .filter { it.SSID.isNotBlank() }
            .distinctBy { it.SSID }
            .sortedByDescending { it.level }
            .map {
                WifiNetwork(
                    ssid = it.SSID,
                    signal = WifiManager.calculateSignalLevel(
                        it.level, 100
                    )
                )
            }

        val text = when {
            results.isEmpty() ->
                "Сети не найдены. Проверьте Wi-Fi и геолокацию"

            else ->
                "Самая сильная сеть: ${results.first().ssid}"
        }

        callback(results, text)
    } catch (e: SecurityException) {
        callback(emptyList(), "Нет разрешения на сканирование Wi-Fi")
    }
}
