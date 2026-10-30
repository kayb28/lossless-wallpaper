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
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val flags = when (target) {
                WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }

            // Open a fresh stream from the original document for each target that needs it.
            fun applyStream() {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wm.setStream(
                            /* data = */ input,
                            /* visibleCropHint = */ null,
                            /* allowBackup = */ true,
                            /* which = */ flags,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        wm.setStream(input)
                    }
                } ?: error("No se pudo abrir el archivo seleccionado")
            }

            // FLAG_SYSTEM | FLAG_LOCK in one call is supported on API 24+.
            // Some OEMs ignore combined flags; apply twice if BOTH fails halfway.
            if (target == WallpaperTarget.BOTH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    wm.setStream(input, null, true, WallpaperManager.FLAG_SYSTEM)
                } ?: error("No se pudo abrir el archivo seleccionado")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    wm.setStream(input, null, true, WallpaperManager.FLAG_LOCK)
                } ?: error("No se pudo abrir el archivo seleccionado")
            } else {
                applyStream()
            }
        }
    }
}
