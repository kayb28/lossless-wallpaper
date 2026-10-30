package com.kayb28.losslesswallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImageInfo(
    val width: Int,
    val height: Int,
    val mimeType: String?,
)

data class ScreenInfo(
    val widthPx: Int,
    val heightPx: Int,
    val desiredMinWidth: Int,
    val desiredMinHeight: Int,
)

data class QualityReport(
    val image: ImageInfo,
    val screen: ScreenInfo,
    val imageMegapixels: Double,
    val screenMegapixels: Double,
    val coversScreen: Boolean,
    val coversDesired: Boolean,
    val warning: String?,
)

object ImageInspector {

    suspend fun readBounds(context: Context, uri: Uri): ImageInfo? = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, opts)
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return@withContext null
        ImageInfo(opts.outWidth, opts.outHeight, mime)
    }

    fun screenInfo(context: Context): ScreenInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val wallpaper = android.app.WallpaperManager.getInstance(context)
        return ScreenInfo(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            desiredMinWidth = wallpaper.desiredMinimumWidth.coerceAtLeast(metrics.widthPixels),
            desiredMinHeight = wallpaper.desiredMinimumHeight.coerceAtLeast(metrics.heightPixels),
        )
    }

    fun qualityReport(image: ImageInfo, screen: ScreenInfo): QualityReport {
        val coversScreen = image.width >= screen.widthPx && image.height >= screen.heightPx
        val coversDesired =
            image.width >= screen.desiredMinWidth && image.height >= screen.desiredMinHeight
        val warning = when {
            !coversScreen ->
                "La imagen (${image.width}×${image.height}) es más pequeña que la pantalla " +
                    "(${screen.widthPx}×${screen.heightPx}). El sistema puede ampliarla y se notará pérdida de nitidez. No hay upscale automático en esta app."
            !coversDesired ->
                "La imagen cubre la pantalla visible, pero es menor que el tamaño deseado del sistema " +
                    "(${screen.desiredMinWidth}×${screen.desiredMinHeight}). En algunos dispositivos el recorte del launcher puede verse menos nítido."
            else -> null
        }
        return QualityReport(
            image = image,
            screen = screen,
            imageMegapixels = image.width * image.height / 1_000_000.0,
            screenMegapixels = screen.widthPx * screen.heightPx / 1_000_000.0,
            coversScreen = coversScreen,
            coversDesired = coversDesired,
            warning = warning,
        )
    }

    /** Crop rectangle (in image pixels) matching the screen aspect ratio, centered. No scaling. */
    fun centeredCropRect(imageW: Int, imageH: Int, screenW: Int, screenH: Int): android.graphics.Rect {
        val screenAspect = screenW.toFloat() / screenH.toFloat()
        val imageAspect = imageW.toFloat() / imageH.toFloat()
        return if (imageAspect > screenAspect) {
            // Image is wider: crop sides
            val cropW = (imageH * screenAspect).toInt().coerceAtMost(imageW)
            val left = (imageW - cropW) / 2
            android.graphics.Rect(left, 0, left + cropW, imageH)
        } else {
            // Image is taller: crop top/bottom
            val cropH = (imageW / screenAspect).toInt().coerceAtMost(imageH)
            val top = (imageH - cropH) / 2
            android.graphics.Rect(0, top, imageW, top + cropH)
        }
    }
}
