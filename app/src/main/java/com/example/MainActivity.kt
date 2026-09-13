package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.database.AppDatabase
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.PurchaseScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppStrings
import com.example.util.LanguagePreferences
import com.example.util.LocalAppStrings
import com.example.util.ThemePreferences
import com.example.viewmodel.VpnViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val database = AppDatabase.getDatabase(applicationContext)
        ThemePreferences.init(applicationContext)
        LanguagePreferences.init(applicationContext)
        val viewModelFactory = VpnViewModel.Factory(database)
        val viewModel = ViewModelProvider(this, viewModelFactory)[VpnViewModel::class.java]

        setContent {
            val themeMode by viewModel.appThemeMode.collectAsState()
            val appLanguage by viewModel.appLanguage.collectAsState()
            val appStrings = remember(appLanguage) { AppStrings.get(appLanguage) }
            val layoutDirection = if (appLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(
                LocalLayoutDirection provides layoutDirection,
                LocalAppStrings provides appStrings
            ) {
                MyApplicationTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val activeSession by viewModel.activeSession.collectAsState()

                    // Start directly on Dashboard if user session is already active locally
                    val startDestination = if (activeSession != null) "main" else "login"

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("login") {
                            LoginScreen(
                                viewModel = viewModel,
                                onNavigateToMain = {
                                    navController.navigate("main") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToPurchase = {
                                    navController.navigate("purchase")
                                }
                            )
                        }
                        composable("main") {
                            MainScreen(
                                viewModel = viewModel,
                                onNavigateBackToLogin = {
                                    navController.navigate("login") {
                                        popUpTo("main") { inclusive = true }
                                    }
                                },
                                onNavigateToPurchase = {
                                    navController.navigate("purchase")
                                }
                            )
                        }
                        composable("purchase") {
                            PurchaseScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
