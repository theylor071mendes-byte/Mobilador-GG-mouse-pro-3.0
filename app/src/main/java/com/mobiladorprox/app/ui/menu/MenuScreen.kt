package com.mobiladorprox.app.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mobiladorprox.app.ui.components.NeonCard
import com.mobiladorprox.app.ui.components.SectionTitle
import com.mobiladorprox.app.ui.navigation.Routes
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.NeonPurple
import com.mobiladorprox.app.ui.theme.TextSecondary

private const val ROUTE_KEYMAPS = "__keymaps__"
private const val ROUTE_MOUSE_SETTINGS = "__mouse_settings__"

private data class MenuItem(val title: String, val description: String, val icon: ImageVector, val route: String)

private val menuItems = listOf(
    MenuItem("Editar Keymaps", "Posicione botões, vincule teclas e cliques do mouse, dentro do jogo", Icons.Filled.Edit, ROUTE_KEYMAPS),
    MenuItem("Otimização", "Informações reais do aparelho e atalhos de ajuste", Icons.Filled.Tune, Routes.OPTIMIZATION),
    MenuItem("Configurações de desempenho", "Perfis ALTO / MÉDIO / BAIXO / MUITO BAIXO", Icons.Filled.Speed, Routes.PERFORMANCE),
    MenuItem("Configurações do mouse", "Sensibilidade e polling rate, dentro do jogo", Icons.Filled.Mouse, ROUTE_MOUSE_SETTINGS)
)

@Composable
fun MenuScreen(onNavigate: (route: String) -> Unit, onEditKeymaps: () -> Unit, onMouseSettings: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Menu", "Ajustes do mapeador e do aplicativo") }

        items(menuItems) { item ->
            NeonCard(
                modifier = Modifier.clickable {
                    when (item.route) {
                        ROUTE_KEYMAPS -> onEditKeymaps()
                        ROUTE_MOUSE_SETTINGS -> onMouseSettings()
                        else -> onNavigate(item.route)
                    }
                },
                accentColor = if (item.route == ROUTE_KEYMAPS || item.route == ROUTE_MOUSE_SETTINGS) NeonBlue else NeonPurple
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.icon, contentDescription = null, tint = NeonBlue)
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(item.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(item.description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
        }
    }
}
