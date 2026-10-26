package com.mobiladorprox.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * Lista os aplicativos instalados que podem ser abertos pelo usuário
 * (têm uma tela de lançamento), usando a API pública de resolução de
 * intents do Android. Requer a declaração <queries> no AndroidManifest
 * para enxergar outros apps a partir do Android 11 — sem isso, apenas o
 * próprio app apareceria na lista.
 */
object InstalledAppsProvider {

    fun listLaunchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L)) }
                .getOrDefault(emptyList())
        } else {
            @Suppress("DEPRECATION")
            runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        }

        return resolved
            .asSequence()
            .filter { it.activityInfo != null && it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
                val icon = runCatching {
                    info.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
                }.getOrNull()
                InstalledApp(pkg, label, icon)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
