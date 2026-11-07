package com.venujatech.netray.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import java.io.IOException
import kotlin.math.max

internal object QrImageDecoder {
    const val MAX_LONG_SIDE = 2048

    @Throws(IOException::class)
    fun decode(context: Context, uri: Uri): List<String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Unable to open the selected image")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Selected file is not a readable image")

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: throw IOException("Unable to decode the selected image")

        val rotation = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val oriented = rotate(bitmap, rotation)
        val decodedWidth = oriented.width
        val decodedHeight = oriented.height
        val pixels = IntArray(oriented.width * oriented.height)
        oriented.getPixels(pixels, 0, oriented.width, 0, 0, oriented.width, oriented.height)
        if (oriented !== bitmap) oriented.recycle()
        bitmap.recycle()
        return decodePixels(pixels, decodedWidth, decodedHeight)
    }

    fun calculateSampleSize(width: Int, height: Int, maxLongSide: Int = MAX_LONG_SIDE): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        val longest = max(width, height)
        while (longest / sample > maxLongSide) sample *= 2
        return sample
    }

    internal fun decodeSource(source: LuminanceSource): List<String> {
        val found = linkedMapOf<String, Result>()
        fun scan(candidate: LuminanceSource, tryHarder: Boolean) {
            val reader = MultiFormatReader()
            val hints = mapOf<DecodeHintType, Any>(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to tryHarder
            )
            reader.setHints(hints)
            val multi = GenericMultipleBarcodeReader(reader)
            try {
                multi.decodeMultiple(BinaryBitmap(HybridBinarizer(candidate)), hints)
                    .filter { !it.text.isNullOrBlank() }
                    .forEach { found.putIfAbsent(it.text, it) }
            } catch (_: NotFoundException) {
                try {
                    reader.reset()
                    reader.setHints(hints)
                    val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(candidate)))
                    if (result.text.isNotBlank()) found.putIfAbsent(result.text, result)
                } catch (_: Exception) { }
            } catch (_: Exception) { }
        }

        scan(source, tryHarder = false)
        if (found.isEmpty()) {
            scan(source, tryHarder = true)
            scan(source.invert(), tryHarder = true)
            if (source.isRotateSupported) {
                var rotated = source
                repeat(3) {
                    rotated = rotated.rotateCounterClockwise()
                    scan(rotated, tryHarder = true)
                    scan(rotated.invert(), tryHarder = true)
                }
            }
        }
        return found.keys.toList()
    }

    internal fun decodePixels(pixels: IntArray, width: Int, height: Int): List<String> {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return emptyList()
        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        return decodeSource(source)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
