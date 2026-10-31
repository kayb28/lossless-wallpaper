package com.kayb28.losslesswallpaper.core

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.math.min
import kotlin.math.sqrt

enum class WallpaperTarget(val flags: Int) {
    HOME(WallpaperManager.FLAG_SYSTEM),
    LOCK(WallpaperManager.FLAG_LOCK),
    BOTH(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

object WallpaperApplier {
    /** Crop mode streams the original bytes. Other modes compose a temporary copy and never edit the source. */
    suspend fun apply(
        context: Context,
        uri: Uri,
        crop: RectI?,
        target: WallpaperTarget,
        mode: WallpaperMode = WallpaperMode.CROP,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0,
        fitBackgroundColor: Int = Color.rgb(18, 20, 24),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val manager = WallpaperManager.getInstance(context)
            if (mode == WallpaperMode.CROP) {
                applyOriginal(manager, context, uri, crop, target)
            } else {
                val screen = resolveScreenInfo(context)
                val requestedWidth = targetWidth.takeIf { it > 0 } ?: screen.desiredWidth
                val requestedHeight = targetHeight.takeIf { it > 0 } ?: screen.desiredHeight
                val (outputWidth, outputHeight) = boundedOutputSize(requestedWidth, requestedHeight)
                val maxSourceDimension = min(maxOf(outputWidth, outputHeight), 3072)
                val source = ImageMeta.decodeForComposition(context.contentResolver, uri, maxSourceDimension)
                    ?: error("No se pudo decodificar la imagen para crear el fondo")
                try {
                    val scaledCrop = crop?.scaleTo(
                        fromWidth = sourceWidth.takeIf { it > 0 } ?: source.width,
                        fromHeight = sourceHeight.takeIf { it > 0 } ?: source.height,
                        toWidth = source.width,
                        toHeight = source.height,
                    )
                    val composed = WallpaperComposer.compose(
                        source = source,
                        targetWidth = outputWidth,
                        targetHeight = outputHeight,
                        mode = mode,
                        contentRect = scaledCrop,
                        fitBackgroundColor = fitBackgroundColor,
                    )
                    val temporaryFile = File.createTempFile("wallpaper-composite-", ".png", context.cacheDir)
                    try {
                        temporaryFile.outputStream().use { output ->
                            check(composed.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                "No se pudo guardar la composición temporal"
                            }
                        }
                        applyFile(manager, temporaryFile, target)
                    } finally {
                        temporaryFile.delete()
                        composed.recycle()
                    }
                } finally {
                    source.recycle()
                }
            }
        }
    }

    private fun applyOriginal(
        manager: WallpaperManager,
        context: Context,
        uri: Uri,
        crop: RectI?,
        target: WallpaperTarget,
    ) {
        val hint = crop?.let { Rect(it.left, it.top, it.right, it.bottom) }
        when (target) {
            WallpaperTarget.HOME -> setOriginal(manager, context, uri, hint, WallpaperManager.FLAG_SYSTEM)
            WallpaperTarget.LOCK -> setOriginal(manager, context, uri, hint, WallpaperManager.FLAG_LOCK)
            WallpaperTarget.BOTH -> {
                setOriginal(manager, context, uri, hint, WallpaperManager.FLAG_SYSTEM)
                setOriginal(manager, context, uri, hint, WallpaperManager.FLAG_LOCK)
            }
        }
    }

    private fun applyFile(manager: WallpaperManager, file: File, target: WallpaperTarget) {
        fun set(flags: Int) {
            FileInputStream(file).use { manager.setStream(it, null, true, flags) }
        }
        when (target) {
            WallpaperTarget.HOME -> set(WallpaperManager.FLAG_SYSTEM)
            WallpaperTarget.LOCK -> set(WallpaperManager.FLAG_LOCK)
            WallpaperTarget.BOTH -> {
                set(WallpaperManager.FLAG_SYSTEM)
                set(WallpaperManager.FLAG_LOCK)
            }
        }
    }

    private fun setOriginal(manager: WallpaperManager, context: Context, uri: Uri, hint: Rect?, flags: Int) {
        context.contentResolver.openInputStream(uri)?.use { manager.setStream(it, hint, true, flags) }
            ?: error("No se pudo abrir la imagen seleccionada")
    }

    private fun boundedOutputSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Resolución objetivo inválida: ${width}×$height" }
        val pixelLimit = 8_000_000.0
        val dimensionLimit = 4096.0
        val scale = min(
            1.0,
            min(sqrt(pixelLimit / (width.toDouble() * height)), dimensionLimit / maxOf(width, height)),
        )
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    private fun RectI.scaleTo(fromWidth: Int, fromHeight: Int, toWidth: Int, toHeight: Int): RectI {
        val left = (left.toFloat() * toWidth / fromWidth).toInt().coerceIn(0, toWidth - 1)
        val top = (top.toFloat() * toHeight / fromHeight).toInt().coerceIn(0, toHeight - 1)
        val right = (right.toFloat() * toWidth / fromWidth).toInt().coerceIn(left + 1, toWidth)
        val bottom = (bottom.toFloat() * toHeight / fromHeight).toInt().coerceIn(top + 1, toHeight)
        return RectI(left, top, right, bottom)
    }
}
