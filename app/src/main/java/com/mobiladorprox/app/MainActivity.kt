package com.mobiladorprox.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobiladorprox.app.ui.ViewModelFactory
import com.mobiladorprox.app.ui.games.GamePickerScreen
import com.mobiladorprox.app.ui.home.HomeScreen
import com.mobiladorprox.app.ui.home.HomeViewModel
import com.mobiladorprox.app.ui.keymaps.KeymapEditorScreen
import com.mobiladorprox.app.ui.menu.MenuScreen
import com.mobiladorprox.app.ui.mouse.MouseSettingsScreen
import com.mobiladorprox.app.ui.navigation.Routes
import com.mobiladorprox.app.ui.optimization.OptimizationScreen
import com.mobiladorprox.app.ui.performance.PerformanceScreen
import com.mobiladorprox.app.ui.theme.MobiladorProXTheme
import com.mobiladorprox.app.ui.theme.NeonBlue
import com.mobiladorprox.app.ui.theme.SurfaceDark
import com.mobiladorprox.app.ui.theme.TextSecondary

/**
 * Activity única do app. Hospeda toda a interface em Jetpack Compose e
 * navega entre as telas usando Navigation Compose. A barra inferior tem
 * apenas dois destinos: "Iniciar jogo" (tela de Jogos) e "Menu". O painel
 * flutuante do mapeador roda em um serviço separado (OverlayService), fora
 * desta Activity.
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

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "Iniciar jogo", Icons.Filled.PlayArrow),
    BottomDestination(Routes.MENU, "Menu", Icons.Filled.Menu)
)

@Composable
private fun MobiladorApp(factory: ViewModelFactory) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.HOME || currentRoute == Routes.MENU

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
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
                            colors = NavigationBarItemDefaults.colors(
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
                    onAddGame = { navController.navigate(Routes.GAME_PICKER) { launchSingleTop = true } }
                )
            }

            composable(Routes.GAME_PICKER) {
                val homeViewModel: HomeViewModel = viewModel(factory = factory)
                GamePickerScreen(
                    onBack = { navController.popBackStack() },
                    onGameSelected = { packageName, label ->
                        homeViewModel.selectGame(packageName, label) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Routes.MENU) {
                val homeViewModel: HomeViewModel = viewModel(factory = factory)
                val selectedGame by homeViewModel.selectedGame.collectAsStateWithLifecycle()
                MenuScreen(
                    onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                    onEditKeymaps = {
                        val profileId = selectedGame?.profileId
                        if (profileId != null) {
                            navController.navigate(Routes.keymaps(profileId)) { launchSingleTop = true }
                        } else {
                            // Sem jogo selecionado ainda: leva para a escolha de um jogo,
                            // já que os keymaps são configurados por jogo.
                            navController.navigate(Routes.GAME_PICKER) { launchSingleTop = true }
                        }
                    }
                )
            }

            composable(
                route = Routes.KEYMAPS,
                arguments = listOf(navArgument("profileId") { type = NavType.LongType })
            ) { entry ->
                val profileId = entry.arguments?.getLong("profileId") ?: return@composable
                KeymapEditorScreen(
                    factory = factory,
                    profileId = profileId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.OPTIMIZATION) {
                SubScreenScaffold(title = "Otimização", onBack = { navController.popBackStack() }) {
                    OptimizationScreen(factory = factory)
                }
            }

            composable(Routes.PERFORMANCE) {
                SubScreenScaffold(title = "Configurações de desempenho", onBack = { navController.popBackStack() }) {
                    PerformanceScreen(factory = factory)
                }
            }

            composable(Routes.MOUSE_SETTINGS) {
                SubScreenScaffold(title = "Configurações do mouse", onBack = { navController.popBackStack() }) {
                    MouseSettingsScreen(factory = factory)
                }
            }
        }
    }
}

@Composable
private fun SubScreenScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
