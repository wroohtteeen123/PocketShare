package io.pocketshare

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Pure raster generation, also exercised by the desktop QR decode regression test. */
object QrPixels {
    const val SIZE = 720
    fun encode(value: String, ink: Int): IntArray {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, SIZE, SIZE,
            mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        return IntArray(SIZE * SIZE) { index -> if (matrix[index % SIZE, index / SIZE]) ink else 0 }
    }
}
