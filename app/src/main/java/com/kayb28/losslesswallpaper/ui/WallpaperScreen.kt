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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
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
import com.kayb28.losslesswallpaper.core.ImageBorderDetector
import com.kayb28.losslesswallpaper.core.ImageInfo
import com.kayb28.losslesswallpaper.core.ImageMeta
import com.kayb28.losslesswallpaper.core.RectI
import com.kayb28.losslesswallpaper.core.ScreenInfo
import com.kayb28.losslesswallpaper.core.WallpaperApplier
import com.kayb28.losslesswallpaper.core.WallpaperComposer
import com.kayb28.losslesswallpaper.core.WallpaperMode
import com.kayb28.losslesswallpaper.core.WallpaperTarget
import com.kayb28.losslesswallpaper.core.resolveScreenInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
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
    var mode by remember { mutableStateOf(WallpaperMode.CROP) }
    var autoDetectMargins by remember { mutableStateOf(true) }
    var detectionSensitivity by remember { mutableStateOf(0.5f) }
    var detectedMargins by remember { mutableStateOf<RectI?>(null) }
    var applying by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0, neverEqualPolicy()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            cropCenter = null
            detectedMargins = null
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

    LaunchedEffect(preview, info, screen, autoDetectMargins, detectionSensitivity) {
        val sourcePreview = preview
        val imageInfo = info
        val screenInfo = screen
        if (!autoDetectMargins || sourcePreview == null || imageInfo == null || screenInfo == null) {
            detectedMargins = null
        } else {
            val previewBounds = withContext(Dispatchers.Default) {
                ImageBorderDetector.detect(
                    sourcePreview,
                    screenInfo.screenWidth.toFloat() / screenInfo.screenHeight,
                    detectionSensitivity,
                )
            }
            detectedMargins = previewBounds?.scaleRect(
                fromWidth = sourcePreview.width,
                fromHeight = sourcePreview.height,
                toWidth = imageInfo.width,
                toHeight = imageInfo.height,
            )
        }
    }

    val contentBounds = remember(info, detectedMargins) {
        val imageInfo = info ?: return@remember null
        detectedMargins ?: RectI(0, 0, imageInfo.width, imageInfo.height)
    }

    val cropBaseSpec = remember(info, screen, contentBounds) {
        val imageInfo = info
        val screenInfo = screen
        val bounds = contentBounds
        if (imageInfo != null && screenInfo != null && bounds != null) {
            CropCalculator.computeCrop(bounds.width, bounds.height, screenInfo.screenWidth, screenInfo.screenHeight)
        } else {
            null
        }
    }

    val activeCrop = remember(cropBaseSpec, contentBounds, detectedMargins, cropCenter) {
        val baseSpec = cropBaseSpec ?: return@remember null
        val bounds = contentBounds ?: return@remember null
        val relative = baseSpec.cropRect
        if (relative == null) {
            if (detectedMargins != null) bounds else null
        } else {
            val center = cropCenter?.let { Offset(it.x - bounds.left, it.y - bounds.top) }
                ?: Offset(relative.centerX, relative.centerY)
            CropCalculator.movedCrop(baseSpec, center.x, center.y)?.offset(bounds.left, bounds.top)
        }
    }

    val spec = remember(info, screen, activeCrop) {
        val i = info
        val s = screen
        if (i != null && s != null) {
            CropSpec(i.width, i.height, activeCrop, s.screenWidth, s.screenHeight)
        } else {
            null
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

            // Copias locales: permiten smart cast a no-nulo sin depender de
            // propiedades delegadas (mutableStateOf), que Kotlin no puede
            // smart-castear de forma segura.
            val currentPreview = preview
            val currentSpec = spec
            val currentUri = uri
            val currentInfo = info
            val currentScreen = screen
            val currentContentBounds = contentBounds

            if (
                currentUri == null || currentPreview == null || currentSpec == null ||
                currentInfo == null || currentScreen == null || currentContentBounds == null
            ) {
                EmptyState(
                    modifier = Modifier.fillMaxWidth(),
                    onPick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            } else {
                val compositionArea = if (mode == WallpaperMode.FIT) {
                    RectI(0, 0, currentInfo.width, currentInfo.height)
                } else {
                    currentContentBounds
                }
                PreviewCard(
                    bitmap = currentPreview,
                    spec = currentSpec,
                    cropRect = activeCrop,
                    mode = mode,
                    targetWidth = currentScreen.screenWidth,
                    targetHeight = currentScreen.screenHeight,
                    contentRect = compositionArea,
                    onMove = { dx, dy ->
                        val bounds = activeCrop ?: currentContentBounds
                        val base = cropCenter ?: Offset(bounds.centerX, bounds.centerY)
                        cropCenter = Offset(base.x + dx, base.y + dy)
                    },
                )
                Spacer(Modifier.height(12.dp))
                ModeSelector(selected = mode, onSelect = { mode = it })
                if (mode != WallpaperMode.FIT) {
                    Spacer(Modifier.height(8.dp))
                    AutoMarginControls(
                        enabled = autoDetectMargins,
                        sensitivity = detectionSensitivity,
                        hasDetected = detectedMargins != null,
                        onEnabledChange = { autoDetectMargins = it },
                        onSensitivityChange = { detectionSensitivity = it },
                    )
                }
                Spacer(Modifier.height(12.dp))
                QualityPanel(
                    info = currentInfo,
                    screen = currentScreen,
                    spec = currentSpec,
                    cropRect = activeCrop,
                    mode = mode,
                    fitArea = compositionArea,
                )
                Spacer(Modifier.height(12.dp))
                TargetSelector(selected = target, onSelect = { target = it })
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            applying = true
                            val sourceArea = if (mode == WallpaperMode.CROP) activeCrop else compositionArea
                            val result = WallpaperApplier.apply(
                                context = context,
                                uri = currentUri,
                                crop = sourceArea,
                                target = target,
                                mode = mode,
                                targetWidth = currentScreen.screenWidth,
                                targetHeight = currentScreen.screenHeight,
                                sourceWidth = currentInfo.width,
                                sourceHeight = currentInfo.height,
                            )
                            applying = false
                            val message = if (result.isSuccess) {
                                context.getString(
                                    if (mode == WallpaperMode.CROP) R.string.applied_original_ok else R.string.applied_composite_ok,
                                )
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
                    onClick = { uri = null; info = null; preview = null; cropCenter = null; detectedMargins = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.change_image))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(if (mode == WallpaperMode.CROP) R.string.preview_note_crop else R.string.preview_note_effect),
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
private fun ModeSelector(selected: WallpaperMode, onSelect: (WallpaperMode) -> Unit) {
    Column {
        Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WallpaperMode.values().forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    label = { Text(stringResource(mode.labelResource())) },
                )
            }
        }
    }
}

@Composable
private fun AutoMarginControls(
    enabled: Boolean,
    sensitivity: Float,
    hasDetected: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSensitivityChange: (Float) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_margins), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(
                            when {
                                !enabled -> R.string.margins_disabled
                                hasDetected -> R.string.margins_found
                                else -> R.string.margins_not_found
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                Text(stringResource(R.string.sensitivity), style = MaterialTheme.typography.labelMedium)
                Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = 0f..1f, steps = 1)
            }
        }
    }
}

