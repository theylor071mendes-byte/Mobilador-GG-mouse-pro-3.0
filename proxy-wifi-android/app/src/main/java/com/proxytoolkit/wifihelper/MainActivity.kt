package com.proxytoolkit.wifihelper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val PREFS_NAME = "proxy_wifi_helper_prefs"
private const val KEY_HOST = "proxy_host"
private const val KEY_PORT = "proxy_port"
private const val KEY_BYPASS = "proxy_bypass"

/**
 * App único propósito: ajudar a configurar o proxy da rede Wi-Fi atual.
 *
 * Limitação real e assumida (sem contornar com APIs privadas/root): a
 * partir do Android 10, nenhum app comum (sem ser "dono do aparelho" via
 * MDM/DevicePolicyManager) tem permissão para escrever sozinho o proxy de
 * uma rede Wi-Fi já salva — só a própria tela nativa de configurações
 * permite isso. Por isso este app guarda os dados do proxy localmente,
 * oferece copiar cada campo para a área de transferência, e leva direto
 * para a tela do sistema (Settings.ACTION_WIFI_SETTINGS, API pública e
 * estável) com um passo a passo real de onde tocar a partir dali.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProxyHelperScreen()
                }
            }
        }
    }
}

@Composable
fun ProxyHelperScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var host by remember { mutableStateOf(prefs.getString(KEY_HOST, "") ?: "") }
    var port by remember { mutableStateOf(prefs.getString(KEY_PORT, "") ?: "") }
    var bypass by remember { mutableStateOf(prefs.getString(KEY_BYPASS, "") ?: "") }

    fun persist() {
        prefs.edit()
            .putString(KEY_HOST, host)
            .putString(KEY_PORT, port)
            .putString(KEY_BYPASS, bypass)
            .apply()
    }

    val wifiConnected = remember { isWifiConnected(context) }
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    fun copyToClipboard(label: String, value: String) {
        if (value.isBlank()) return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Proxy Wi-Fi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Atalho para configurar o proxy da sua rede Wi-Fi. O Android não deixa " +
                "nenhum app comum alterar sozinho o proxy de uma rede — só um app de " +
                "administração do aparelho (MDM) pode fazer isso. Então este app guarda " +
                "os dados do seu proxy aqui, prontos para copiar, e já leva você para a " +
                "tela certa do sistema.",
            style = MaterialTheme.typography.bodyMedium
        )

        ConnectionStatusCard(wifiConnected)

        Text("Dados do proxy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = host,
            onValueChange = { host = it; persist() },
            label = { Text("Host do proxy (ex.: 192.168.0.10)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { copyToClipboard("Proxy host", host) }) { Text("Copiar") }
            }
        )

        OutlinedTextField(
            value = port,
            onValueChange = { new -> if (new.all { it.isDigit() } && new.length <= 5) { port = new; persist() } },
            label = { Text("Porta (ex.: 8080)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { copyToClipboard("Proxy porta", port) }) { Text("Copiar") }
            }
        )

        OutlinedTextField(
            value = bypass,
            onValueChange = { bypass = it; persist() },
            label = { Text("Exceções (opcional, separadas por vírgula)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { copyToClipboard("Proxy exceções", bypass) }) { Text("Copiar") }
            }
        )

        Button(
            onClick = {
                persist()
                runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abrir configurações de Wi-Fi")
        }

        StepsCard()

        Text(
            "Os campos acima ficam salvos neste aparelho, então você não precisa digitar de novo da próxima vez.",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ConnectionStatusCard(connected: Boolean) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (connected) "✓ Conectado a uma rede Wi-Fi agora" else "✗ Sem Wi-Fi conectado no momento",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StepsCard() {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Como configurar depois de abrir a tela de Wi-Fi:", fontWeight = FontWeight.Bold)
            Text("1. Toque na rede Wi-Fi já conectada.")
            Text("2. Toque no ícone de lápis (editar rede).")
            Text("3. Toque em \"Opções avançadas\".")
            Text("4. Em \"Proxy\", escolha \"Manual\".")
            Text("5. Cole o host e a porta que você copiou acima.")
            Text("6. Salve. Esses passos podem variar um pouco conforme o fabricante do aparelho.")
        }
    }
}

/** Só confere se há conexão Wi-Fi ativa agora; não lê nome de rede nem precisa de localização. */
private fun isWifiConnected(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return@runCatching false
    val capabilities = cm.getNetworkCapabilities(network) ?: return@runCatching false
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}.getOrDefault(false)
