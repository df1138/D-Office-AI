package com.officeclone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.officeclone.app.ui.HomeScreen
import com.officeclone.app.ui.ConverterScreen
import com.officeclone.app.ui.DriveScreen
import com.officeclone.app.ui.SheetEditorScreen
import com.officeclone.app.ui.SlidesEditorScreen
import com.officeclone.app.ui.WordEditorScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val currentRoute = entry?.destination?.route ?: "home"

                fun navigateTo(route: String) {
                    if (route == currentRoute) return
                    nav.navigate(route) {
                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentRoute == "home",
                                onClick = { navigateTo("home") },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("Главная") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "word",
                                onClick = { navigateTo("word") },
                                icon = { Icon(Icons.Default.Description, contentDescription = null) },
                                label = { Text("Документ") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "sheet",
                                onClick = { navigateTo("sheet") },
                                icon = { Icon(Icons.Default.GridOn, contentDescription = null) },
                                label = { Text("Таблица") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "converter",
                                onClick = { navigateTo("converter") },
                                icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                                label = { Text("Конвертер") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "slides",
                                onClick = { navigateTo("slides") },
                                icon = { Icon(Icons.Default.Slideshow, contentDescription = null) },
                                label = { Text("Слайды") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "drive",
                                onClick = { navigateTo("drive") },
                                icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                                label = { Text("Drive") }
                            )
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        modifier = Modifier.padding(padding)
                    ) {
                        composable("home") { HomeScreen(onOpenWord = { navigateTo("word") }, onOpenSheet = { navigateTo("sheet") }, onOpenSlides = { navigateTo("slides") }, onOpenConverter = { navigateTo("converter") }) }
                        composable("converter") { ConverterScreen() }
                        composable("word") { WordEditorScreen() }
                        composable("sheet") { SheetEditorScreen() }
                        composable("slides") { SlidesEditorScreen() }
                        composable("drive") { DriveScreen() }
                    }
                }
            }
        }
    }
}
