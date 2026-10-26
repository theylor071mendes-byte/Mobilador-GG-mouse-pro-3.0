package com.mobiladorprox.app.ui.keymaps

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobiladorprox.app.data.db.entity.KeyBindingEntity
import com.mobiladorprox.app.data.model.GestureType
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.components.GlowButton
import com.mobiladorprox.app.ui.components.NeonCard
import com.mobiladorprox.app.ui.components.SectionTitle
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.NeonPurple
import com.mobiladorprox.app.ui.theme.SurfaceDark
import com.mobiladorprox.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun KeymapEditorScreen(factory: ViewModelFactory, profileId: Long, onBack: () -> Unit) {
    val viewModel: KeymapEditorViewModel = viewModel(factory = factory)
    LaunchedEffect(profileId) { viewModel.load(profileId) }

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val profilesForGame by viewModel.profilesForGame.collectAsStateWithLifecycle()
    val bindings by viewModel.bindings.collectAsStateWithLifecycle()
    val unsaved by viewModel.unsavedChanges.collectAsStateWithLifecycle()

    var addingBinding by remember { mutableStateOf(false) }
    var editingBinding by remember { mutableStateOf<KeyBindingEntity?>(null) }
    var showLoadProfile by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showNewProfile by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(profile?.gameLabel ?: "Editar Keymaps") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(profile?.profileName ?: "", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    if (unsaved) {
                        Text("Alterações não salvas", color = NeonBlue, style = MaterialTheme.typography.labelLarge)
                    }
                }
                TextButton(onClick = { showNewProfile = true }) { Text("+ Perfil") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Arraste os botões para a posição ideal na tela. Toque em um botão para renomear, trocar a tecla ou excluir.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))

            // Prévia da tela do jogo (paisagem), com os controles arrastáveis
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF101018))
                    .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            ) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()
                val chipSize = 40.dp

                bindings.forEach { binding ->
                    var xPercent by remember(binding.id) { mutableStateOf(binding.xPercent) }
                    var yPercent by remember(binding.id) { mutableStateOf(binding.yPercent) }

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (xPercent * widthPx).roundToInt() - (chipSize.toPx() / 2).roundToInt(),
                                    (yPercent * heightPx).roundToInt() - (chipSize.toPx() / 2).roundToInt()
                                )
                            }
                            .size(chipSize)
                            .clip(CircleShape)
                            .background(NeonPurple.copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .pointerInput(binding.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    xPercent = (xPercent + dragAmount.x / widthPx).coerceIn(0f, 1f)
                                    yPercent = (yPercent + dragAmount.y / heightPx).coerceIn(0f, 1f)
                                    viewModel.updateBindingPosition(binding.id, xPercent, yPercent)
                                }
                            }
                            .clickable { editingBinding = binding },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            binding.label.take(2).uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { addingBinding = true }) { Text("+ Adicionar botão") }

            Spacer(Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(bindings, key = { it.id }) { binding ->
                    BindingListRow(binding, onClick = { editingBinding = binding })
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showLoadProfile = true }, modifier = Modifier.weight(1f)) {
                    Text("Carregar perfil")
                }
                TextButton(onClick = { showRestoreConfirm = true }, modifier = Modifier.weight(1f)) {
                    Text("Restaurar padrão")
                }
            }
            Spacer(Modifier.height(8.dp))
            GlowButton(text = "SALVAR CONFIGURAÇÃO", onClick = { viewModel.saveConfiguration() })
        }
    }

    if (addingBinding) {
        AddBindingDialog(
            onDismiss = { addingBinding = false },
            onConfirm = { label, keyCode, gestureType ->
                viewModel.addBinding(label, keyCode, gestureType)
                addingBinding = false
            }
        )
    }

    editingBinding?.let { binding ->
        EditBindingDialog(
            binding = binding,
            onDismiss = { editingBinding = null },
            onRename = { viewModel.renameBinding(binding.id, it) },
            onChangeKey = { viewModel.updateBindingKey(binding.id, it) },
            onDelete = { viewModel.removeBinding(binding.id); editingBinding = null }
        )
    }

    if (showLoadProfile) {
        AlertDialog(
            onDismissRequest = { showLoadProfile = false },
            title = { Text("Carregar perfil") },
            text = {
                Column {
                    profilesForGame.forEach { p ->
                        TextButton(onClick = {
                            viewModel.load(p.id)
                            showLoadProfile = false
                        }) { Text(p.profileName) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLoadProfile = false }) { Text("Fechar") } }
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restaurar padrão?") },
            text = { Text("Os vínculos atuais deste perfil serão substituídos pelo conjunto padrão. Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = { viewModel.restoreDefaults(); showRestoreConfirm = false }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancelar") } }
        )
    }

    if (showNewProfile) {
        var name by remember { mutableStateOf("Novo perfil") }
        AlertDialog(
            onDismissRequest = { showNewProfile = false },
            title = { Text("Novo perfil para este jogo") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createNewProfile(name) { viewModel.load(it) }
                    showNewProfile = false
                }) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { showNewProfile = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun BindingListRow(binding: KeyBindingEntity, onClick: () -> Unit) {
    val gestureType = runCatching { GestureType.valueOf(binding.gestureType) }.getOrNull()
    val keyName = remember(binding.keyCode) { KeyEvent.keyCodeToString(binding.keyCode).removePrefix("KEYCODE_") }
    NeonCard(modifier = Modifier.clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(binding.label, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(gestureType?.displayName ?: binding.gestureType, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(keyName, color = NeonBlue, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EditBindingDialog(
    binding: KeyBindingEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onChangeKey: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var label by remember { mutableStateOf(binding.label) }
    var capturing by remember { mutableStateOf(false) }
    var newKeyCode by remember { mutableStateOf(binding.keyCode) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar botão") },
        text = {
            Column {
                OutlinedTextField(value = label, onValueChange = { label = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                if (capturing) {
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    newKeyCode = event.nativeKeyEvent.keyCode
                                    capturing = false
                                    true
                                } else false
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("Pressione a tecla…", color = TextSecondary) }
                } else {
                    TextButton(onClick = { capturing = true }) {
                        Text("Tecla: " + KeyEvent.keyCodeToString(newKeyCode).removePrefix("KEYCODE_"))
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onDelete) { Text("Excluir botão") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (label.isNotBlank()) onRename(label.trim())
                if (newKeyCode != binding.keyCode) onChangeKey(newKeyCode)
                onDismiss()
            }) { Text("Pronto") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AddBindingDialog(onDismiss: () -> Unit, onConfirm: (String, Int, GestureType) -> Unit) {
    var label by remember { mutableStateOf("") }
    var gestureType by remember { mutableStateOf(GestureType.TAP) }
    var capturedKeyCode by remember { mutableStateOf<Int?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo botão") },
        text = {
            Column {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Nome da ação") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(GestureType.TAP, GestureType.HOLD, GestureType.LOOK_TOGGLE).forEach { type ->
                        FilterChip(selected = gestureType == type, onClick = { gestureType = type }, label = { Text(type.displayName) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (capturing) {
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    capturedKeyCode = event.nativeKeyEvent.keyCode
                                    capturing = false
                                    true
                                } else false
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("Pressione a tecla ou clique do mouse…", color = TextSecondary) }
                } else {
                    val keyLabel = capturedKeyCode?.let { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") } ?: "Nenhuma tecla definida"
                    TextButton(onClick = { capturing = true }) { Text("Definir tecla: $keyLabel") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Dica: o clique direito do mouse costuma ser reconhecido como \"BACK\" pelo Android.",
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
