package com.ezequielbrrt.domemory.feature.multiplayer

import android.content.Context
import com.ezequielbrrt.domemory.core.deeplink.DeepLink
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Extracts only room invites accepted by the shared deep-link parser. */
object MultiplayerQrScan {
    fun roomCode(rawValue: String?): String? = (DeepLink.parse(rawValue) as? DeepLink.Join)?.code
}

/**
 * Opens Google Play services' QR scanner. It supplies its own camera UI, so the app neither
 * requests CAMERA permission nor retains camera frames.
 */
fun launchMultiplayerQrScanner(
    context: Context,
    onScanned: (String?) -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { onScanned(it.rawValue) }
        .addOnFailureListener(onFailure)
}
