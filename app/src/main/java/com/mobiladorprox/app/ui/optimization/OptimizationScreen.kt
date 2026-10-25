package com.mobiladorprox.app.ui.optimization

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.components.IconTextRow
import com.mobiladorprox.app.ui.components.NeonCard
import com.mobiladorprox.app.ui.components.PillStatus
import com.mobiladorprox.app.ui.components.SectionTitle
import com.mobiladorprox.app.ui.components.StatusPill
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.NeonPurple
import com.mobiladorprox.app.ui.theme.StatusAmber
import com.mobiladorprox.app.ui.theme.TextSecondary
import com.mobiladorprox.app.util.DeviceInfoProvider

@Composable
fun OptimizationScreen(factory: ViewModelFactory? = null) {
    val context = LocalContext.current
    val viewModel: OptimizationViewModel = viewModel()
    val refreshTick by viewModel.refreshTick.collectAsStateWithLifecycle()

    var ramInfo by remember { mutableStateOf(DeviceInfoProvider.getRamInfo(context)) }
    var deviceInfo by remember { mutableStateOf(DeviceInfoProvider.getDeviceInfo()) }
    var refreshRate by remember { mutableStateOf(DeviceInfoProvider.getRefreshRateHz(context)) }
    var connectionInfo by remember { mutableStateOf(DeviceInfoProvider.getConnectionInfo(context)) }

    androidx.compose.runtime.LaunchedEffect(refreshTick) {
        ramInfo = DeviceInfoProvider.getRamInfo(context)
        deviceInfo = DeviceInfoProvider.getDeviceInfo()
        refreshRate = DeviceInfoProvider.getRefreshRateHz(context)
        connectionInfo = DeviceInfoProvider.getConnectionInfo(context)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionTitle("Otimização", "Informações reais do aparelho, sem estimativas")
                TextButton(onClick = { viewModel.refresh() }) { Text("Atualizar") }
            }
        }

        item {
            NeonCard {
                SectionTitle("Memória RAM")
                Spacer(Modifier.height(10.dp))
                val usedMb = (ramInfo.totalMb - ramInfo.availableMb).coerceAtLeast(0)
                val fraction = if (ramInfo.totalMb > 0) (usedMb.toFloat() / ramInfo.totalMb.toFloat()) else 0f
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (ramInfo.lowMemory) StatusAmber else NeonBlue,
                    trackColor = Color(0xFF2A2A3A)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${usedMb} MB em uso de ${ramInfo.totalMb} MB totais (${ramInfo.availableMb} MB disponíveis)",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (ramInfo.lowMemory) {
                    Spacer(Modifier.height(6.dp))
                    StatusPill("Sistema reporta pouca memória livre", PillStatus.WARNING)
                }
            }
        }

        item {
            NeonCard {
                SectionTitle("Dispositivo")
                Spacer(Modifier.height(10.dp))
                Text("${deviceInfo.manufacturer} ${deviceInfo.model}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Android ${deviceInfo.androidVersion} (API ${deviceInfo.sdkInt})",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    refreshRate?.let { "Taxa de atualização da tela: %.0f Hz".format(it) }
                        ?: "Taxa de atualização da tela: não informada pelo sistema",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            NeonCard {
                SectionTitle("Conexões")
                Spacer(Modifier.height(10.dp))
                StatusRow("Teclado físico", connectionInfo.physicalKeyboardConnected)
                StatusRow("Mouse", connectionInfo.mouseConnected)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (connectionInfo.bluetoothSupported) {
                        if (connectionInfo.bluetoothEnabled) {
                            "Bluetooth ativado — ${connectionInfo.bondedBluetoothDeviceCount} dispositivo(s) pareado(s)"
                        } else "Bluetooth suportado, mas desativado"
                    } else "Este aparelho não possui Bluetooth",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Periféricos USB detectados: ${connectionInfo.usbDeviceCount}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }) { Text("Ajustes de Bluetooth") }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Acessibilidade") }
                }
            }
        }

        item {
            NeonCard(accentColor = NeonPurple) {
                SectionTitle("Dicas gerais")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Estas são práticas comuns de uso do Android — nenhuma delas garante mais FPS " +
                        "ou qualquer ganho de desempenho específico no jogo; o resultado depende do " +
                        "hardware do seu aparelho.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                listOf(
                    "Feche aplicativos em segundo plano que você não está usando.",
                    "Evite carregar o celular enquanto joga sessões longas (reduz aquecimento).",
                    "Prefira Wi-Fi estável ou dados móveis com boa cobertura para menor latência de rede.",
                    "Desative modo de economia de energia durante partidas, se a bateria permitir."
                ).forEach { tip ->
                    Text("• $tip", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier)
                    Spacer(Modifier.height(4.dp))
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }) { Text("Abrir Ajustes do sistema") }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, connected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        StatusPill(if (connected) "Conectado" else "Não detectado", if (connected) PillStatus.ON else PillStatus.OFF)
    }
    Spacer(Modifier.height(4.dp))
}
