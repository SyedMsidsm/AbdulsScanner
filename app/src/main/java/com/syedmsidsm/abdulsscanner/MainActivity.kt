package com.syedmsidsm.abdulsscanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.domain.pdf.PdfPrintAdapter
import com.syedmsidsm.abdulsscanner.ui.theme.AppThemeMode
import com.syedmsidsm.abdulsscanner.ui.nav.Routes
import com.syedmsidsm.abdulsscanner.ui.screens.backup.BackupScreen
import com.syedmsidsm.abdulsscanner.ui.screens.backup.BackupViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.document.DocumentScreen
import com.syedmsidsm.abdulsscanner.ui.screens.document.DocumentViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.home.HomeScreen
import com.syedmsidsm.abdulsscanner.ui.screens.home.HomeViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.labs.ArabicOcrLabsScreen
import com.syedmsidsm.abdulsscanner.ui.screens.ocr.ToTextScreen
import com.syedmsidsm.abdulsscanner.ui.screens.ocr.ToTextViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.pageeditor.PageViewerScreen
import com.syedmsidsm.abdulsscanner.ui.screens.qr.QrHistoryScreen
import com.syedmsidsm.abdulsscanner.ui.screens.qr.QrHistoryViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.qr.QrResultScreen
import com.syedmsidsm.abdulsscanner.ui.screens.security.LockScreen
import com.syedmsidsm.abdulsscanner.ui.screens.security.SecuritySettingsScreen
import com.syedmsidsm.abdulsscanner.ui.screens.settings.SettingsScreen
import com.syedmsidsm.abdulsscanner.ui.screens.settings.SettingsViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.tags.TagsScreen
import com.syedmsidsm.abdulsscanner.ui.screens.tags.TagsViewModel
import com.syedmsidsm.abdulsscanner.ui.screens.tools.ToolsScreen
import com.syedmsidsm.abdulsscanner.ui.screens.trash.RecycleBinScreen
import com.syedmsidsm.abdulsscanner.ui.screens.trash.RecycleBinViewModel
import com.syedmsidsm.abdulsscanner.ui.theme.AbdulsScannerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                container.saveScanUseCase,
                container.mergeDocumentsUseCase
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

        fun shareMultipleDocumentPdfs(docs: List<DocumentEntity>) {
            if (docs.isEmpty()) return
            val shareDir = container.fileStore.shareDir
            val uris = ArrayList<Uri>()
            for (doc in docs) {
                val exportFile = container.fileStore.getExportPdfFile(doc.id)
                if (exportFile.exists()) {
                    val safeName = "${doc.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")}.pdf"
                    val shareFile = File(shareDir, safeName)
                    exportFile.copyTo(shareFile, overwrite = true)
                    val uri = FileProvider.getUriForFile(
                        this,
                        "${applicationContext.packageName}.fileprovider",
                        shareFile
                    )
                    uris.add(uri)
                }
            }
            if (uris.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/pdf"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share ${docs.size} documents"))
            }
        }

        fun shareDocumentJpgs(doc: DocumentEntity) {
            val shareDir = container.fileStore.shareDir
            val safeDocName = doc.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            CoroutineScope(Dispatchers.IO).launch {
                val pages = container.database.pageDao().getByDocumentId(doc.id)
                val uris = ArrayList<Uri>()
                pages.forEachIndexed { idx, page ->
                    val renderedFile = container.fileStore.getPageRenderedFile(doc.id, page.id)
                    if (renderedFile.exists()) {
                        val shareFile = File(shareDir, "$safeDocName - ${idx + 1}.jpg")
                        renderedFile.copyTo(shareFile, overwrite = true)
                        val uri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${applicationContext.packageName}.fileprovider",
                            shareFile
                        )
                        uris.add(uri)
                    }
                }
                if (uris.isNotEmpty()) {
                    val intent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uris[0])
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "image/jpeg"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    startActivity(Intent.createChooser(intent, doc.name))
                }
            }
        }

        fun printDocumentPdf(doc: DocumentEntity) {
            val exportFile = container.fileStore.getExportPdfFile(doc.id)
            if (!exportFile.exists()) return
            val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager != null) {
                val printAdapter = PdfPrintAdapter(exportFile)
                val attributes = PrintAttributes.Builder()
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .build()
                printManager.print(doc.name, printAdapter, attributes)
            }
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
                        val isSessionUnlocked by container.sessionManager.isSessionUnlocked.collectAsStateWithLifecycle()
                        HomeScreen(
                            viewModel = homeViewModel,
                            isSessionUnlocked = isSessionUnlocked,
                            onNavigateToDocument = { docId ->
                                navController.navigate(Routes.document(docId))
                            },
                            onNavigateToLock = { docId ->
                                navController.navigate(Routes.lock(docId))
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
                            onNavigateToTags = {
                                navController.navigate(Routes.TAGS)
                            },
                            onSharePdf = { doc -> shareDocumentPdf(doc) },
                            onShareMultiplePdfs = { docs -> shareMultipleDocumentPdfs(docs) }
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
                            fileStore = container.fileStore,
                            settingsRepository = container.settingsRepository,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToPage = { dId, pageId ->
                                navController.navigate(Routes.pageViewer(dId, pageId))
                            },
                            onNavigateToOcr = { dId ->
                                navController.navigate(Routes.ocr(dId))
                            },
                            onNavigateToSecurity = {
                                navController.navigate(Routes.SETTINGS_SECURITY)
                            },
                            onSharePdf = { doc -> shareDocumentPdf(doc) },
                            onShareJpg = { doc -> shareDocumentJpgs(doc) },
                            onPrint = { doc -> printDocumentPdf(doc) }
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

                        PageViewerScreen(
                            docId = docId,
                            initialPageId = pageId,
                            documentRepository = container.documentRepository,
                            pageRenderer = container.pageRenderer,
                            fileStore = container.fileStore,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.TAGS) {
                        val tagsViewModel: TagsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = TagsViewModel.provideFactory(container.tagRepository)
                        )
                        TagsScreen(
                            viewModel = tagsViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.SETTINGS_SECURITY) {
                        SecuritySettingsScreen(
                            settingsRepository = container.settingsRepository,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Routes.LOCK,
                        arguments = listOf(navArgument("docId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val docId = backStackEntry.arguments?.getString("docId") ?: return@composable
                        LockScreen(
                            settingsRepository = container.settingsRepository,
                            sessionManager = container.sessionManager,
                            onSuccess = {
                                navController.popBackStack()
                                navController.navigate(Routes.document(docId))
                            },
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToResetPin = {
                                navController.navigate(Routes.SETTINGS_SECURITY)
                            }
                        )
                    }

                    composable(Routes.TOOLS) {
                        ToolsScreen(
                            container = container,
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

                    composable(
                        route = Routes.OCR,
                        arguments = listOf(
                            navArgument("docId") { type = NavType.StringType },
                            navArgument("pages") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val docId = backStackEntry.arguments?.getString("docId") ?: return@composable
                        val pagesParam = backStackEntry.arguments?.getString("pages").orEmpty()
                        val pageIds = if (pagesParam.isNotBlank()) pagesParam.split(",") else emptyList()

                        val toTextViewModel: ToTextViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = ToTextViewModel.provideFactory(
                                docId = docId,
                                pageIds = pageIds,
                                database = container.database,
                                fileStore = container.fileStore,
                                ocrService = container.ocrService,
                                searchRepository = container.searchRepository,
                                settingsRepository = container.settingsRepository
                            )
                        )
                        ToTextScreen(
                            viewModel = toTextViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Routes.QR_RESULT,
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                            navArgument("raw") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getLong("id") ?: 0L
                        val raw = backStackEntry.arguments?.getString("raw").orEmpty()
                        QrResultScreen(
                            qrId = id,
                            rawText = if (raw.isNotEmpty()) android.net.Uri.decode(raw) else null,
                            qrRepository = container.qrRepository,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Routes.QR_HISTORY) {
                        val qrHistoryViewModel: QrHistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                            factory = QrHistoryViewModel.provideFactory(container.qrRepository)
                        )
                        QrHistoryScreen(
                            viewModel = qrHistoryViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToResult = { id ->
                                navController.navigate(Routes.qrResult(id))
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
                            onNavigateToSecurity = { navController.navigate(Routes.SETTINGS_SECURITY) },
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
