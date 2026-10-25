package com.mobiladorprox.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.home.HomeScreen
import com.mobiladorprox.app.ui.navigation.Routes
import com.mobiladorprox.app.ui.optimization.OptimizationScreen
import com.mobiladorprox.app.ui.permissions.PermissionsScreen
import com.mobiladorprox.app.ui.profiles.ProfileEditorScreen
import com.mobiladorprox.app.ui.profiles.ProfilesScreen
import com.mobiladorprox.app.ui.theme.MobiladorProXTheme
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.SurfaceDark
import com.mobiladorprox.app.ui.theme.TextSecondary

/**
 * Activity única do app. Hospeda toda a interface em Jetpack Compose e
 * navega entre as telas (Início, Meus Perfis, Otimização e Permissões)
 * usando Navigation Compose. O painel flutuante do mapeador roda em um
 * serviço separado (OverlayService), fora desta Activity.
 */
class MainActivity : ComponentActivity() {

    private val factory by lazy {
        val app = application as MobiladorApplication
        ViewModelFactory(app.repository, app.preferences)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Resultado tratado de forma transparente: se negado, a notificação
           do serviço em primeiro plano simplesmente não aparece; o mapeador
           continua funcionando normalmente. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            MobiladorProXTheme {
                MobiladorApp(factory)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Início", Icons.Filled.Home),
    BottomDestination(Routes.PROFILES, "Perfis", Icons.Filled.Build),
    BottomDestination(Routes.OPTIMIZATION, "Otimização", Icons.Filled.Speed),
    BottomDestination(Routes.PERMISSIONS, "Permissões", Icons.Filled.Shield)
)

@androidx.compose.runtime.Composable
private fun MobiladorApp(factory: ViewModelFactory) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                bottomDestinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonBlue,
                            selectedTextColor = NeonBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = SurfaceDark
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    factory = factory,
                    onNavigateToPermissions = {
                        navController.navigate(Routes.PERMISSIONS) { launchSingleTop = true }
                    },
                    onNavigateToProfiles = {
                        navController.navigate(Routes.PROFILES) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.PROFILES) {
                ProfilesScreen(
                    factory = factory,
                    onEditProfile = { id ->
                        navController.navigate(Routes.profileEditor(id))
                    }
                )
            }
            composable(
                route = Routes.PROFILE_EDITOR,
                arguments = listOf(navArgument("profileId") { type = NavType.LongType })
            ) { entry ->
                val profileId = entry.arguments?.getLong("profileId") ?: return@composable
                ProfileEditorScreen(
                    factory = factory,
                    profileId = profileId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.OPTIMIZATION) {
                OptimizationScreen()
            }
            composable(Routes.PERMISSIONS) {
                PermissionsScreen()
            }
        }
    }
}
