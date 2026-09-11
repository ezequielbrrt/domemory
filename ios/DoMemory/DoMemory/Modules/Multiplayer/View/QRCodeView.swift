//
//  QRCodeView.swift
//  DoMemory
//

import CoreImage.CIFilterBuiltins
import SwiftUI

struct QRCodeView: View {
    let code: String

    private let context = CIContext()
    private let filter = CIFilter.qrCodeGenerator()

    var body: some View {
        Image(uiImage: makeQRCode(from: code))
            .interpolation(.none)
            .resizable()
            .scaledToFit()
            .padding(12)
            .background(Color.white)
            .clipShape(.rect(cornerRadius: 8))
            .accessibilityLabel(Strings.multiplayerQRCode)
    }

    private func makeQRCode(from string: String) -> UIImage {
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"

        guard let outputImage = filter.outputImage else {
            return UIImage(systemName: "qrcode") ?? UIImage()
        }

        let coloredImage = outputImage
            .transformed(by: CGAffineTransform(scaleX: 12, y: 12))
            .applyingFilter("CIFalseColor", parameters: [
                "inputColor0": CIColor.black,
                "inputColor1": CIColor.white
            ])

        guard let cgImage = context.createCGImage(coloredImage, from: coloredImage.extent) else {
            return UIImage(systemName: "qrcode") ?? UIImage()
        }
        return UIImage(cgImage: cgImage)
    }
}
