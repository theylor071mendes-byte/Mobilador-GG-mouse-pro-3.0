package com.mobiladorprox.app.ui.profiles

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.GestureType
import com.mobiladorprox.app.data.model.PresetMode
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.components.LabeledSlider
import com.mobiladorprox.app.ui.components.NeonCard
import com.mobiladorprox.app.ui.components.SectionTitle
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.NeonPurple
import com.mobiladorprox.app.ui.theme.SurfaceDark
import com.mobiladorprox.app.ui.theme.TextSecondary

@Composable
fun ProfileEditorScreen(factory: ViewModelFactory, profileId: Long, onBack: () -> Unit) {
    val viewModel: ProfileEditorViewModel = viewModel(factory = factory)
    LaunchedEffect(profileId) { viewModel.load(profileId) }

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val bindings by viewModel.bindings.collectAsStateWithLifecycle()

    var editingBinding by remember { mutableStateOf<KeyBindingEntity?>(null) }
    var addingBinding by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(profile?.name ?: "Editar perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        val currentProfile = profile
        if (currentProfile == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionTitle(
                    "Predefinições de sensibilidade",
                    "Pontos de partida configuráveis — não é promessa de desempenho"
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PresetMode.PRECISAO, PresetMode.RUSH, PresetMode.EQUILIBRIO, PresetMode.LONGA_DISTANCIA)
                        .forEach { preset ->
                            val currentPreset = runCatching { PresetMode.valueOf(currentProfile.presetMode) }.getOrNull()
                            FilterChip(
                                selected = currentPreset == preset,
                                onClick = { viewModel.applyPreset(preset) },
                                label = { Text(preset.displayName) }
                            )
                        }
                }
            }

            item { ProfileSlidersCard(currentProfile, viewModel) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionTitle("Vínculos de tecla", "Toque para alterar a tecla de cada ação")
                    TextButton(onClick = { addingBinding = true }) { Text("+ Adicionar") }
                }
            }

            items(bindings, key = { it.id }) { binding ->
                BindingRow(binding = binding, onChangeKey = { editingBinding = binding })
            }

            item {
                NeonCard(accentColor = NeonBlue) {
                    Text(
                        "Restaurar valores padrão apaga os vínculos atuais e recoloca a sensibilidade e os botões nas posições originais do tipo de jogo.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.restoreDefaults() }) { Text("Restaurar padrões deste perfil") }
                }
            }
        }
    }

    editingBinding?.let { binding ->
        KeyCaptureDialog(
            title = "Nova tecla para \"${binding.label}\"",
            onDismiss = { editingBinding = null },
            onKeyCaptured = { keyCode ->
                viewModel.updateBindingKey(binding, keyCode)
                editingBinding = null
            }
        )
    }

    if (addingBinding) {
        AddBindingDialog(
            onDismiss = { addingBinding = false },
            onConfirm = { label, keyCode, gestureType ->
                viewModel.addCustomBinding(label, keyCode, gestureType)
                addingBinding = false
            }
        )
    }
}

