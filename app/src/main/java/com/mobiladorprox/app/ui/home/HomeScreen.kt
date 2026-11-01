package com.mobiladorprox.app.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.components.PillStatus
import com.mobiladorprox.app.ui.components.StatusPill
import com.mobiladorprox.app.ui.theme.BackgroundBlack
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.NeonPurple
import com.mobiladorprox.app.ui.theme.NeonPurpleDim
import com.mobiladorprox.app.ui.theme.StatusGreen
import com.mobiladorprox.app.ui.theme.SurfaceDark
import com.mobiladorprox.app.ui.theme.SurfaceElevated
import com.mobiladorprox.app.ui.theme.TextSecondary
import com.mobiladorprox.app.util.DeviceInfoProvider

@Composable
fun HomeScreen(factory: ViewModelFactory, onAddGame: () -> Unit) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var keyboardConnected by remember { mutableStateOf(false) }
    var mouseConnected by remember { mutableStateOf(false) }

    fun refreshStatus() {
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

    // perfil aguardando confirmação de deleção
    var pendingDeleteProfile by remember { mutableStateOf<GameProfileEntity?>(null) }

    Scaffold(
        containerColor = BackgroundBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddGame,
                containerColor = NeonPurple,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp, 12.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar jogo")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ── Header ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        "Mapeador",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Mobilador",
                        style = MaterialTheme.typography.headlineSmall,
                        color = NeonBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        if (mouseConnected) "Mouse" else "Sem mouse",
                        if (mouseConnected) PillStatus.ON else PillStatus.OFF
                    )
                    StatusPill(
                        if (keyboardConnected) "Teclado" else "Sem teclado",
                        if (keyboardConnected) PillStatus.ON else PillStatus.OFF
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Subtítulo dinâmico ─────────────────────────────────
            val gameCount = uiState.profiles.distinctBy { it.packageName }.size
            Text(
                when (gameCount) {
                    0 -> "Toque em + para adicionar seu primeiro jogo"
                    1 -> "1 jogo configurado"
                    else -> "$gameCount jogos configurados"
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // ── Grid de jogos ──────────────────────────────────────
            if (uiState.profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.profiles, key = { it.id }) { profile ->
                        val isActive = profile.id == uiState.activeProfileId
                        GameCard(
                            profile = profile,
                            isActive = isActive,
                            onLaunch = {
                                viewModel.launchGame(
                                    context = context,
                                    profile = profile,
                                    onNeedAccessibility = {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                    onNeedOverlay = {
                                        overlayPermissionLauncher.launch(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    }
                                )
                            },
                            onSelect = { viewModel.setActiveProfile(profile) },
                            onDelete = { pendingDeleteProfile = profile }
                        )
                    }
                }
            }
        }
    }

    // ── Diálogo de confirmação de exclusão ─────────────────────────
    pendingDeleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDeleteProfile = null },
            containerColor = SurfaceElevated,
            titleContentColor = Color.White,
            textContentColor = TextSecondary,
            title = { Text("Remover perfil?") },
            text = {
                Text(
                    "\"${profile.profileName}\" (${profile.gameLabel}) será apagado permanentemente."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile.id)
                    pendingDeleteProfile = null
                }) {
                    Text("Remover", color = NeonPurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProfile = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card de jogo estilo GG Mouse Pro 3
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameCard(
    profile: GameProfileEntity,
    isActive: Boolean,
    onLaunch: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var icon by remember(profile.packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(profile.packageName) {
        icon = runCatching {
            context.packageManager.getApplicationIcon(profile.packageName)
                .toBitmap(128, 128).asImageBitmap()
        }.getOrNull()
    }

    val borderColor by animateColorAsState(
        if (isActive) NeonPurple else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(300),
        label = "cardBorder"
    )
    val glowAlpha by animateFloatAsState(
        if (isActive) 0.35f else 0f,
        animationSpec = tween(300),
        label = "cardGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceElevated)
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect)
    ) {
        // Glow de fundo quando ativo
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(NeonPurple.copy(alpha = glowAlpha), Color.Transparent)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Ícone do jogo ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                val bm = icon
                if (bm != null) {
                    Image(
                        bitmap = bm,
                        contentDescription = profile.gameLabel,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Text(
                        profile.gameLabel.take(2).uppercase(),
                        color = NeonBlue,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Nome do jogo ───────────────────────────────────────
            Text(
                profile.gameLabel,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // ── Nome do perfil ─────────────────────────────────────
            Text(
                profile.profileName,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.weight(1f))

            // ── Badge "Ativo" ──────────────────────────────────────
            AnimatedVisibility(
                visible = isActive,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(StatusGreen.copy(alpha = 0.18f))
                        .border(1.dp, StatusGreen, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text("● Ativo", color = StatusGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Botões de ação ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Botão Excluir
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remover perfil",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Botão Iniciar (ocupa o resto)
                PlayButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    onClick = onLaunch
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(100), label = "playBtnScale")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(NeonPurple, NeonBlue)))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Iniciar", tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Iniciar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(SurfaceElevated)
                .border(1.dp, NeonPurpleDim, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Nenhum jogo adicionado",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Toque em + para escolher um jogo instalado\ne criar o perfil de keymap.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
    }
}
