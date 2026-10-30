package com.kayb28.losslesswallpaper.core

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

data class ScreenInfo(
    /** Resolución real de la pantalla (portrait: ancho < alto). */
    val screenWidth: Int,
    val screenHeight: Int,
    /** Lo que pide el sistema (getDesiredMinimumWidth/Height); suele ser >= pantalla. */
    val desiredWidth: Int,
    val desiredHeight: Int,
)

fun resolveScreenInfo(context: Context): ScreenInfo {
    val windowManager = context.getSystemService(WindowManager::class.java)

    val size = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager.maximumWindowMetrics.bounds
        Point(bounds.width(), bounds.height())
    } else {
        @Suppress("DEPRECATION")
        Point().also { windowManager.defaultDisplay.getRealSize(it) }
    }

    // El recorte se calcula en orientación vertical (uso normal de wallpaper).
    val screenWidth = minOf(size.x, size.y)
    val screenHeight = maxOf(size.x, size.y)

    val wallpaperManager = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
    val desiredWidth = wallpaperManager?.desiredMinimumWidth?.takeIf { it > 0 } ?: screenWidth
    val desiredHeight = wallpaperManager?.desiredMinimumHeight?.takeIf { it > 0 } ?: screenHeight

    return ScreenInfo(screenWidth, screenHeight, desiredWidth, desiredHeight)
}
