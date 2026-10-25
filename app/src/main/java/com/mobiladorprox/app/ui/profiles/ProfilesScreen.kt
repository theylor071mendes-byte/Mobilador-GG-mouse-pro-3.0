package com.mobiladorprox.app.ui.profiles

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobiladorprox.app.data.db.entity.GameProfileEntity
import com.mobiladorprox.app.data.model.GameType
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.components.NeonCard
import com.mobiladorprox.app.ui.components.SectionTitle
import com.mobiladorprox.app.ui.theme.NeonPurple
import com.mobiladorprox.app.ui.theme.TextSecondary

@Composable
fun ProfilesScreen(factory: ViewModelFactory, onEditProfile: (Long) -> Unit) {
    val viewModel: ProfilesViewModel = viewModel(factory = factory)
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<GameProfileEntity?>(null) }
    var duplicateTarget by remember { mutableStateOf<GameProfileEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<GameProfileEntity?>(null) }
    var restoreTarget by remember { mutableStateOf<GameProfileEntity?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }, containerColor = NeonPurple) {
                Icon(Icons.Filled.Add, contentDescription = "Novo perfil", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 20.dp,
                bottom = padding.calculateBottomPadding() + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionTitle("MINHAS CONFIGURAÇÕES", "Crie, edite e organize seus perfis de mapeamento")
            }

            items(profiles, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    onEdit = { onEditProfile(profile.id) },
                    onDuplicate = { duplicateTarget = profile },
                    onRename = { renameTarget = profile },
                    onDelete = { deleteTarget = profile },
                    onRestoreDefaults = { restoreTarget = profile }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateProfileDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, type ->
                viewModel.createProfile(name, type) { onEditProfile(it) }
                showCreateDialog = false
            }
        )
    }

    renameTarget?.let { profile ->
        TextInputDialog(
            title = "Renomear perfil",
            initialValue = profile.name,
            confirmLabel = "Renomear",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameProfile(profile.id, newName)
                renameTarget = null
            }
        )
    }

    duplicateTarget?.let { profile ->
        TextInputDialog(
            title = "Duplicar perfil",
            initialValue = "${profile.name} (cópia)",
            confirmLabel = "Duplicar",
            onDismiss = { duplicateTarget = null },
            onConfirm = { newName ->
                viewModel.duplicateProfile(profile.id, newName)
                duplicateTarget = null
            }
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Excluir \"${profile.name}\"?") },
            text = { Text("Essa ação remove o perfil e todos os seus vínculos de tecla. Não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile.id)
                    deleteTarget = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }

    restoreTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("Restaurar padrões de \"${profile.name}\"?") },
            text = { Text("Sensibilidade e posições dos botões voltam aos valores padrão do tipo de jogo.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreDefaults(profile.id)
                    restoreTarget = null
                }) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ProfileRow(
    profile: GameProfileEntity,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRestoreDefaults: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val gameTypeLabel = runCatching { GameType.valueOf(profile.gameType).displayName }.getOrDefault(profile.gameType)

    NeonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(gameTypeLabel, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                if (profile.isDefault) {
                    Text("Perfil padrão", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
            }
            Row {
                TextButton(onClick = onEdit) { Text("Editar") }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais ações", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menuExpanded = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Renomear") }, onClick = { menuExpanded = false; onRename() })
                        DropdownMenuItem(text = { Text("Restaurar padrão") }, onClick = { menuExpanded = false; onRestoreDefaults() })
                        DropdownMenuItem(text = { Text("Excluir") }, onClick = { menuExpanded = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateProfileDialog(onDismiss: () -> Unit, onConfirm: (String, GameType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(GameType.FREE_FIRE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo perfil") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do perfil") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Tipo de jogo", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameType.values().forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedType) },
                enabled = name.isNotBlank()
            ) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
