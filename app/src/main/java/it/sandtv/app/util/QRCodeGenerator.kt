package it.sandtv.app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Utility class for generating QR codes
 */
object QRCodeGenerator {
    
    /**
     * Generates a QR code bitmap from the given content
     * 
     * @param content The content to encode in the QR code (URL, text, etc.)
     * @param size The size in pixels for the QR code (width = height)
     * @param foregroundColor The color for the QR code pattern (default: black)
     * @param backgroundColor The color for the background (default: transparent)
     * @return A Bitmap containing the QR code
     */
    fun generate(
        content: String,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.TRANSPARENT
    ): Bitmap {
        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) foregroundColor else backgroundColor)
            }
        }
        
        return bitmap
    }
}
