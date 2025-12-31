package com.ethyllium.authservice.infrastructure.outbound.crypto

import com.ethyllium.authservice.domain.port.driven.QrCodeGenerator
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

@Component
class QrCodeGeneratorAdapter : QrCodeGenerator {
    override fun generateQrCode(uri: String, width: Int, height: Int): ByteArray {
        val matrix = MultiFormatWriter().encode(uri, BarcodeFormat.QR_CODE, width, height)
        val outputStream = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream)
        return outputStream.toByteArray()
    }
}