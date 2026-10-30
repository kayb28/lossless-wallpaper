package com.kayb28.losslesswallpaper.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kayb28.losslesswallpaper.R
import com.kayb28.losslesswallpaper.core.CropCalculator
import com.kayb28.losslesswallpaper.core.CropSpec
import com.kayb28.losslesswallpaper.core.ImageInfo
import com.kayb28.losslesswallpaper.core.ImageMeta
import com.kayb28.losslesswallpaper.core.RectI
import com.kayb28.losslesswallpaper.core.ScreenInfo
import com.kayb28.losslesswallpaper.core.WallpaperApplier
import com.kayb28.losslesswallpaper.core.WallpaperTarget
import com.kayb28.losslesswallpaper.core.resolveScreenInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WallpaperApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var uri by remember { mutableStateOf<Uri?>(null) }
    var info by remember { mutableStateOf<ImageInfo?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var screen by remember { mutableStateOf<ScreenInfo?>(null) }
    var cropCenter by remember { mutableStateOf<Offset?>(null) }
    var target by remember { mutableStateOf(WallpaperTarget.BOTH) }
    var applying by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0, neverEqualPolicy()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            cropCenter = null
            uri = picked
            reloadKey++
        }
    }

    LaunchedEffect(uri, reloadKey) {
        val current = uri ?: return@LaunchedEffect
        info = null
        preview = null
        screen = resolveScreenInfo(context)
        val loadedInfo = withContext(Dispatchers.IO) { ImageMeta.readInfo(context.contentResolver, current) }
        val loadedPreview = withContext(Dispatchers.IO) {
            ImageMeta.decodePreview(context.contentResolver, current)
        }
        if (loadedInfo == null || loadedPreview == null) {
            uri = null
            info = null
            preview = null
            snackbar.showSnackbar(context.getString(R.string.err_decode))
        } else {
            info = loadedInfo
            preview = loadedPreview
        }
    }

    val spec = remember(info, screen) {
        val i = info
        val s = screen
        if (i != null && s != null) {
            CropCalculator.computeCrop(i.width, i.height, s.screenWidth, s.screenHeight)
        } else {
            null
        }
    }

    val activeCrop = remember(spec, cropCenter) {
        when {
            spec?.cropRect == null -> spec?.cropRect
            cropCenter == null -> spec.cropRect
            else -> CropCalculator.movedCrop(spec, cropCenter.x, cropCenter.y)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (uri == null || preview == null || spec == null) {
                EmptyState(
                    modifier = Modifier.fillMaxWidth(),
                    onPick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            } else {
                PreviewCard(
                    bitmap = preview,
                    spec = spec,
                    cropRect = activeCrop,
                    onMove = { dx, dy ->
                        val base = cropCenter ?: Offset(
                            spec.cropRect?.centerX ?: 0f,
                            spec.cropRect?.centerY ?: 0f,
                        )
                        cropCenter = Offset(base.x + dx, base.y + dy)
                    },
                )
                Spacer(Modifier.height(12.dp))
                QualityPanel(info = info, screen = screen, spec = spec, cropRect = activeCrop)
                Spacer(Modifier.height(12.dp))
                TargetSelector(selected = target, onSelect = { target = it })
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        val currentUri = uri ?: return@Button
                        scope.launch {
                            applying = true
                            val result = WallpaperApplier.apply(context, currentUri, activeCrop, target)
                            applying = false
                            val message = if (result.isSuccess) {
                                context.getString(R.string.applied_ok)
                            } else {
                                context.getString(
                                    R.string.applied_fail,
                                    result.exceptionOrNull()?.message ?: "?",
                                )
                            }
                            snackbar.showSnackbar(message)
                        }
                    },
                    enabled = !applying,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (applying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.apply), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { uri = null; info = null; preview = null; cropCenter = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.change_image))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.preview_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onPick: () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onPick, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.choose_image))
            }
        }
    }
}

