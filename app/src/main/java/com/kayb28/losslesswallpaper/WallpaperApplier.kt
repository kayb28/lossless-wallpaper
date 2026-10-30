package com.kayb28.losslesswallpaper

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class WallpaperTarget {
    HOME,
    LOCK,
    BOTH,
}

/**
 * Applies the **original file bytes** as wallpaper via [WallpaperManager.setStream].
 * Never decodes to Bitmap and never recompresses to JPEG/PNG.
 */
object WallpaperApplier {

    suspend fun apply(
        context: Context,
        uri: Uri,
        target: WallpaperTarget,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val wm = WallpaperManager.getInstance(context)

            fun openStream() = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                when (target) {
                    WallpaperTarget.HOME -> {
                        openStream().use { input ->
                            wm.setStream(input, null, true, WallpaperManager.FLAG_SYSTEM)
                        }
                    }
                    WallpaperTarget.LOCK -> {
                        openStream().use { input ->
                            wm.setStream(input, null, true, WallpaperManager.FLAG_LOCK)
                        }
                    }
                    WallpaperTarget.BOTH -> {
                        // Apply separately: some OEMs mishandle combined flags.
                        openStream().use { input ->
                            wm.setStream(input, null, true, WallpaperManager.FLAG_SYSTEM)
                        }
                        openStream().use { input ->
                            wm.setStream(input, null, true, WallpaperManager.FLAG_LOCK)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                openStream().use { input -> wm.setStream(input) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
