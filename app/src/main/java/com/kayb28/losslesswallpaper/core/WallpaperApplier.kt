package com.kayb28.losslesswallpaper.core

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class WallpaperTarget(val flags: Int) {
    /** Pantalla de inicio. */
    HOME(WallpaperManager.FLAG_SYSTEM),

    /** Pantalla de bloqueo. */
    LOCK(WallpaperManager.FLAG_LOCK),

    /** Ambas pantallas. */
    BOTH(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

object WallpaperApplier {

    /**
     * REGLA #1 — La imagen se aplica BYTE A BYTE desde el archivo original:
     *
     *   ContentResolver.openInputStream(uri) -> WallpaperManager.setStream(...)
     *
     * - Sin Bitmap intermedio, sin recompresión, sin JPEG.
     * - Sin reescalado por nuestra parte: el archivo viaja tal cual (PNG/WebP/JPG).
     * - El recorte se comunica con `visibleCropHint`: un Rect en píxeles de la
     *   imagen con la proporción EXACTA de la pantalla. Recortar no recompime:
     *   el sistema toma la zona indicada del archivo original.
     * - Si la imagen es menor que la pantalla, el sistema puede reescalarla;
     *   la app lo avisa en pantalla y NUNCA hace upscale por su cuenta.
     */
    suspend fun apply(context: Context, uri: Uri, crop: RectI?, target: WallpaperTarget): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val wallpaperManager = WallpaperManager.getInstance(context)
                val hint = crop?.let { Rect(it.left, it.top, it.right, it.bottom) }

                when (target) {
                    WallpaperTarget.HOME -> setOriginal(wallpaperManager, context, uri, hint, WallpaperManager.FLAG_SYSTEM)
                    WallpaperTarget.LOCK -> setOriginal(wallpaperManager, context, uri, hint, WallpaperManager.FLAG_LOCK)
                    WallpaperTarget.BOTH -> {
                        // Dos llamadas separadas: algunos OEMs manejan mal los
                        // flags combinados y solo aplicaban una pantalla.
                        setOriginal(wallpaperManager, context, uri, hint, WallpaperManager.FLAG_SYSTEM)
                        setOriginal(wallpaperManager, context, uri, hint, WallpaperManager.FLAG_LOCK)
                    }
                }
            }
        }

    private fun setOriginal(
        wallpaperManager: WallpaperManager,
        context: Context,
        uri: Uri,
        hint: Rect?,
        flags: Int,
    ) {
        // InputStream nuevo en cada llamada (no se reutiliza uno consumido).
        context.contentResolver.openInputStream(uri)?.use { input ->
            wallpaperManager.setStream(input, hint, true, flags)
        } ?: error("No se pudo abrir la imagen seleccionada")
    }
}
