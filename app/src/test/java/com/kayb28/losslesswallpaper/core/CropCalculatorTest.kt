package com.kayb28.losslesswallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class CropCalculatorTest {

    // Pantalla típica de teléfono: 1080 x 2400
    private val targetW = 1080
    private val targetH = 2400

    @Test
    fun `imagen con la misma proporcion usa la imagen completa`() {
        val spec = CropCalculator.computeCrop(2160, 4800, targetW, targetH)
        assertNull(spec.cropRect)
        assertFalse(spec.needsUpscale)
        assertEquals(1.0f, spec.systemScale, 0.001f)
    }

    @Test
    fun `misma proporcion pero menor que la pantalla avisa reescalado`() {
        val spec = CropCalculator.computeCrop(540, 1200, targetW, targetH)
        assertNull(spec.cropRect)
        assertTrue(spec.needsUpscale)
        assertEquals(2.0f, spec.systemScale, 0.001f)
    }

    @Test
    fun `imagen mas ancha recorta a lo ancho con alto completo`() {
        val spec = CropCalculator.computeCrop(4000, 2400, targetW, targetH)
        assertNotNull(spec.cropRect)
        val rect = spec.cropRect!!
        assertEquals(2400, rect.height)
        // w = round(2400 * (1080/2400)) = 1080
        assertEquals(1080, rect.width)
        // centrado
        assertEquals((4000 - 1080) / 2, rect.left)
        assertFalse(spec.needsUpscale)
        // proporcion exacta de la pantalla
        val ratio = rect.width.toFloat() / rect.height
        abs(ratio - targetW.toFloat() / targetH).let { assertTrue(it < 0.001f) }
    }

    @Test
    fun `imagen mas alta recorta a lo alto con ancho completo`() {
        val spec = CropCalculator.computeCrop(1080, 4000, targetW, targetH)
        assertNotNull(spec.cropRect)
        val rect = spec.cropRect!!
        assertEquals(1080, rect.width)
        // h = round(1080 / (1080/2400)) = 2400
        assertEquals(2400, rect.height)
        assertEquals((4000 - 2400) / 2, rect.top)
        assertFalse(spec.needsUpscale)
    }

    @Test
    fun `imagen pequenia en ambos ejes marca upscale y mantiene proporcion`() {
        val spec = CropCalculator.computeCrop(800, 1600, targetW, targetH)
        assertNotNull(spec.cropRect)
        val rect = spec.cropRect!!
        // ratio imagen 0.5 > ratio objetivo 0.45 -> recorte horizontal
        assertEquals(1600, rect.height)
        assertEquals(720, rect.width)
        assertTrue(spec.needsUpscale)
        assertEquals(1.5f, spec.systemScale, 0.001f)
        // el recorte sigue dentro de la imagen
        assertTrue(rect.left >= 0 && rect.right <= 800)
    }

    @Test
    fun `movedCrop respeta los limites de la imagen`() {
        val spec = CropCalculator.computeCrop(4000, 2400, targetW, targetH)
        // arrastrar todo a la izquierda
        val farLeft = CropCalculator.movedCrop(spec, 0f, 1200f)
        assertEquals(0, farLeft!!.left)
        // todo a la derecha
        val farRight = CropCalculator.movedCrop(spec, 4000f, 1200f)
        assertEquals(4000, farRight!!.right)
        // por encima / por debajo (aquí el alto es completo, no se mueve en Y)
        val anyY = CropCalculator.movedCrop(spec, 2000f, -500f)
        assertEquals(0, anyY!!.top)
        assertEquals(2400, anyY.bottom)
    }

    @Test
    fun `movedCrop con spec sin recorte devuelve null`() {
        val spec = CropCalculator.computeCrop(2160, 4800, targetW, targetH)
        assertNull(CropCalculator.movedCrop(spec, 100f, 100f))
    }

    @Test
    fun `movedCrop nunca sale de la imagen`() {
        val spec = CropCalculator.computeCrop(800, 1600, targetW, targetH)
        repeat(50) { i ->
            val cx = (-2000f + i * 137f)
            val cy = (-1500f + i * 89f)
            val rect = CropCalculator.movedCrop(spec, cx, cy)!!
            assertTrue(rect.left >= 0, "left fuera: ${rect.left}")
            assertTrue(rect.top >= 0, "top fuera: ${rect.top}")
            assertTrue(rect.right <= 800, "right fuera: ${rect.right}")
            assertTrue(rect.bottom <= 1600, "bottom fuera: ${rect.bottom}")
        }
    }

    @Test
    fun `dimensiones invalidas lanzan excepcion`() {
        assertThrows(IllegalArgumentException::class.java) {
            CropCalculator.computeCrop(0, 100, targetW, targetH)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CropCalculator.computeCrop(100, 100, 0, 200)
        }
    }

    @Test
    fun `recorte cuadrado en pantalla cuadrada`() {
        val spec = CropCalculator.computeCrop(2000, 3000, 1000, 1000)
        assertNotNull(spec.cropRect)
        val rect = spec.cropRect!!
        assertEquals(2000, rect.width)
        assertEquals(2000, rect.height)
        assertEquals(500, rect.top)
        assertEquals(3000, rect.bottom)
    }
}
