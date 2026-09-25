package com.normcap.android

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Design Read: utility OCR untuk pengguna Indonesia, gaya Material fungsional,
// dial ENERGY 1 / RHYTHM 1 / MOTION 1 (draft without direction, R-37).
class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf("home")
    private var shot: Bitmap? by mutableStateOf(null)
    private var ocrText by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var lang by mutableStateOf(OcrLang.LATIN)
    private var pendingAutoCapture = false

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK && res.data != null) {
                grabScreen(res.resultCode, res.data!!)
            } else {
                error = "Izin capture ditolak. Coba lagi."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAutoCapture = intent?.action == "AUTO_CAPTURE"
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        if (pendingAutoCapture) {
            pendingAutoCapture = false
            askCapture()
        }
    }

    private fun askCapture() {
        error = null
        val mgr = getSystemService<MediaProjectionManager>()!!
        captureLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun grabScreen(resultCode: Int, data: Intent) {
        loading = true
        error = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bmp = takeScreenshot(resultCode, data)
                withContext(Dispatchers.Main) {
                    shot = bmp
                    screen = "crop"
                    loading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Gagal capture: ${e.message}"
                    loading = false
                }
            }
        }
    }

    private fun takeScreenshot(resultCode: Int, data: Intent): Bitmap {
        val mgr = getSystemService<MediaProjectionManager>()!!
        val projection = mgr.getMediaProjection(resultCode, data)
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        var vd: VirtualDisplay? = null
        try {
            vd = projection.createVirtualDisplay(
                "normcap", w, h, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null,
            )
            // ponytail: polling singkat, ImageReader tanpa callback lebih sedikit kode.
            var img: android.media.Image? = null
            val end = System.currentTimeMillis() + 1500
            while (img == null && System.currentTimeMillis() < end) {
                img = reader.acquireLatestImage()
                if (img == null) Thread.sleep(80)
            }
            val image = img ?: throw IllegalStateException("layar kosong, coba lagi")
            try {
                val plane = image.planes[0]
                val buf = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPad = rowStride - pixelStride * w
                val full = Bitmap.createBitmap(w + rowPad / pixelStride, h, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(buf)
                return Bitmap.createBitmap(full, 0, 0, w, h)
            } finally {
                image.close()
            }
        } finally {
            vd?.release()
            reader.close()
            projection.stop()
        }
    }

    private fun runOcr(bmp: Bitmap) {
        loading = true
        error = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val t = recognizeText(bmp, lang)
                withContext(Dispatchers.Main) {
                    ocrText = t.ifBlank { "(tidak ada teks terbaca)" }
                    screen = "result"
                    loading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "OCR gagal: ${e.message}"
                    loading = false
                }
            }
        }
    }

    @Composable
    private fun App() {
        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("NormCap", style = MaterialTheme.typography.headlineMedium)
                    LangRow()
                    when {
                        loading -> {
                            CircularProgressIndicator()
                            Text("Memproses...")
                        }
                        error != null -> {
                            Text("Terjadi masalah: $error")
                            ActionButton("Coba lagi") {
                                error = null
                                screen = "home"
                            }
                        }
                        screen == "home" -> HomeScreen()
                        screen == "crop" -> CropScreen()
                        screen == "result" -> ResultScreen()
                    }
                }
            }
        }
    }

    @Composable
    private fun LangRow() {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Bahasa OCR", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OcrLang.entries.forEach { l ->
                    FilterChip(
                        selected = lang == l,
                        onClick = { lang = l },
                        label = { Text(if (l == OcrLang.LATIN) "ID/EN" else l.label) },
                    )
                }
            }
        }
    }

    @Composable
    private fun HomeScreen() {
        // Empty state jujur (R-27): belum ada hasil.
        Text("Ambil screenshot layar lalu pilih area untuk diubah jadi teks. Semua offline.")
        ActionButton("Capture layar") { askCapture() }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { startBubble() },
            modifier = Modifier.fillMaxWidth().height(48.dp), // >=44dp (R-03)
        ) { Text(if (Settings.canDrawOverlays(this@MainActivity)) "Bubble aktif" else "Aktifkan bubble mengambang") }
        Text("Hasil bisa disalin, dibagikan, atau dibuka otomatis kalau berisi link, email, atau nomor.")
    }

    @Composable
    private fun CropScreen() {
        val bmp = shot
        if (bmp == null) {
            Text("Screenshot hilang. Ulangi capture.")
            ActionButton("Kembali") { screen = "home" }
            return
        }
        var rect by mutableStateOf<Rect?>(null)
        var canvasPx by mutableStateOf(IntSize.Zero)
        Text("Seret untuk pilih area, lalu jalankan OCR.")
        Canvas(
            Modifier.fillMaxWidth().height(420.dp)
                .onSizeChanged { canvasPx = it }
                .pointerInput(Unit) {
                var start = Offset.Zero
                detectDragGestures(
                    onDragStart = { start = it; rect = Rect(start, start) },
                    onDrag = { ch, drag ->
                        ch.consume()
                        val end = rect!!.bottomRight + Offset(drag.x, drag.y)
                        rect = Rect(start, end)
                    },
                )
            }
        ) {
            drawImage(bmp.asImageBitmap(), dstSize = size)
            rect?.let {
                drawRect(
                    color = androidx.compose.ui.graphics.Color(0xFF1A73E8),
                    topLeft = it.topLeft, size = it.size,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
                )
            }
        }
        ActionButton("OCR area ini") {
            val r = rect
            if (r == null || canvasPx.width < 1 || canvasPx.height < 1) runOcr(bmp)
            else {
                // ponytail: gambar diskala pas ke Canvas, skala crop = bitmap / canvas.
                val sx = bmp.width / canvasPx.width.toFloat()
                val sy = bmp.height / canvasPx.height.toFloat()
                val x0 = minOf(r.left, r.right).coerceIn(0f, canvasPx.width.toFloat())
                val y0 = minOf(r.top, r.bottom).coerceIn(0f, canvasPx.height.toFloat())
                val x1 = maxOf(r.left, r.right).coerceIn(0f, canvasPx.width.toFloat())
                val y1 = maxOf(r.top, r.bottom).coerceIn(0f, canvasPx.height.toFloat())
                val left = (x0 * sx).toInt().coerceIn(0, bmp.width - 1)
                val top = (y0 * sy).toInt().coerceIn(0, bmp.height - 1)
                val ww = ((x1 - x0) * sx).toInt().coerceIn(1, bmp.width - left)
                val hh = ((y1 - y0) * sy).toInt().coerceIn(1, bmp.height - top)
                runOcr(Bitmap.createBitmap(bmp, left, top, ww, hh))
            }
        }
        Image(bmp.asImageBitmap(), contentDescription = "Hasil screenshot")
    }

    @Composable
    private fun ResultScreen() {
        Text("Hasil OCR:", style = MaterialTheme.typography.labelLarge)
        Text(ocrText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Salin", Modifier.weight(1f)) { copyText(ocrText) }
            ActionButton("Bagikan", Modifier.weight(1f)) { shareText(ocrText) }
        }
        MagicActions(ocrText)
        OutlinedButton(
            onClick = { screen = "home"; ocrText = "" },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Scan lagi") }
    }

    @Composable
    private fun MagicActions(t: String) {
        val url = Patterns.WEB_URL.matcher(t).let { m -> if (m.find()) m.group() else null }
        val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(t)?.value
        val phone = Regex("\\+?[0-9][0-9 ()-]{6,}").find(t)?.value?.trim()
        if (url == null && email == null && phone == null) return
        Text("Magic:", style = MaterialTheme.typography.labelLarge)
        url?.let { u ->
            val fixed = if (u.startsWith("http")) u else "https://$u"
            ActionButton("Buka link") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fixed))) }
        }
        email?.let { e ->
            ActionButton("Kirim email") { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$e"))) }
        }
        phone?.let { p ->
            ActionButton("Hubungi nomor") { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$p"))) }
        }
    }

    @Composable
    private fun ActionButton(label: String, mod: Modifier = Modifier, onClick: () -> Unit) {
        Button(onClick = onClick, modifier = mod.then(Modifier.fillMaxWidth().height(48.dp))) {
            Text(label)
        }
    }

    private fun copyText(t: String) {
        getSystemService<ClipboardManager>()!!
            .setPrimaryClip(ClipData.newPlainText("ocr", t))
    }

    private fun shareText(t: String) {
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, t) },
            "Bagikan teks",
        ))
    }

    private fun startBubble() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, CaptureService::class.java))
    }
}
