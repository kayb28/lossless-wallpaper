package com.kayb28.losslesswallpaper

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var report by remember { mutableStateOf<QualityReport?>(null) }
    var target by remember { mutableStateOf(WallpaperTarget.BOTH) }
    var applying by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Photo picker / some providers may not support persistable grants.
            }
            imageUri = uri
        }
    }

    LaunchedEffect(imageUri) {
        val uri = imageUri
        previewBitmap = null
        report = null
        if (uri == null) return@LaunchedEffect
        val info = ImageInspector.readBounds(context, uri)
        if (info != null) {
            report = ImageInspector.qualityReport(info, ImageInspector.screenInfo(context))
        }
        // Preview only: decode for on-screen display. Apply path never uses this bitmap.
        previewBitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, opts)
                // subsample only for preview memory; does not affect setStream apply
                var sample = 1
                val maxSide = 2048
                while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) {
                    sample *= 2
                }
                context.contentResolver.openInputStream(uri)?.use { input2 ->
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeStream(input2, null, decodeOpts)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lossless Wallpaper") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Aplica el archivo original (PNG / WebP / JPG) con WallpaperManager.setStream. Sin recomprimir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    picker.launch(arrayOf("image/png", "image/jpeg", "image/webp", "image/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(if (imageUri == null) "Elegir imagen" else "Cambiar imagen")
            }

            // Preview with screen-aspect crop overlay
            val screen = remember { ImageInspector.screenInfo(context) }
            val screenAspect = screen.widthPx.toFloat() / screen.heightPx.toFloat()

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(screenAspect)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
            ) {
                val bmp = previewBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Vista previa del recorte de pantalla",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        text = "Recorte ≈ proporción pantalla",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        text = "Vista previa",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            report?.let { q ->
                QualityCard(q)
            }

            Text("Aplicar en", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetChip("Inicio", WallpaperTarget.HOME, target) { target = it }
                TargetChip("Bloqueo", WallpaperTarget.LOCK, target) { target = it }
                TargetChip("Ambas", WallpaperTarget.BOTH, target) { target = it }
            }

            Button(
                onClick = {
                    val uri = imageUri ?: return@Button
                    scope.launch {
                        applying = true
                        val result = WallpaperApplier.apply(context, uri, target)
                        applying = false
                        if (result.isSuccess) {
                            snackbar.showSnackbar("Fondo aplicado (stream original)")
                        } else {
                            snackbar.showSnackbar(
                                result.exceptionOrNull()?.message ?: "Error al aplicar",
                            )
                        }
                    }
                },
                enabled = imageUri != null && !applying,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (applying) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Aplicar sin recomprimir")
                }
            }

            TextButton(onClick = {
                scope.launch {
                    snackbar.showSnackbar(
                        "Verificación: aplica una foto con texto fino → captura de pantalla → compara con el original a 100% zoom.",
                    )
                }
            }) {
                Text("Cómo verificar la calidad")
            }
        }
    }
}

@Composable
private fun TargetChip(
    label: String,
    value: WallpaperTarget,
    selected: WallpaperTarget,
    onSelect: (WallpaperTarget) -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@Composable
private fun QualityCard(q: QualityReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Indicador de calidad", fontWeight = FontWeight.SemiBold)
        Text("Imagen: ${q.image.width}×${q.image.height} (${"%.2f".format(q.imageMegapixels)} MP)")
        Text("MIME: ${q.image.mimeType ?: "desconocido"}")
        Text("Pantalla: ${q.screen.widthPx}×${q.screen.heightPx} (${"%.2f".format(q.screenMegapixels)} MP)")
        Text("Deseado sistema: ${q.screen.desiredMinWidth}×${q.screen.desiredMinHeight}")
        if (q.warning != null) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.padding(4.dp))
                Text(q.warning, color = MaterialTheme.colorScheme.error)
            }
        } else {
            Text(
                "La imagen cubre la pantalla y el tamaño deseado. Se aplicará el archivo original.",
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
