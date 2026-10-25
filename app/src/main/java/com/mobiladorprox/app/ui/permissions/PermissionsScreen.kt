package com.mobiladorprox.app.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mobiladorprox.app.service.MapperAccessibilityService
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
fun PermissionsScreen() {
    val context = LocalContext.current

    var accessibilityEnabled by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(true) }
    var bluetoothGranted by remember { mutableStateOf(true) }

    fun refresh() {
        accessibilityEnabled = DeviceInfoProvider.isAccessibilityServiceEnabled(
            context, MapperAccessibilityService::class.java.name
        )
        overlayGranted = DeviceInfoProvider.canDrawOverlays(context)
        notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
        bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionTitle("Permissões", "Para que cada uma serve, de forma transparente")
        }

        item {
            PermissionCard(
                title = "Serviço de Acessibilidade",
                granted = accessibilityEnabled,
                description = "Usado para ler teclas físicas do teclado (USB/Bluetooth) e simular " +
                    "toques na tela (API oficial de gestos de acessibilidade). O app está configurado " +
                    "para NÃO ler o conteúdo da tela — apenas reage a eventos de tecla.",
                actionLabel = "Abrir Ajustes de Acessibilidade",
                onAction = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )
        }

        item {
            PermissionCard(
                title = "Exibir sobre outros aplicativos",
                granted = overlayGranted,
                description = "Necessária para mostrar o painel flutuante arrastável enquanto você " +
                    "está dentro do jogo.",
                actionLabel = "Conceder permissão",
                onAction = {
                    overlayLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }
            )
        }

        item {
            PermissionCard(
                title = "Notificações",
                granted = notificationGranted,
                description = "O painel flutuante roda como serviço em primeiro plano, e o Android " +
                    "exige uma notificação visível enquanto ele está ativo (a partir do Android 13). " +
                    "Se negada, o mapeador continua funcionando, só sem a notificação visível.",
                actionLabel = "Permitir notificações",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                showAction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            )
        }

        item {
            PermissionCard(
                title = "Dispositivos Bluetooth próximos",
                granted = bluetoothGranted,
                description = "Opcional: usada só para mostrar o nome e a quantidade de controles/" +
                    "teclados/mouses Bluetooth pareados na tela Otimização. O mapeamento em si " +
                    "funciona sem essa permissão.",
                actionLabel = "Permitir",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
                showAction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            )
        }

        item {
            NeonCard(accentColor = StatusAmber) {
                SectionTitle("Limitações importantes do Android")
                Spacer(Modifier.height(8.dp))
                BulletText(
                    "Os toques são simulados por gestos (API de Acessibilidade), não por acesso ao " +
                        "motor do jogo — a precisão depende da calibração manual das posições dos " +
                        "botões para a resolução do seu aparelho."
                )
                BulletText(
                    "Quando a câmera por mouse está ativa, a janela transparente usada para capturar " +
                        "o ponteiro assume o foco da tela. Alguns jogos (inclusive o Free Fire) podem " +
                        "pausar ou exibir o menu de pausa quando percebem perda de foco de janela — " +
                        "isso é um comportamento do próprio jogo/Android, não um defeito do app."
                )
                BulletText(
                    "Este app não lê nem modifica a memória de nenhum outro aplicativo, não injeta " +
                        "código e não tenta contornar nenhum sistema anticheat."
                )
                BulletText(
                    "Não há qualquer garantia de aumento de FPS ou desempenho — os ajustes de " +
                        "sensibilidade e \"DPI\" só mudam a forma como os gestos são gerados."
                )
            }
        }

        item {
            NeonCard(accentColor = NeonPurple) {
                SectionTitle("Aviso sobre regras dos jogos")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Muitos jogos competitivos para celular — incluindo o Free Fire — tratam o uso " +
                        "de teclado e mouse mapeados como vantagem não permitida nos seus termos de " +
                        "uso, podendo aplicar penalidades à conta do jogador. Essa decisão é das " +
                        "desenvolvedoras do jogo, não deste app. Use por sua conta e risco e verifique " +
                        "sempre as regras atuais do jogo que for usar.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    showAction: Boolean = true
) {
    NeonCard(accentColor = if (granted) NeonBlue else NeonPurple) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            StatusPill(if (granted) "Concedida" else "Pendente", if (granted) PillStatus.ON else PillStatus.WARNING)
        }
        Spacer(Modifier.height(8.dp))
        Text(description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (!granted && showAction) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun BulletText(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("•  ", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(6.dp))
}
