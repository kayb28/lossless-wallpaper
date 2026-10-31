package com.kayb28.losslesswallpaper.core

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

data class ImageInfo(val width: Int, val height: Int, val mimeType: String?) {
    val formatLabel: String
        get() = when (mimeType) {
            "image/png" -> "PNG"
            "image/jpeg" -> "JPEG"
            "image/webp" -> "WebP"
            "image/heif", "image/heic" -> "HEIF"
            else -> mimeType?.removePrefix("image/")?.uppercase() ?: "?"
        }
}

object ImageMeta {

    /**
     * Lee SOLO las cabeceras del archivo (inJustDecodeBounds = true).
     * No se decodifica ningún píxel y no se crea ningún Bitmap: únicamente
     * se necesita la resolución y el formato para el indicador de calidad.
     * El wallpaper siempre se aplica desde el archivo original (ver WallpaperApplier).
     */
    fun readInfo(resolver: ContentResolver, uri: Uri): ImageInfo? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            ImageInfo(opts.outWidth, opts.outHeight, opts.outMimeType)
        }
    }.getOrNull()

    /**
     * Decodifica una versión REDUCIDA solo para la vista previa en pantalla.
     * Este bitmap es exclusivamente visual y jamás se usa para aplicar el fondo;
     * el archivo original se pasa byte a byte a WallpaperManager.setStream().
     */
    fun decodePreview(resolver: ContentResolver, uri: Uri, maxDimension: Int = 1440): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= maxDimension ||
            bounds.outHeight / (sample * 2) >= maxDimension
        ) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    /** Decodes a bounded working copy for composition; the selected source file is never written. */
    fun decodeForComposition(resolver: ContentResolver, uri: Uri, maxDimension: Int = 3072): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val limit = maxDimension.coerceIn(512, 4096)
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > limit * 1.25f) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}
