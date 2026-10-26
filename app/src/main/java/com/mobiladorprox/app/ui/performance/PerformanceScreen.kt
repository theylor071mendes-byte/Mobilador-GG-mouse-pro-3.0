package com.mobiladorprox.app.ui.performance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobiladorprox.app.data.model.PerformanceProfile
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.components.NeonCard
import com.mobiladorprox.app.ui.components.SectionTitle
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.TextSecondary

@Composable
fun PerformanceScreen(factory: ViewModelFactory) {
    val viewModel: PerformanceViewModel = viewModel(factory = factory)
    val selected by viewModel.selectedProfile.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle(
                "Configurações de desempenho",
                "Ajusta apenas o que o app realmente controla — sem prometer eliminar tremor de mira"
            )
        }

        items(PerformanceProfile.entries) { profile ->
            val isSelected = selected == profile
            NeonCard(
                modifier = Modifier.clickable { viewModel.selectProfile(profile) },
                accentColor = if (isSelected) NeonBlue else com.mobiladorprox.app.ui.theme.NeonPurple
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.displayName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(profile.description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(
                        if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selecionado" else "Não selecionado",
                        tint = if (isSelected) NeonBlue else TextSecondary
                    )
                }
            }
        }

        item {
            NeonCard {
                Text(
                    "Esses perfis ajustam a suavização aplicada ao movimento da câmera (mouse) e os " +
                        "efeitos visuais do próprio aplicativo. Eles não alteram o processamento do jogo " +
                        "em si nem garantem mais quadros por segundo.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
