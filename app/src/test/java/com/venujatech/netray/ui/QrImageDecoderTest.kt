package com.venujatech.netray.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrImageDecoderTest {
    @Test
    fun decodesNormalAndRotatedCodes() {
        val qr = qrPixels("vless://synthetic@example.test:443?security=tls")
        assertEquals(listOf("vless://synthetic@example.test:443?security=tls"), QrImageDecoder.decodePixels(qr.pixels, qr.width, qr.height))

        val rotated = rotate90(qr.pixels, qr.width, qr.height)
        assertEquals(listOf("vless://synthetic@example.test:443?security=tls"), QrImageDecoder.decodePixels(rotated, qr.height, qr.width))
    }

    @Test
    fun decodesInvertedAndLowContrastCodes() {
        val qr = qrPixels("trojan://test@example.test:443")
        val inverted = IntArray(qr.pixels.size) { index -> if (qr.pixels[index] == BLACK) WHITE else BLACK }
        assertEquals(listOf("trojan://test@example.test:443"), QrImageDecoder.decodePixels(inverted, qr.width, qr.height))

        val lowContrast = IntArray(qr.pixels.size) { index ->
            if (qr.pixels[index] == BLACK) 0xFF555555.toInt() else 0xFFCCCCCC.toInt()
        }
        assertEquals(listOf("trojan://test@example.test:443"), QrImageDecoder.decodePixels(lowContrast, qr.width, qr.height))
    }

    @Test
    fun returnsMultipleDistinctCodesAndEmptyForNoCode() {
        val first = qrPixels("vless://one@example.test:443")
        val second = qrPixels("vmess://synthetic")
        val canvasWidth = first.width * 2 + 24
        val canvas = IntArray(canvasWidth * first.height) { WHITE }
        copyInto(first, canvas, canvasWidth, 0, 0)
        copyInto(second, canvas, canvasWidth, first.width + 24, 0)
        assertEquals(setOf("vless://one@example.test:443", "vmess://synthetic"), QrImageDecoder.decodePixels(canvas, canvasWidth, first.height).toSet())

        assertTrue(QrImageDecoder.decodePixels(IntArray(256 * 256) { WHITE }, 256, 256).isEmpty())
    }

    @Test
    fun sampleSizeBoundsLargeImageAndHandlesInvalidDimensions() {
        assertTrue(8192 / QrImageDecoder.calculateSampleSize(8192, 4096) <= QrImageDecoder.MAX_LONG_SIDE)
        assertEquals(1, QrImageDecoder.calculateSampleSize(0, 0))
    }

    private data class Pixels(val width: Int, val height: Int, val pixels: IntArray)

    private fun qrPixels(text: String): Pixels {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 256, 256)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix.get(x, y)) BLACK else WHITE
        }
        return Pixels(matrix.width, matrix.height, pixels)
    }

    private fun rotate90(source: IntArray, width: Int, height: Int): IntArray {
        val out = IntArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            val newX = height - 1 - y
            val newY = x
            out[newY * height + newX] = source[y * width + x]
        }
        return out
    }

    private fun copyInto(source: Pixels, target: IntArray, targetWidth: Int, left: Int, top: Int) {
        for (y in 0 until source.height) {
            source.pixels.copyInto(target, (top + y) * targetWidth + left, y * source.width, (y + 1) * source.width)
        }
    }

    companion object {
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
