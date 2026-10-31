package com.kayb28.losslesswallpaper.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import java.nio.ByteBuffer
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
                drawBlurBackground(canvas, content, full, paint, radius = 7, shade = 0x12000000)
                drawContainedFeathered(canvas, content, full, paint)
            }
            WallpaperMode.BLACK -> {
                canvas.drawColor(Color.BLACK)
                drawContained(canvas, content, full, paint)
            }
            WallpaperMode.BLUR -> {
                drawBlurBackground(canvas, content, full, paint, radius = 9, shade = 0x44000000)
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
        canvas.drawBitmap(bitmap, null, containedRect(bitmap, target), paint)
    }

    private fun containedRect(bitmap: Bitmap, target: RectF): RectF {
        val scale = min(1f, min(target.width() / bitmap.width, target.height() / bitmap.height))
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = target.centerX() - width / 2f
        val top = target.centerY() - height / 2f
        return RectF(left, top, left + width, top + height)
    }

    /** Feather the foreground's alpha into the matching blurred image behind it. */
    private fun drawContainedFeathered(canvas: Canvas, bitmap: Bitmap, target: RectF, paint: Paint) {
        val destination = containedRect(bitmap, target)
        val mask = makeFeatherMask(bitmap.width.toFloat() / bitmap.height)
        val checkpoint = canvas.saveLayer(destination, null)
        try {
            canvas.drawBitmap(bitmap, null, destination, paint)
            val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawBitmap(mask, null, destination, maskPaint)
        } finally {
            canvas.restoreToCount(checkpoint)
            mask.recycle()
        }
    }

    private fun makeFeatherMask(aspect: Float): Bitmap {
        val longest = 512
        val width = if (aspect >= 1f) longest else ((longest * aspect).toInt().coerceIn(96, longest) / 4) * 4
        val height = if (aspect >= 1f) ((longest / aspect).toInt().coerceIn(96, longest) / 4) * 4 else longest
        val pixels = ByteArray(width * height)
        val featherX = width * 0.055f
        val featherY = height * 0.055f
        for (y in 0 until height) for (x in 0 until width) {
            val edge = min(
                min(x + 0.5f, width - x - 0.5f) / featherX,
                min(y + 0.5f, height - y - 0.5f) / featherY,
            ).coerceIn(0f, 1f)
            val smooth = edge * edge * (3f - 2f * edge)
            pixels[y * width + x] = (smooth * 255).toInt().toByte()
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        }
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

    private fun drawBlurBackground(canvas: Canvas, bitmap: Bitmap, target: RectF, paint: Paint, radius: Int, shade: Int) {
        val aspect = target.width() / target.height()
        val thumbWidth = if (aspect >= 1f) 256 else (256 * aspect).toInt().coerceAtLeast(64)
        val thumbHeight = if (aspect >= 1f) (256 / aspect).toInt().coerceAtLeast(64) else 256
        val thumbnail = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
        drawCenterCrop(Canvas(thumbnail), bitmap, RectF(0f, 0f, thumbWidth.toFloat(), thumbHeight.toFloat()), paint)
        val pixels = IntArray(thumbWidth * thumbHeight)
        thumbnail.getPixels(pixels, 0, thumbWidth, 0, 0, thumbWidth, thumbHeight)
        boxBlur(pixels, thumbWidth, thumbHeight, radius)
        thumbnail.setPixels(pixels, 0, thumbWidth, 0, 0, thumbWidth, thumbHeight)
        canvas.drawBitmap(thumbnail, null, target, paint)
        if (Color.alpha(shade) > 0) canvas.drawColor(shade)
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
