package com.mobiladorprox.app.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobiladorprox.app.service.MapperAccessibilityService
import com.mobiladorprox.app.service.OverlayService
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.components.GlowButton
import com.mobiladorprox.app.ui.components.NeonCard
import com.mobiladorprox.app.ui.components.PillStatus
import com.mobiladorprox.app.ui.components.SectionTitle
import com.mobiladorprox.app.ui.components.StatusPill
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.TextSecondary
import com.mobiladorprox.app.util.DeviceInfoProvider

@Composable
fun HomeScreen(
    factory: ViewModelFactory,
    onNavigateToPermissions: () -> Unit,
    onNavigateToProfiles: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()

    var accessibilityEnabled by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(false) }
    var overlayRunning by remember { mutableStateOf(OverlayService.isRunning) }

    fun refreshStatus() {
        accessibilityEnabled = DeviceInfoProvider.isAccessibilityServiceEnabled(
            context, MapperAccessibilityService::class.java.name
        )
        overlayGranted = DeviceInfoProvider.canDrawOverlays(context)
        overlayRunning = OverlayService.isRunning
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshStatus()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshStatus() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    "MOBILADOR PRO X",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeonBlue
                )
                Text(
                    "Mapeamento de teclado, mouse e controle para Free Fire e outros jogos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item {
            NeonCard {
                SectionTitle("Status do sistema")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        if (accessibilityEnabled) "Acessibilidade ON" else "Acessibilidade OFF",
                        if (accessibilityEnabled) PillStatus.ON else PillStatus.OFF
                    )
                    StatusPill(
                        if (overlayGranted) "Overlay OK" else "Overlay pendente",
                        if (overlayGranted) PillStatus.ON else PillStatus.WARNING
                    )
                }
                Spacer(Modifier.height(8.dp))
                StatusPill(
                    if (overlayRunning) "Mapeador em execução" else "Mapeador parado",
                    if (overlayRunning) PillStatus.ON else PillStatus.OFF
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Perfil selecionado: ${activeProfile?.name ?: "nenhum"}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            GlowButton(
                text = if (overlayRunning) "PARAR MAPEADOR" else "INICIAR MAPEADOR",
                onClick = {
                    when {
                        overlayRunning -> {
                            context.startService(Intent(context, OverlayService::class.java).apply {
                                action = OverlayService.ACTION_STOP
                            })
                        }
                        !accessibilityEnabled -> {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        !overlayGranted -> {
                            overlayPermissionLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                        activeProfile == null -> {
                            onNavigateToProfiles()
                        }
                        else -> {
                            val intent = Intent(context, OverlayService::class.java).apply {
                                putExtra(OverlayService.EXTRA_PROFILE_ID, activeProfile!!.id)
                            }
                            ContextCompat.startForegroundService(context, intent)
                        }
                    }
                }
            )
        }

        if (!accessibilityEnabled || !overlayGranted) {
            item {
                NeonCard(accentColor = NeonBlue) {
                    SectionTitle("Antes de começar")
                    Spacer(Modifier.height(8.dp))
                    if (!accessibilityEnabled) {
                        Text(
                            "1. Toque em INICIAR para abrir Acessibilidade, encontre \"MOBILADOR PRO X\" na lista e ative.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (!overlayGranted) {
                        Text(
                            "2. Depois, toque novamente para permitir que o app apareça sobre outros aplicativos.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Veja a tela Permissões para entender exatamente para que cada uma serve.",
                        color = NeonBlue,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item {
            SectionTitle("Perfis", "Toque para escolher qual perfil o mapeador vai usar")
        }

        items(profiles) { p ->
            NeonCard(
                modifier = Modifier.clickable { viewModel.selectProfile(p.id) },
                accentColor = if (activeProfile?.id == p.id) NeonBlue else MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(p.name, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                        val gameTypeLabel = runCatching {
                            com.mobiladorprox.app.data.model.GameType.valueOf(p.gameType).displayName
                        }.getOrDefault(p.gameType)
                        Text(gameTypeLabel, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (activeProfile?.id == p.id) {
                        StatusPill("Selecionado", PillStatus.ON)
                    } else {
                        StatusPill("Selecionar", PillStatus.WARNING)
                    }
                }
            }
        }
    }
}
