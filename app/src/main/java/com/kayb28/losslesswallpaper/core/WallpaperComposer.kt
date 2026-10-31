package com.kayb28.losslesswallpaper.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/** Builds a new in-memory output only for a selected effect; the input bitmap/file is never modified. */
object WallpaperComposer {
    fun compose(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        mode: WallpaperMode,
        contentRect: RectI? = null,
        fitBackgroundColor: Int = Color.rgb(18, 20, 24),
    ): Bitmap {
        require(targetWidth > 0 && targetHeight > 0)
        val bounds = contentRect?.clamped(source.width, source.height)
            ?: RectI(0, 0, source.width, source.height)
        val content = if (bounds.left == 0 && bounds.top == 0 && bounds.right == source.width && bounds.bottom == source.height) {
            source
        } else {
            Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width, bounds.height)
        }
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val full = RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())

        when (mode) {
            WallpaperMode.CROP -> drawCenterCrop(canvas, content, full, paint)
            WallpaperMode.FIT -> {
                canvas.drawColor(fitBackgroundColor)
                drawContained(canvas, content, full, paint)
            }
            WallpaperMode.AUTO_COLOR -> {
                drawEdgeGradient(canvas, content, full, paint)
                drawContained(canvas, content, full, paint)
            }
            WallpaperMode.BLACK -> {
                canvas.drawColor(Color.BLACK)
                drawContained(canvas, content, full, paint)
            }
            WallpaperMode.BLUR -> {
                drawBlurBackground(canvas, content, full, paint)
                drawContained(canvas, content, full, paint)
            }
            WallpaperMode.EXTEND_EDGES -> {
                canvas.drawBitmap(content, null, full, paint)
                drawContained(canvas, content, full, paint)
            }
        }
        if (content !== source) content.recycle()
        return output
    }

    private fun drawContained(canvas: Canvas, bitmap: Bitmap, target: RectF, paint: Paint) {
        val scale = min(1f, min(target.width() / bitmap.width, target.height() / bitmap.height))
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = target.centerX() - width / 2f
        val top = target.centerY() - height / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, target: RectF, paint: Paint) {
        val scale = max(target.width() / bitmap.width, target.height() / bitmap.height)
        val visibleWidth = target.width() / scale
        val visibleHeight = target.height() / scale
        val left = (bitmap.width - visibleWidth) / 2f
        val top = (bitmap.height - visibleHeight) / 2f
        canvas.drawBitmap(
            bitmap,
            Rect(left.toInt(), top.toInt(), (left + visibleWidth).toInt(), (top + visibleHeight).toInt()),
            target,
            paint,
        )
    }

    private fun drawEdgeGradient(canvas: Canvas, bitmap: Bitmap, target: RectF, paint: Paint) {
        val colors = intArrayOf(
            averageEdge(bitmap, top = true),
            averageEdge(bitmap, left = true),
            averageEdge(bitmap, top = false),
            averageEdge(bitmap, left = false),
        )
        paint.shader = LinearGradient(
            0f,
            0f,
            target.width(),
            target.height(),
            colors,
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(target, paint)
        paint.shader = null
    }

    private fun averageEdge(bitmap: Bitmap, top: Boolean? = null, left: Boolean? = null): Int {
        val horizontal = top != null
        val length = if (horizontal) bitmap.width else bitmap.height
        val thickness = (min(bitmap.width, bitmap.height) * 0.04f).toInt().coerceAtLeast(1)
        val step = (length / 256).coerceAtLeast(1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0
        var i = 0
        while (i < length) {
            for (band in 0 until thickness) {
                val x = when {
                    horizontal -> i
                    left == true -> band
                    else -> bitmap.width - 1 - band
                }
                val y = when {
                    horizontal && top == true -> band
                    horizontal -> bitmap.height - 1 - band
                    left == true -> i
                    else -> i
                }
                val color = bitmap.getPixel(x, y)
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
                count++
            }
            i += step
        }
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private fun drawBlurBackground(canvas: Canvas, bitmap: Bitmap, target: RectF, paint: Paint) {
        val thumbWidth = 96
        val thumbHeight = 160
        val thumbnail = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
        drawCenterCrop(Canvas(thumbnail), bitmap, RectF(0f, 0f, thumbWidth.toFloat(), thumbHeight.toFloat()), paint)
        val pixels = IntArray(thumbWidth * thumbHeight)
        thumbnail.getPixels(pixels, 0, thumbWidth, 0, 0, thumbWidth, thumbHeight)
        boxBlur(pixels, thumbWidth, thumbHeight, radius = 9)
        thumbnail.setPixels(pixels, 0, thumbWidth, 0, 0, thumbWidth, thumbHeight)
        canvas.drawBitmap(thumbnail, null, target, paint)
        canvas.drawColor(0x33000000)
        thumbnail.recycle()
    }

    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val source = pixels.copyOf()
        for (y in 0 until height) for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            var count = 0
            for (offset in -radius..radius) {
                val sx = (x + offset).coerceIn(0, width - 1)
                val color = source[y * width + sx]
                red += Color.red(color); green += Color.green(color); blue += Color.blue(color); count++
            }
            pixels[y * width + x] = Color.rgb(red / count, green / count, blue / count)
        }
        source.indices.forEach { source[it] = pixels[it] }
        val horizontal = pixels.copyOf()
        for (y in 0 until height) for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            var count = 0
            for (offset in -radius..radius) {
                val sy = (y + offset).coerceIn(0, height - 1)
                val color = horizontal[sy * width + x]
                red += Color.red(color); green += Color.green(color); blue += Color.blue(color); count++
            }
            pixels[y * width + x] = Color.rgb(red / count, green / count, blue / count)
        }
    }

    private fun RectI.clamped(width: Int, height: Int): RectI {
        val left = left.coerceIn(0, width - 1)
        val top = top.coerceIn(0, height - 1)
        val right = right.coerceIn(left + 1, width)
        val bottom = bottom.coerceIn(top + 1, height)
        return RectI(left, top, right, bottom)
    }
}
