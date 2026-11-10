package com.syedmsidsm.abdulsscanner

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.ui.theme.AppThemeMode
import com.syedmsidsm.abdulsscanner.ui.nav.Routes
import com.syedmsidsm.abdulsscanner.ui.screens.backup.BackupScreen
import com.syedmsidsm.abdulsscanner.ui.screens.backup.BackupViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.document.DocumentScreen
import com.syedmsidsm.abdulsscanner.ui.screens.document.DocumentViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.home.HomeScreen
import com.syedmsidsm.abdulsscanner.ui.screens.home.HomeViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.labs.ArabicOcrLabsScreen
import com.syedmsidsm.abdulsscanner.ui.screens.pageeditor.PageViewerScreen
import com.syedmsidsm.abdulsscanner.ui.screens.settings.SettingsScreen
import com.syedmsidsm.abdulsscanner.ui.screens.settings.SettingsViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.tools.ToolsScreen
import com.syedmsidsm.abdulsscanner.ui.screens.trash.RecycleBinScreen
import com.syedmsidsm.abdulsscanner.ui.screens.trash.RecycleBinViewModel
import com.syedmsidsm.abdulsscanner.ui.theme.AbdulsScannerTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as AbdulsScannerApp
        val container = app.container

        // 1. Splash Screen handling as required by docs/logo-pack/LOGO-AND-SPLASH.md
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        val homeViewModel: HomeViewModel by viewModels {
            HomeViewModel.provideFactory(
                container.documentRepository,
                container.tagRepository,
                container.searchRepository,
                container.settingsRepository,
                container.saveScanUseCase
            )
        }

        val startedAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            !homeViewModel.appReady.value && SystemClock.uptimeMillis() - startedAt < 2000
        }

        splash.setOnExitAnimationListener { provider ->
            val remaining = if (Build.VERSION.SDK_INT >= 31) {
                (provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis
                        - SystemClock.uptimeMillis()).coerceIn(0L, 1000L)
            } else {
                0L
            }
            provider.iconView.animate()
                .scaleX(1.12f).scaleY(1.12f)
                .setStartDelay(remaining)
                .setDuration(250)
                .start()
            provider.view.animate()
                .alpha(0f)
                .setStartDelay(remaining)
                .setDuration(250)
                .withEndAction { provider.remove() }
                .start()
        }

        fun shareDocumentPdf(doc: DocumentEntity) {
            val exportFile = container.fileStore.getExportPdfFile(doc.id)
            if (!exportFile.exists()) return

            val shareDir = container.fileStore.shareDir
            val safeName = "${doc.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")}.pdf"
            val shareFile = File(shareDir, safeName)
            exportFile.copyTo(shareFile, overwrite = true)

            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                shareFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, doc.name))
        }

        setContent {
            val themeSetting = container.settingsRepository.theme.collectAsStateWithLifecycle(initialValue = "DARK")
            val themeMode = try {
                AppThemeMode.valueOf(themeSetting.value)
            } catch (e: Exception) {
                AppThemeMode.DARK
            }

            AbdulsScannerTheme(themeMode = themeMode) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToDocument = { docId ->
                                navController.navigate(Routes.document(docId))
                            },
                            onNavigateToTools = {
                                navController.navigate(Routes.TOOLS)
                            },
                            onNavigateToSettings = {
                                navController.navigate(Routes.SETTINGS)
                            },
                            onNavigateToBackup = {
                                navController.navigate(Routes.SETTINGS_BACKUP)
                            },
                            onNavigateToTags = {},
                            onSharePdf = { doc -> shareDocumentPdf(doc) }
                        )
                    }

                    composable(
                        route = Routes.DOCUMENT,
                        arguments = listOf(navArgument("docId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val docId = backStackEntry.arguments?.getString("docId") ?: return@composable
                        val documentViewModel: DocumentViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = DocumentViewModel.provideFactory(
                                docId,
                                container.documentRepository,
                                container.tagRepository,
                                container.saveScanUseCase
                            )
                        )

                        DocumentScreen(
                            viewModel = documentViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToPage = { dId, pageId ->
                                navController.navigate(Routes.pageViewer(dId, pageId))
                            },
                            onNavigateToOcr = { dId ->
                                navController.navigate(Routes.ocr(dId))
                            },
                            onSharePdf = { doc -> shareDocumentPdf(doc) },
                            onShareJpg = {},
                            onPrint = {}
                        )
                    }

                    composable(
                        route = Routes.PAGE_VIEWER,
                        arguments = listOf(
                            navArgument("docId") { type = NavType.StringType },
                            navArgument("pageId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val docId = backStackEntry.arguments?.getString("docId") ?: return@composable
                        val pageId = backStackEntry.arguments?.getString("pageId") ?: return@composable

                        val docViewModel: DocumentViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = DocumentViewModel.provideFactory(
                                docId,
                                container.documentRepository,
                                container.tagRepository,
                                container.saveScanUseCase
                            )
                        )
                        val docState = docViewModel.uiState.collectAsStateWithLifecycle().value

                        PageViewerScreen(
                            pages = docState.pages,
                            initialPageId = pageId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.TOOLS) {
                        ToolsScreen(
                            onNavigateToDocs = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            },
                            onCameraClick = {
                                navController.navigate(Routes.HOME)
                            },
                            onNavigateToRoute = { route ->
                                navController.navigate(route)
                            }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        val settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = SettingsViewModel.provideFactory(container)
                        )
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToBackup = { navController.navigate(Routes.SETTINGS_BACKUP) },
                            onNavigateToTrash = { navController.navigate(Routes.SETTINGS_TRASH) },
                            onNavigateToLabs = { navController.navigate(Routes.LABS_ARABIC_OCR) }
                        )
                    }

                    composable(Routes.SETTINGS_BACKUP) {
                        val backupViewModel: BackupViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = BackupViewModel.provideFactory(container)
                        )
                        BackupScreen(
                            viewModel = backupViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.SETTINGS_TRASH) {
                        val trashViewModel: RecycleBinViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = RecycleBinViewModel.provideFactory(container)
                        )
                        RecycleBinScreen(
                            viewModel = trashViewModel,
                            fileStore = container.fileStore,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.LABS_ARABIC_OCR) {
                        ArabicOcrLabsScreen(
                            tesseractEngine = container.tesseractEngine,
                            paddleEngine = container.paddleEngine,
                            mlKitLatinEngine = container.mlKitLatinEngine,
                            settingsRepository = container.settingsRepository,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