@Composable
private fun ProfileSlidersCard(
    profile: com.mobiladorprox.app.data.db.entity.GameProfileEntity,
    viewModel: ProfileEditorViewModel
) {
    var sensH by remember(profile.id) { mutableStateOf(profile.sensitivityHorizontal) }
    var sensV by remember(profile.id) { mutableStateOf(profile.sensitivityVertical) }
    var cursorSpeed by remember(profile.id) { mutableStateOf(profile.cursorSpeed) }
    var dpi by remember(profile.id) { mutableStateOf(profile.dpiMultiplier) }

    // Mantém os sliders sincronizados quando o perfil muda por fora (ex.: ao aplicar um preset).
    LaunchedEffect(profile.sensitivityHorizontal, profile.sensitivityVertical, profile.cursorSpeed, profile.dpiMultiplier) {
        sensH = profile.sensitivityHorizontal
        sensV = profile.sensitivityVertical
        cursorSpeed = profile.cursorSpeed
        dpi = profile.dpiMultiplier
    }

    fun commit() = viewModel.updateSettings(sensH, sensV, cursorSpeed, dpi)

    NeonCard {
        SectionTitle("Ajuste fino")
        Spacer(Modifier.height(12.dp))
        LabeledSlider(
            label = "Sensibilidade horizontal",
            value = sensH,
            valueRange = 0.05f..2f,
            onValueChange = { sensH = it },
            onValueChangeFinished = { commit() }
        )
        Spacer(Modifier.height(10.dp))
        LabeledSlider(
            label = "Sensibilidade vertical",
            value = sensV,
            valueRange = 0.05f..2f,
            onValueChange = { sensV = it },
            onValueChangeFinished = { commit() }
        )
        Spacer(Modifier.height(10.dp))
        LabeledSlider(
            label = "Velocidade do cursor",
            value = cursorSpeed,
            valueRange = 0.2f..2.5f,
            onValueChange = { cursorSpeed = it },
            onValueChangeFinished = { commit() }
        )
        Spacer(Modifier.height(10.dp))
        LabeledSlider(
            label = "\"DPI\" (multiplicador de ganho por software)",
            value = dpi,
            valueRange = 0.5f..3f,
            onValueChange = { dpi = it },
            onValueChangeFinished = { commit() }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Este ajuste não altera o hardware do mouse — é um multiplicador aplicado ao movimento relativo antes de virar gesto de câmera.",
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun BindingRow(binding: KeyBindingEntity, onChangeKey: () -> Unit) {
    val gestureType = runCatching { GestureType.valueOf(binding.gestureType) }.getOrNull()
    val keyName = remember(binding.keyCode) {
        KeyEvent.keyCodeToString(binding.keyCode).removePrefix("KEYCODE_")
    }
    NeonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(binding.label, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    gestureType?.displayName ?: binding.gestureType,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(10.dp),
                onClick = onChangeKey
            ) {
                Text(
                    keyName,
                    color = NeonBlue,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/**
 * Diálogo que captura a próxima tecla física pressionada (teclado USB/Bluetooth)
 * usando a API pública de eventos de teclado do Compose sobre uma área com foco.
 */
@Composable
private fun KeyCaptureDialog(title: String, onDismiss: () -> Unit, onKeyCaptured: (Int) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            onKeyCaptured(event.nativeKeyEvent.keyCode)
                            true
                        } else {
                            false
                        }
                    },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("Pressione a tecla desejada no teclado conectado…", color = TextSecondary)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AddBindingDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, keyCode: Int, gestureType: GestureType) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var gestureType by remember { mutableStateOf(GestureType.TAP) }
    var capturedKeyCode by remember { mutableStateOf<Int?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo vínculo de tecla") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nome da ação") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Text("Tipo de gesto", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(GestureType.TAP, GestureType.HOLD, GestureType.LOOK_TOGGLE).forEach { type ->
                        FilterChip(
                            selected = gestureType == type,
                            onClick = { gestureType = type },
                            label = { Text(type.displayName) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (capturing) {
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    capturedKeyCode = event.nativeKeyEvent.keyCode
                                    capturing = false
                                    true
                                } else false
                            },
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text("Pressione a tecla…", color = TextSecondary)
                    }
                } else {
                    val keyLabel = capturedKeyCode?.let {
                        KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_")
                    } ?: "Nenhuma tecla definida"
                    TextButton(onClick = { capturing = true }) { Text("Definir tecla: $keyLabel") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "A posição na tela desse vínculo pode ser calibrada depois pelo painel flutuante, no \"Modo edição\".",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        confirmButton = {
            val keyCode = capturedKeyCode
            TextButton(
                onClick = { if (label.isNotBlank() && keyCode != null) onConfirm(label.trim(), keyCode, gestureType) },
                enabled = label.isNotBlank() && capturedKeyCode != null
            ) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