@Composable
private fun PreviewCard(
    bitmap: Bitmap,
    spec: CropSpec,
    cropRect: RectI?,
    onMove: (Float, Float) -> Unit,
) {
    val imageBitmap = remember(bitmap.id) { bitmap.asImageBitmap() }
    var boxSizePx by remember { mutableStateOf(IntSize.Zero) }

    // Escala píxeles de imagen -> píxeles de la vista previa
    val scale = if (boxSizePx.width > 0) boxSizePx.width.toFloat() / spec.imageWidth else 1f

    val ratio = spec.imageWidth.toFloat() / spec.imageHeight
    // La caja SIEMPRE mantiene la proporción exacta de la imagen:
    // - horizontal: ancho completo, alto derivado
    // - vertical: alto limitado (520.dp) y ancho derivado (matchHeightConstraintsFirst)
    val imageModifier = if (ratio >= 1f) {
        Modifier.fillMaxWidth().aspectRatio(ratio)
    } else {
        Modifier.heightIn(max = 520.dp).fillMaxWidth().aspectRatio(ratio, matchHeightConstraintsFirst = true)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = imageModifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF171B21))
                .onSizeChanged { boxSizePx = it }
                // La clave es `spec` (estable): el lambda lee el estado vivo
                // (cropCenter, boxSizePx) a través de delegados, así el gesto
                // no se reinicia en cada frame de arrastre.
                .pointerInput(spec) {
                    if (spec.cropRect == null) return@pointerInput
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onMove(dragAmount.x / scale, dragAmount.y / scale)
                    }
                },
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            if (cropRect != null && scale > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    val left = cropRect.left * scale
                    val top = cropRect.top * scale
                    val right = cropRect.right * scale
                    val bottom = cropRect.bottom * scale

                    // Oscurecer lo que queda fuera del recorte
                    val scrim = Color(0xB3000000)
                    drawRect(scrim, size = Size(left, size.height), topLeft = Offset.Zero)
                    drawRect(scrim, size = Size(size.width - right, size.height), topLeft = Offset(right, 0f))
                    drawRect(scrim, size = Size(right - left, top), topLeft = Offset(left, 0f))
                    drawRect(
                        scrim,
                        size = Size(right - left, size.height - bottom),
                        topLeft = Offset(left, bottom),
                    )

                    // Borde del recorte
                    drawRect(
                        color = Color(0xFF8FB8FF),
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (cropRect != null) {
                stringResource(R.string.crop_hint)
            } else {
                stringResource(R.string.crop_full_image)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QualityPanel(
    info: ImageInfo?,
    screen: ScreenInfo?,
    spec: CropSpec?,
    cropRect: RectI?,
) {
    if (info == null || screen == null || spec == null) return
    val mono = FontFamily.Monospace

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = stringResource(R.string.quality_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            QualityRow(stringResource(R.string.quality_image), "${info.width}×${info.height} · ${info.formatLabel}", mono)
            QualityRow(stringResource(R.string.quality_screen), "${screen.screenWidth}×${screen.screenHeight}", mono)
            QualityRow(stringResource(R.string.quality_desired), "${screen.desiredWidth}×${screen.desiredHeight}", mono)
            QualityRow(
                stringResource(R.string.quality_crop),
                if (cropRect != null) {
                    "${cropRect.width}×${cropRect.height}"
                } else {
                    stringResource(R.string.quality_crop_full)
                },
                mono,
            )

            Spacer(Modifier.height(10.dp))
            val coversDesired = spec.cropWidth >= screen.desiredWidth && spec.cropHeight >= screen.desiredHeight
            when {
                spec.needsUpscale -> {
                    QualityBanner(
                        backgroundColor = Color(0xFF3B2F14),
                        borderColor = Color(0xFF8A6D2F),
                        textColor = Color(0xFFE8C97A),
                        title = stringResource(R.string.quality_warn_title),
                        body = stringResource(
                            R.string.quality_warn_body,
                            String.format(Locale.US, "×%.2f", spec.systemScale),
                        ),
                    )
                }
                !coversDesired -> {
                    QualityBanner(
                        backgroundColor = Color(0xFF14321F),
                        borderColor = Color(0xFF2F6D45),
                        textColor = Color(0xFF9BE0B4),
                        title = stringResource(R.string.quality_ok_title),
                        body = stringResource(R.string.quality_ok_body),
                    )
                    Spacer(Modifier.height(8.dp))
                    QualityBanner(
                        backgroundColor = Color(0xFF1B2740),
                        borderColor = Color(0xFF3D5480),
                        textColor = Color(0xFFA8C4F0),
                        title = stringResource(R.string.quality_partial_title),
                        body = stringResource(R.string.quality_partial_body, "${screen.desiredWidth}×${screen.desiredHeight}"),
                    )
                }
                else -> {
                    QualityBanner(
                        backgroundColor = Color(0xFF14321F),
                        borderColor = Color(0xFF2F6D45),
                        textColor = Color(0xFF9BE0B4),
                        title = stringResource(R.string.quality_ok_title),
                        body = stringResource(R.string.quality_ok_body),
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityRow(label: String, value: String, mono: FontFamily) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = mono, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun QualityBanner(backgroundColor: Color, borderColor: Color, textColor: Color, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textColor)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = textColor)
    }
}

@Composable
private fun TargetSelector(selected: WallpaperTarget, onSelect: (WallpaperTarget) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == WallpaperTarget.HOME,
            onClick = { onSelect(WallpaperTarget.HOME) },
            label = { Text(stringResource(R.string.target_home)) },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = selected == WallpaperTarget.LOCK,
            onClick = { onSelect(WallpaperTarget.LOCK) },
            label = { Text(stringResource(R.string.target_lock)) },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = selected == WallpaperTarget.BOTH,
            onClick = { onSelect(WallpaperTarget.BOTH) },
            label = { Text(stringResource(R.string.target_both)) },
            modifier = Modifier.weight(1f),
        )
    }
}
