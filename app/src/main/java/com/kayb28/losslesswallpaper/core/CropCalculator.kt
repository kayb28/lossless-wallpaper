package com.kayb28.losslesswallpaper.core

/**
 * Rectángulo en coordenadas de píxeles de la imagen original.
 * Sin dependencias de Android para poder testearlo en JVM puro.
 */
data class RectI(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Resultado del cálculo de recorte.
 *
 * [cropRect] es la zona de la imagen con la proporción EXACTA de la pantalla.
 * Es `null` cuando la imagen ya tiene esa proporción (se usa la imagen completa).
 * Nunca se reescala: el rectángulo siempre vive dentro de la imagen original.
 */
data class CropSpec(
    val imageWidth: Int,
    val imageHeight: Int,
    val cropRect: RectI?,
    val targetWidth: Int,
    val targetHeight: Int,
) {
    val cropWidth: Int get() = cropRect?.width ?: imageWidth
    val cropHeight: Int get() = cropRect?.height ?: imageHeight

    /** true si el recorte no cubre la resolución objetivo: el sistema lo reescalará. */
    val needsUpscale: Boolean
        get() = cropWidth < targetWidth || cropHeight < targetHeight

    /** Escala que aplicará el sistema; 1.0 = relación 1:1 (sin reescalado). */
    val systemScale: Float
        get() = maxOf(targetWidth.toFloat() / cropWidth, targetHeight.toFloat() / cropHeight)
}

object CropCalculator {

    private const val EPSILON = 1e-4f

    /**
     * Calcula el recorte centrado con la proporción exacta de [targetWidth] x [targetHeight].
     *
     * - Imagen con la misma proporción -> imagen completa (cropRect = null).
     * - Imagen más ancha -> se recorta a lo ancho, alto completo.
     * - Imagen más alta -> se recorta a lo alto, ancho completo.
     * - Nunca se agranda ni se reduce la imagen: el rectángulo se limita a sus píxeles reales.
     */
    fun computeCrop(imageWidth: Int, imageHeight: Int, targetWidth: Int, targetHeight: Int): CropSpec {
        require(imageWidth > 0 && imageHeight > 0) { "Dimensiones de imagen inválidas: ${imageWidth}x${imageHeight}" }
        require(targetWidth > 0 && targetHeight > 0) { "Dimensiones objetivo inválidas: ${targetWidth}x${targetHeight}" }

        val targetRatio = targetWidth.toFloat() / targetHeight
        val imageRatio = imageWidth.toFloat() / imageHeight

        val rect: RectI? = when {
            kotlin.math.abs(imageRatio - targetRatio) <= EPSILON * maxOf(imageRatio, targetRatio) -> {
                null // misma proporción: la imagen completa ya encaja
            }
            imageRatio > targetRatio -> {
                // Imagen más ancha: recorte vertical completo, ancho a proporción exacta.
                var w = kotlin.math.round(imageHeight * targetRatio)
                if (w > imageWidth) w = imageWidth
                if (w < 1) w = 1
                val left = (imageWidth - w) / 2
                RectI(left, 0, left + w, imageHeight)
            }
            else -> {
                // Imagen más alta: recorte horizontal completo, alto a proporción exacta.
                var h = kotlin.math.round(imageWidth / targetRatio)
                if (h > imageHeight) h = imageHeight
                if (h < 1) h = 1
                val top = (imageHeight - h) / 2
                RectI(0, top, imageWidth, top + h)
            }
        }

        return CropSpec(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            cropRect = rect,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )
    }

    /**
     * Recorte desplazado por el usuario. El centro se ajusta para que el rectángulo
     * SIEMPRE quede dentro de la imagen original (sin márgenes ni reescalado).
     */
    fun movedCrop(spec: CropSpec, centerX: Float, centerY: Float): RectI? {
        val rect = spec.cropRect ?: return null
        val w = rect.width
        val h = rect.height
        if (w >= spec.imageWidth && h >= spec.imageHeight) return rect

        val cx = if (w >= spec.imageWidth) {
            spec.imageWidth / 2f
        } else {
            centerX.coerceIn(w / 2f, spec.imageWidth - w / 2f)
        }
        val cy = if (h >= spec.imageHeight) {
            spec.imageHeight / 2f
        } else {
            centerY.coerceIn(h / 2f, spec.imageHeight - h / 2f)
        }

        val left = kotlin.math.round(cx - w / 2f).coerceIn(0, spec.imageWidth - w)
        val top = kotlin.math.round(cy - h / 2f).coerceIn(0, spec.imageHeight - h)
        return RectI(left, top, left + w, top + h)
    }
}
