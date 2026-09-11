//
//  RemoteImage.swift
//  DoMemory
//
//  A remote image with a caller-supplied fallback, backed by
//  RemoteImageService's memory and disk caches.
//

import SwiftUI

/// Draws the image at `url`, or `fallback` until it arrives — and permanently,
/// if it never does.
///
/// The fallback is what the surface looked like before it had artwork, so a
/// season with no art, a bad URL or no network is not a degraded state to
/// handle: it is simply the old design.
struct RemoteImage<Fallback: View>: View {
    let url: URL?
    @ViewBuilder let fallback: () -> Fallback

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
            } else {
                fallback()
            }
        }
        // Keyed on the URL so a season handover at local midnight swaps the
        // artwork instead of leaving the outgoing season's picture in place.
        .task(id: url) { await load() }
    }

    private func load() async {
        guard let url else {
            image = nil
            return
        }

        // Already decoded: paint it in this frame. Fading in artwork the player
        // has been looking at all session — every time the menu's TabView
        // rebuilds the season card — reads as a glitch, not as polish.
        if let cached = RemoteImageService.shared.cachedImage(for: url) {
            image = cached
            return
        }

        let loaded = await RemoteImageService.shared.image(for: url)
        guard !Task.isCancelled else { return }
        withAnimation(.easeOut(duration: 0.25)) {
            image = loaded
        }
    }
}
