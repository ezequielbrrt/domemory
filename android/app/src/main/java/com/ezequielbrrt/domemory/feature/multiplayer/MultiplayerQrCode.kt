package com.ezequielbrrt.domemory.feature.multiplayer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ezequielbrrt.domemory.R
import com.ezequielbrrt.domemory.services.multiplayer.MultiplayerInvite
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/** A scannable representation of the exact scheme URL sent in a room invite. */
@Composable
fun MultiplayerQrCode(code: String, modifier: Modifier = Modifier) {
    val bitmap = remember(code) {
        MultiplayerQrCodeEncoder.bitmap(MultiplayerInvite.schemeUrl(code), QR_IMAGE_SIZE_PX)
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.multiplayer_qr_code),
        modifier = modifier
            .background(Color.White)
            .padding(12.dp)
            .size(QR_IMAGE_SIZE_DP),
    )
}

object MultiplayerQrCodeEncoder {
    fun matrix(content: String, size: Int): BitMatrix {
        require(content.isNotBlank()) { "QR content must not be blank." }
        require(size > 0) { "QR size must be positive." }
        return QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to QR_MARGIN_MODULES),
        )
    }

    fun bitmap(content: String, size: Int): Bitmap {
        val matrix = matrix(content, size)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) BLACK else WHITE
        }
        return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }
    }

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}

private const val QR_IMAGE_SIZE_PX = 512
private val QR_IMAGE_SIZE_DP = 190.dp
private const val QR_MARGIN_MODULES = 1
