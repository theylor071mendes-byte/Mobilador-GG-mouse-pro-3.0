package com.mobiladorprox.app.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobiladorprox.app.service.MapperAccessibilityService
import com.mobiladorprox.app.service.OverlayService
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.components.GlowButton
import com.mobiladorprox.app.ui.components.PillStatus
import com.mobiladorprox.app.ui.components.StatusPill
import com.mobiladorprox.app.ui.theme.BackgroundBlack
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.NeonPurple
import com.mobiladorprox.app.ui.theme.SurfaceElevated
import com.mobiladorprox.app.ui.theme.TextSecondary
import com.mobiladorprox.app.util.DeviceInfoProvider

@Composable
fun HomeScreen(factory: ViewModelFactory, onAddGame: () -> Unit) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val selectedGame by viewModel.selectedGame.collectAsStateWithLifecycle()

    var accessibilityEnabled by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(false) }
    var keyboardConnected by remember { mutableStateOf(false) }
    var mouseConnected by remember { mutableStateOf(false) }

    fun refreshStatus() {
        accessibilityEnabled = DeviceInfoProvider.isAccessibilityServiceEnabled(
            context, MapperAccessibilityService::class.java.name
        )
        overlayGranted = DeviceInfoProvider.canDrawOverlays(context)
        val connection = DeviceInfoProvider.getConnectionInfo(context)
        keyboardConnected = connection.physicalKeyboardConnected
        mouseConnected = connection.mouseConnected
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

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Jogos", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(
                    if (mouseConnected) "Mouse conectado" else "Mouse desconectado",
                    if (mouseConnected) PillStatus.ON else PillStatus.OFF
                )
                Spacer(Modifier.height(6.dp))
                StatusPill(
                    if (keyboardConnected) "Teclado conectado" else "Teclado desconectado",
                    if (keyboardConnected) PillStatus.ON else PillStatus.OFF
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        GlowButton(text = "+ Adicionar jogo", onClick = onAddGame)
        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val game = selectedGame
            if (game == null) {
                Text(
                    "Nenhum jogo selecionado.\nToque em \"+ Adicionar jogo\" para escolher um aplicativo instalado.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(32.dp)
                )
            } else {
                GameCoverCard(
                    packageName = game.packageName,
                    label = game.label,
                    startLabel = when {
                        !accessibilityEnabled -> "ATIVAR ACESSIBILIDADE"
                        !overlayGranted -> "PERMITIR SOBREPOSIÇÃO"
                        else -> "INICIAR JOGO"
                    },
                    onStart = {
                        when {
                            !accessibilityEnabled -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            !overlayGranted -> overlayPermissionLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            )
                            else -> {
                                val serviceIntent = Intent(context, OverlayService::class.java).apply {
                                    putExtra(OverlayService.EXTRA_PROFILE_ID, game.profileId)
                                }
                                ContextCompat.startForegroundService(context, serviceIntent)
                                context.packageManager.getLaunchIntentForPackage(game.packageName)?.let {
                                    context.startActivity(it)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GameCoverCard(
    packageName: String,
    label: String,
    startLabel: String,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        icon = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(256, 256).asImageBitmap()
        }.getOrNull()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(width = 220.dp, height = 300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(SurfaceElevated, BackgroundBlack)))
                .background(Brush.radialGradient(listOf(NeonPurple.copy(alpha = 0.25f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    modifier = Modifier.size(140.dp).clip(RoundedCornerShape(28.dp))
                )
            } else {
                Text(label.take(2).uppercase(), color = NeonBlue, style = MaterialTheme.typography.headlineLarge)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        GlowButton(text = startLabel, onClick = onStart, modifier = Modifier.padding(horizontal = 24.dp))
    }
}
