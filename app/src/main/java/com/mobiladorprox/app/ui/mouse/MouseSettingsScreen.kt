package com.mobiladorprox.app.ui.mouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobiladorprox.app.data.model.PollingRate
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.components.NeonCard
import com.mobiladorprox.app.ui.components.SectionTitle
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.NeonPurple
import com.mobiladorprox.app.ui.theme.TextSecondary

@Composable
fun MouseSettingsScreen(factory: ViewModelFactory) {
    val viewModel: MouseSettingsViewModel = viewModel(factory = factory)
    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
    val pollingRate by viewModel.pollingRate.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionTitle("Configurações do mouse", "Sensibilidade e taxa de resposta") }

        item {
            NeonCard {
                Text("Sensibilidade da seta", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { viewModel.setSensitivity((sensitivity - 0.25f).coerceIn(1f, 20f)) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Diminuir", tint = NeonBlue)
                    }
                    Text(
                        "%.2f".format(sensitivity),
                        color = NeonBlue,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = { viewModel.setSensitivity((sensitivity + 0.25f).coerceIn(1f, 20f)) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Aumentar", tint = NeonBlue)
                    }
                }
                Slider(
                    value = sensitivity,
                    onValueChange = { viewModel.setSensitivity(it) },
                    valueRange = 1f..20f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonBlue,
                        activeTrackColor = NeonPurple,
                        inactiveTrackColor = Color(0xFF34344A)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1.00", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                    Text("20.00", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Multiplica o movimento relativo do mouse durante a captura de ponteiro, antes de " +
                        "virar gesto de câmera. É um ajuste real, aplicado pelo próprio app.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        item {
            NeonCard {
                Text("Polling Rate", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PollingRate.entries.forEach { rate ->
                        FilterChip(
                            selected = pollingRate == rate,
                            onClick = { viewModel.setPollingRate(rate) },
                            label = { Text(rate.label) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "O Android não oferece uma forma pública de alterar a taxa de resposta (polling " +
                        "rate) de um mouse USB ou Bluetooth a partir de um app comum. Esta opção guarda " +
                        "sua preferência apenas como referência — a taxa real depende do firmware do seu " +
                        "mouse e, quando houver, do software do fabricante. Nenhuma alteração é simulada.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        item {
            TextButton(onClick = { viewModel.restoreDefaults() }) { Text("Restaurar configurações padrão do mouse") }
        }
    }
}
