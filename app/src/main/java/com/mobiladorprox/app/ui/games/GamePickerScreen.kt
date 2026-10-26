package com.mobiladorprox.app.ui.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobiladorprox.app.ui.components.NeonCard
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.SurfaceDark
import com.mobiladorprox.app.ui.theme.TextSecondary
import com.mobiladorprox.app.util.InstalledApp
import com.mobiladorprox.app.util.InstalledAppsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GamePickerScreen(onBack: () -> Unit, onGameSelected: (packageName: String, label: String) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { InstalledAppsProvider.listLaunchableApps(context) }
        loading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Adicionar jogo") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Toque em um aplicativo instalado para criar (ou abrir) o perfil de keymap dele.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(apps, key = { it.packageName }) { app ->
                NeonCard(modifier = Modifier.clickable { onGameSelected(app.packageName, app.label) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bitmap = app.icon
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = app.label,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(modifier = Modifier.size(44.dp))
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                        Text(app.label, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
