package com.kayb28.losslesswallpaper.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/** Detects only symmetric, flat-color edge bands whose removal improves screen-ratio fit. */
object ImageBorderDetector {
    fun detect(bitmap: Bitmap, targetAspect: Float, sensitivity: Float): RectI? {
        if (bitmap.width < 64 || bitmap.height < 64 || targetAspect <= 0f) return null
        val tolerance = 5.5 + 20.0 * sensitivity.coerceIn(0f, 1f)
        var left = 0
        var top = 0
        var right = bitmap.width
        var bottom = bitmap.height

        val horizontal = pairedBands(bitmap, horizontal = true, tolerance = tolerance)
        if (horizontal != null) {
            top += horizontal.first
            bottom -= horizontal.second
        }
        val vertical = pairedBands(bitmap, horizontal = false, tolerance = tolerance)
        if (vertical != null) {
            left += vertical.first
            right -= vertical.second
        }

        if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) return null
        val before = abs(ln((bitmap.width.toDouble() / bitmap.height) / targetAspect.toDouble()))
        val after = abs(ln(((right - left).toDouble() / (bottom - top)) / targetAspect.toDouble()))
        if (before < 0.08f || after >= before * 0.8f) return null
        return RectI(left, top, right, bottom)
    }

    private fun pairedBands(bitmap: Bitmap, horizontal: Boolean, tolerance: Double): Pair<Int, Int>? {
        val axisSize = if (horizontal) bitmap.height else bitmap.width
        val crossSize = if (horizontal) bitmap.width else bitmap.height
        val limit = (axisSize * 0.18f).toInt().coerceAtLeast(1)
        val minimum = (axisSize * 0.012f).toInt().coerceAtLeast(3)
        val sampleStep = (crossSize / 400).coerceAtLeast(1)
        val first = lineStats(bitmap, horizontal, 0, sampleStep)
        val last = lineStats(bitmap, horizontal, axisSize - 1, sampleStep)
        if (!first.isUniform(tolerance) || !last.isUniform(tolerance) || !first.matches(last, tolerance * 1.7)) {
            return null
        }

        fun band(fromStart: Boolean, reference: Stats): Int {
            var count = 0
            for (offset in 0 until limit) {
                val position = if (fromStart) offset else axisSize - 1 - offset
                val stats = lineStats(bitmap, horizontal, position, sampleStep)
                if (!stats.isUniform(tolerance) || !stats.matches(reference, tolerance * 1.7)) break
                count++
            }
            return count
        }

        val startBand = band(true, first)
        val endBand = band(false, last)
        if (startBand < minimum || endBand < minimum || startBand + endBand >= axisSize / 2) return null
        val innerStart = lineStats(bitmap, horizontal, startBand, sampleStep)
        val innerEnd = lineStats(bitmap, horizontal, axisSize - 1 - endBand, sampleStep)
        val contrastStart = !innerStart.matches(first, tolerance * 1.4) || !innerStart.isUniform(tolerance)
        val contrastEnd = !innerEnd.matches(last, tolerance * 1.4) || !innerEnd.isUniform(tolerance)
        if (!contrastStart || !contrastEnd) return null
        return startBand to endBand
    }

    private data class Stats(val red: Double, val green: Double, val blue: Double, val deviation: Double) {
        fun isUniform(tolerance: Double) = deviation <= tolerance

        fun matches(other: Stats, tolerance: Double): Boolean =
            (abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)) / 3.0 <= tolerance
    }

    private fun lineStats(bitmap: Bitmap, horizontal: Boolean, position: Int, step: Int): Stats {
        val length = if (horizontal) bitmap.width else bitmap.height
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var redSquared = 0.0
        var greenSquared = 0.0
        var blueSquared = 0.0
        var count = 0
        var index = 0
        while (index < length) {
            val pixel = if (horizontal) bitmap.getPixel(index, position) else bitmap.getPixel(position, index)
            val r = (pixel shr 16 and 0xFF).toDouble()
            val g = (pixel shr 8 and 0xFF).toDouble()
            val b = (pixel and 0xFF).toDouble()
            red += r
            green += g
            blue += b
            redSquared += r * r
            greenSquared += g * g
            blueSquared += b * b
            count++
            index += step
        }
        val meanRed = red / count
        val meanGreen = green / count
        val meanBlue = blue / count
        val variance = (
            (redSquared / count - meanRed * meanRed).coerceAtLeast(0.0) +
                (greenSquared / count - meanGreen * meanGreen).coerceAtLeast(0.0) +
                (blueSquared / count - meanBlue * meanBlue).coerceAtLeast(0.0)
            ) / 3.0
        return Stats(meanRed, meanGreen, meanBlue, sqrt(variance))
    }
}
