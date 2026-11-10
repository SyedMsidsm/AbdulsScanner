package com.syedmsidsm.abdulsscanner

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentKind
import com.syedmsidsm.abdulsscanner.data.db.entity.PageFilter
import com.syedmsidsm.abdulsscanner.domain.imaging.Quad
import com.syedmsidsm.abdulsscanner.domain.scan.ScanPage
import com.syedmsidsm.abdulsscanner.domain.scan.ScanSession
import com.syedmsidsm.abdulsscanner.ui.screens.camera.ScanSessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MultiPageScanFlowTest {

    private lateinit var app: AbdulsScannerApp
    private lateinit var testDir: File
    private lateinit var photo1: File
    private lateinit var photo2: File
    private lateinit var photo3: File

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext<AbdulsScannerApp>()
        testDir = File(app.cacheDir, "test_multi_scan_flow").apply {
            deleteRecursively()
            mkdirs()
        }

        photo1 = createSolidColorImage(testDir, "photo1.jpg", 0xFFFFFFFF.toInt(), 800, 1000)
        photo2 = createSolidColorImage(testDir, "photo2.jpg", 0xFFE0E0E0.toInt(), 800, 1000)
        photo3 = createSolidColorImage(testDir, "photo3.jpg", 0xFFCCCCCC.toInt(), 800, 1000)
    }

    private fun createSolidColorImage(dir: File, name: String, color: Int, w: Int, h: Int): File {
        val file = File(dir, name)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
        return file
    }

    @Test
    fun testMultiPageScanFlow_threePages_capturedNextNextDone_reviewShowsThreePagesAndNeverCrashes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        lateinit var viewModel: ScanSessionViewModel

        // 1. Initialize ViewModel on Main Thread
        instrumentation.runOnMainSync {
            viewModel = ScanSessionViewModel(app, SavedStateHandle())
            viewModel.initSession()
        }

        assertNotNull(viewModel.session.value.sessionId)
        assertEquals(0, viewModel.pageCount)

        // 2. Photo 1 Captured -> AutoCrop -> Tap "Next page"
        instrumentation.runOnMainSync {
            val quad1 = Quad.fullQuad()
            viewModel.addPage(
                filePath = photo1.absolutePath,
                cropQuad = quad1,
                rotation = 0,
                filter = PageFilter.ENHANCED
            )
        }
        assertEquals(1, viewModel.pageCount)
        assertEquals(photo1.absolutePath, viewModel.pages[0].filePath)

        // 3. Photo 2 Captured -> AutoCrop -> Tap "Next page"
        instrumentation.runOnMainSync {
            val quad2 = Quad.fullQuad()
            viewModel.addPage(
                filePath = photo2.absolutePath,
                cropQuad = quad2,
                rotation = 0,
                filter = PageFilter.ENHANCED
            )
        }
        assertEquals(2, viewModel.pageCount)
        assertEquals(photo2.absolutePath, viewModel.pages[1].filePath)

        // 4. Photo 3 Captured -> AutoCrop -> Tap "Done"
        instrumentation.runOnMainSync {
            val quad3 = Quad.fullQuad()
            viewModel.addPage(
                filePath = photo3.absolutePath,
                cropQuad = quad3,
                rotation = 0,
                filter = PageFilter.ENHANCED
            )
        }
        assertEquals(3, viewModel.pageCount)
        assertEquals(photo3.absolutePath, viewModel.pages[2].filePath)

        // 5. Verify Review & Filter state has 3 pages
        val session = viewModel.session.value
        assertEquals(3, session.pages.size)

        // 6. Test Review Actions: Rotate current page
        instrumentation.runOnMainSync {
            viewModel.rotatePage(session.pages[0].id, 90)
        }
        assertEquals(90, viewModel.pages[0].rotation)

        // 7. Test Review Actions: Apply filter to all pages
        instrumentation.runOnMainSync {
            viewModel.setFilterForAllPages(PageFilter.BW)
        }
        viewModel.pages.forEach { page ->
            assertEquals(PageFilter.BW, page.filter)
        }

        // 8. Test Review Actions: Crop adjustment on Page 2
        instrumentation.runOnMainSync {
            val updatedQuad = Quad.fromNormalizedList(listOf(0.05f, 0.05f, 0.95f, 0.05f, 0.95f, 0.95f, 0.05f, 0.95f))
            viewModel.updatePage(session.pages[1].id, cropQuad = updatedQuad)
        }
        assertNotNull(viewModel.pages[1].cropQuad)
        assertEquals(0.05f, viewModel.pages[1].cropQuad!!.p0.x, 0.001f)

        // 9. Verify Session persistence on disk (Crash protection)
        val sessionDir = ScanSession.getSessionDir(app, session.sessionId)
        val sessionFile = File(sessionDir, "session.json")
        // Trigger manual persist to be certain disk write completed
        ScanSession.save(app, viewModel.session.value)
        assertTrue("session.json must exist on disk for crash recovery", sessionFile.exists())

        val loadedSession = ScanSession.load(sessionDir)
        assertNotNull(loadedSession)
        assertEquals(3, loadedSession!!.pages.size)

        // 10. Save Scan to Document (Done)
        var savedDocId: String? = null
        kotlinx.coroutines.runBlocking {
            savedDocId = app.container.saveScanUseCase.saveScanSession(
                session = viewModel.session.value,
                kind = DocumentKind.SCAN,
                customName = "Test 3 Page Document"
            )
        }
        instrumentation.runOnMainSync {
            viewModel.discardSession()
        }

        assertNotNull(savedDocId)
        assertTrue("Document ID must not be empty", savedDocId!!.isNotEmpty())

        // 11. Verify Database contains the document and its 3 pages
        kotlinx.coroutines.runBlocking {
            val doc = app.container.database.documentDao().getById(savedDocId!!)
            assertNotNull("Saved document must exist in database", doc)
            assertEquals("Test 3 Page Document", doc!!.name)
            assertEquals(3, doc.pageCount)

            val pages = app.container.database.pageDao().getByDocumentId(doc.id)
            assertEquals(3, pages.size)
        }

        // 12. Verify session directory was cleaned up upon successful save
        assertFalse("Session dir should be cleared after save", sessionFile.exists())
    }
}