private fun WallpaperMode.labelResource(): Int = when (this) {
    WallpaperMode.CROP -> R.string.mode_crop
    WallpaperMode.FIT -> R.string.mode_fit
    WallpaperMode.AUTO_COLOR -> R.string.mode_auto_color
    WallpaperMode.BLACK -> R.string.mode_black
    WallpaperMode.BLUR -> R.string.mode_blur
    WallpaperMode.EXTEND_EDGES -> R.string.mode_extend_edges
}

private fun RectI.offset(dx: Int, dy: Int): RectI = RectI(left + dx, top + dy, right + dx, bottom + dy)

private fun RectI.scaleRect(fromWidth: Int, fromHeight: Int, toWidth: Int, toHeight: Int): RectI {
    val left = (left.toFloat() * toWidth / fromWidth).toInt().coerceIn(0, toWidth - 1)
    val top = (top.toFloat() * toHeight / fromHeight).toInt().coerceIn(0, toHeight - 1)
    val right = (right.toFloat() * toWidth / fromWidth).toInt().coerceIn(left + 1, toWidth)
    val bottom = (bottom.toFloat() * toHeight / fromHeight).toInt().coerceIn(top + 1, toHeight)
    return RectI(left, top, right, bottom)
}

@Composable
private fun PreviewCard(
    bitmap: Bitmap,
    spec: CropSpec,
    cropRect: RectI?,
    mode: WallpaperMode,
    targetWidth: Int,
    targetHeight: Int,
    contentRect: RectI,
    onMove: (Float, Float) -> Unit,
) {
    // `Bitmap` no expone `id`; remember(bitmaps) compara por identidad (Bitmap no
    // sobrescribe equals), así que un nuevo decodificado => nuevo ImageBitmap.
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var composedPreview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bitmap, mode, targetWidth, targetHeight, contentRect) {
        composedPreview?.recycle()
        composedPreview = null
        if (mode != WallpaperMode.CROP) {
            val outWidth = minOf(targetWidth, 720).coerceAtLeast(1)
            val outHeight = (outWidth.toFloat() * targetHeight / targetWidth).roundToInt().coerceAtLeast(1)
            val previewContent = contentRect.scaleRect(spec.imageWidth, spec.imageHeight, bitmap.width, bitmap.height)
            composedPreview = withContext(Dispatchers.Default) {
                WallpaperComposer.compose(
                    bitmap, outWidth, outHeight, mode, previewContent,
                )
            }
        }
    }
    val composedImage = remember(composedPreview) { composedPreview?.asImageBitmap() }
    var boxSizePx by remember { mutableStateOf(IntSize.Zero) }

    // Escala píxeles de imagen -> píxeles de la vista previa
    val scale = if (boxSizePx.width > 0) boxSizePx.width.toFloat() / spec.imageWidth else 1f

    val ratio = if (mode == WallpaperMode.CROP) {
        spec.imageWidth.toFloat() / spec.imageHeight
    } else {
        targetWidth.toFloat() / targetHeight
    }
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
                // (boxSizePx) a través del delegado en cada gesto, así la escala
                // es siempre la actual y el gesto no se reinicia en cada frame.
                .pointerInput(spec, mode) {
                    if (mode == WallpaperMode.CROP && spec.cropRect != null) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val currentWidth = boxSizePx.width
                            if (currentWidth > 0) {
                                val liveScale = spec.imageWidth.toFloat() / currentWidth
                                onMove(dragAmount.x * liveScale, dragAmount.y * liveScale)
                            }
                        }
                    }
                },
        ) {
            if (mode == WallpaperMode.CROP) {
                Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else if (composedImage != null) {
                Image(
                    bitmap = composedImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            if (mode == WallpaperMode.CROP && cropRect != null && scale > 0f) {
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
            text = if (mode == WallpaperMode.FIT) {
                stringResource(R.string.preview_fit_stretch)
            } else if (mode != WallpaperMode.CROP) {
                stringResource(R.string.preview_composite)
            } else if (cropRect != null) {
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
    mode: WallpaperMode,
    fitArea: RectI,
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
            if (mode != WallpaperMode.CROP) {
                Spacer(Modifier.height(10.dp))
                QualityBanner(
                    backgroundColor = Color(0xFF1B2740),
                    borderColor = Color(0xFF3D5480),
                    textColor = Color(0xFFA8C4F0),
                    title = stringResource(R.string.quality_effect_title),
                    body = stringResource(
                        if (mode == WallpaperMode.FIT) R.string.quality_fit_body else R.string.quality_effect_body,
                    ),
                )
                val widthScale = screen.screenWidth.toFloat() / fitArea.width
                val heightScale = screen.screenHeight.toFloat() / fitArea.height
                val fitScale = if (mode == WallpaperMode.FIT) maxOf(widthScale, heightScale) else minOf(widthScale, heightScale)
                if (mode == WallpaperMode.FIT) {
                    val sourceAspect = fitArea.width.toFloat() / fitArea.height
                    val screenAspect = screen.screenWidth.toFloat() / screen.screenHeight
                    val aspectChange = abs(screenAspect / sourceAspect - 1f) * 100f
                    if (aspectChange >= 2f) {
                        Spacer(Modifier.height(8.dp))
                        QualityBanner(
                            backgroundColor = Color(0xFF3B2F14),
                            borderColor = Color(0xFF8A6D2F),
                            textColor = Color(0xFFE8C97A),
                            title = stringResource(R.string.quality_fit_stretch_title),
                            body = stringResource(
                                R.string.quality_fit_stretch_body,
                                String.format(Locale.US, "%.0f%%", aspectChange),
                            ),
                        )
                    }
                }
                if (fitScale > 1f) {
                    Spacer(Modifier.height(8.dp))
                    QualityBanner(
                        backgroundColor = Color(0xFF3B2F14),
                        borderColor = Color(0xFF8A6D2F),
                        textColor = Color(0xFFE8C97A),
                        title = stringResource(R.string.quality_effect_upscale_title),
                        body = stringResource(
                            R.string.quality_effect_upscale_body,
                            String.format(Locale.US, "×%.2f", fitScale),
                        ),
                    )
                }
                return@Column
            }
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
