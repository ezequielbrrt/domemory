package com.ezequielbrrt.domemory.feature.multiplayer

import com.ezequielbrrt.domemory.services.multiplayer.MultiplayerInvite
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiplayerQrCodeEncoderTest {
    @Test
    fun `QR code round trips the shared invite URL`() {
        val inviteUrl = MultiplayerInvite.schemeUrl("ABC123")
        val matrix = MultiplayerQrCodeEncoder.matrix(inviteUrl, 256)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) BLACK else WHITE
        }

        val decoded = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels))),
        )

        assertEquals(inviteUrl, decoded.text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank QR content is rejected`() {
        MultiplayerQrCodeEncoder.matrix("", 256)
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
