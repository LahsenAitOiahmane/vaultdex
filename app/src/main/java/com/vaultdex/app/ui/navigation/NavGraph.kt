package com.vaultdex.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vaultdex.app.ui.screens.*
import com.vaultdex.app.viewmodel.AuthViewModel
import com.vaultdex.app.viewmodel.NotesViewModel
import com.vaultdex.app.viewmodel.ProfileViewModel

object Routes {
    const val LOGIN   = "login"
    const val HOME    = "home"
    const val NOTES   = "notes"
    const val PIN     = "pin"
    const val PROFILE = "profile"
}

@Composable
fun VaultDexNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()

    val startDestination = if (authState.isLoggedIn) Routes.HOME else Routes.LOGIN

    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            val profileViewModel: ProfileViewModel = viewModel()
            HomeScreen(
                authViewModel   = authViewModel,
                profileViewModel = profileViewModel,
                onNavigateToNotes   = { navController.navigate(Routes.NOTES) },
                onNavigateToPin     = { navController.navigate(Routes.PIN) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.NOTES) {
            val notesViewModel: NotesViewModel = viewModel()
            NotesScreen(
                notesViewModel = notesViewModel,
                onBack         = { navController.popBackStack() }
            )
        }

        composable(Routes.PIN) {
            PinScreen(
                authViewModel = authViewModel,
                onBack        = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            val profileViewModel: ProfileViewModel = viewModel()
            ProfileScreen(
                authViewModel   = authViewModel,
                profileViewModel = profileViewModel,
                onBack          = { navController.popBackStack() }
            )
        }
    }
}
