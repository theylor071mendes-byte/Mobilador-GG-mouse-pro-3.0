package com.mobiladorprox.app.ui.inputtest

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobiladorprox.app.ui.components.NeonCard
import com.mobiladorprox.app.ui.components.SectionTitle
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.NeonPurple
import com.mobiladorprox.app.ui.theme.StatusGreen
import com.mobiladorprox.app.ui.theme.SurfaceElevated
import com.mobiladorprox.app.ui.theme.TextSecondary
import com.mobiladorprox.app.util.DeviceInfoProvider

/**
 * Tela de diagnóstico independente do mapeador: confirma, com as próprias
 * APIs do Android, que o teclado físico e o mouse conectados estão sendo
 * reconhecidos de verdade — mostra ao vivo cada tecla pressionada e cada
 * clique do mouse, sem passar pelo Serviço de Acessibilidade nem desenhar
 * nada por cima de outros apps (é um teste local, dentro do próprio app).
 */
@Composable
fun InputTestScreen() {
    val context = LocalContext.current
    val connection = remember { DeviceInfoProvider.getConnectionInfo(context) }

    val focusRequester = remember { FocusRequester() }
    var lastKeyLabel by remember { mutableStateOf("Nenhuma tecla ainda") }
    val pressedKeys = remember { mutableStateMapOf<Int, String>() }
    var totalKeyPresses by remember { mutableIntStateOf(0) }

    var leftDown by remember { mutableStateOf(false) }
    var rightDown by remember { mutableStateOf(false) }
    var middleDown by remember { mutableStateOf(false) }
    var totalClicks by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val code = event.key.nativeKeyCode
                val label = keyLabelFor(code)
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (pressedKeys[code] == null) totalKeyPresses++
                        pressedKeys[code] = label
                        lastKeyLabel = label
                    }
                    KeyEventType.KeyUp -> pressedKeys.remove(code)
                    else -> Unit
                }
                true
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val wasAnyDown = leftDown || rightDown || middleDown
                        val buttons = event.buttons
                        leftDown = buttons.isPrimaryPressed
                        rightDown = buttons.isSecondaryPressed
                        middleDown = buttons.isTertiaryPressed
                        val isAnyDown = leftDown || rightDown || middleDown
                        if (isAnyDown && !wasAnyDown) totalClicks++
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle(
                "Teste de Teclado e Mouse",
                "Toque na tela para focar o teste e pressione teclas ou clique com o mouse"
            )

            NeonCard {
                Text("Conexão detectada pelo Android", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                ConnectionRow("Teclado físico (USB/Bluetooth)", connection.physicalKeyboardConnected)
                Spacer(Modifier.height(6.dp))
                ConnectionRow("Mouse", connection.mouseConnected)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Isto é só o que o Android reporta como hardware conectado; o teste ao vivo abaixo " +
                        "confirma se as teclas e cliques estão realmente chegando ao app.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            NeonCard {
                Text("Analógico (WASD) ao vivo", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                WasdGrid(pressedKeys.keys)
                Spacer(Modifier.height(12.dp))
                Text("Última tecla: $lastKeyLabel", color = NeonBlue, style = MaterialTheme.typography.bodyMedium)
                Text("Total de teclas pressionadas: $totalKeyPresses", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }

            NeonCard {
                Text("Cliques do mouse ao vivo", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MouseButtonChip("Esquerdo", leftDown, Modifier.weight(1f))
                    MouseButtonChip("Direito", rightDown, Modifier.weight(1f))
                    MouseButtonChip("Meio", middleDown, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("Total de cliques: $totalClicks", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }

            Text(
                "Se nada acender ao digitar ou clicar, toque uma vez nesta tela para garantir o foco e " +
                    "confirme que o teclado/mouse estão realmente pareados nas configurações do Android.",
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ConnectionRow(label: String, connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(if (connected) StatusGreen else TextSecondary)
        )
        Text(
            if (connected) "$label — detectado" else "$label — não detectado",
            color = if (connected) Color.White else TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun WasdGrid(pressedCodes: Set<Int>) {
    val w = android.view.KeyEvent.KEYCODE_W
    val a = android.view.KeyEvent.KEYCODE_A
    val s = android.view.KeyEvent.KEYCODE_S
    val d = android.view.KeyEvent.KEYCODE_D

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WasdKey("W", w in pressedCodes)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WasdKey("A", a in pressedCodes)
            WasdKey("S", s in pressedCodes)
            WasdKey("D", d in pressedCodes)
        }
    }
}

@Composable
private fun WasdKey(label: String, pressed: Boolean) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) NeonPurple else SurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (pressed) Color.White else TextSecondary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MouseButtonChip(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) StatusGreen.copy(alpha = 0.25f) else SurfaceElevated)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (active) StatusGreen else TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun keyLabelFor(code: Int): String =
    android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
