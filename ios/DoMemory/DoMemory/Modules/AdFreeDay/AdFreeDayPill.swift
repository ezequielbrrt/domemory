//
//  AdFreeDayPill.swift
//  DoMemory
//
//  The floating "No Ads" entry point and the modifier that hangs it off the
//  bottom-trailing corner of a screen and presents the offer sheet. Applied to
//  every surface that shows involuntary ads and is not itself a game: the
//  three menu tabs, the season map and the multiplayer lobby.
//

import SwiftUI

extension View {
    /// Overlays the ad-free day pill and hosts its sheet. `source` names the
    /// screen for analytics. Hidden for Remove Ads purchasers, and when no
    /// rewarded unit is configured unless a grant is already running.
    func adFreeDayEntryPoint(source: String) -> some View {
        modifier(AdFreeDayEntryPoint(source: source))
    }
}

private struct AdFreeDayEntryPoint: ViewModifier {
    let source: String

    @State private var showOffer = false
    @State private var purchases = PurchaseService.shared

    private var isVisible: Bool {
        guard !purchases.hasPurchasedRemoveAds else { return false }
        return purchases.hasActiveRewardedRemoveAds
            || AdsService.shared.isRewardedConfigured(for: .adFreeDayRewarded)
    }

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottomTrailing) {
                if isVisible {
                    // Re-read on every render so a chain advanced inside the
                    // sheet shows on the pill as soon as the sheet closes.
                    AdFreeDayPill(adsWatched: AdFreeDayService.shared.adsWatched()) {
                        AdFreeDayOfferViewModel.logEntryTapped(source: source)
                        showOffer = true
                    }
                    .padding(.trailing, 16)
                    .padding(.bottom, 12)
                }
            }
            .sheet(isPresented: $showOffer) {
                AdFreeDayOfferView(source: source)
            }
            // An app-open ad rides `didBecomeActive`; backgrounding with the
            // sheet up and coming back must not land one on top of it.
            .onChange(of: showOffer) { _, isShowing in
                AdsService.shared.setFullScreenAdsSuppressed(isShowing)
            }
    }
}

struct AdFreeDayPill: View {
    let adsWatched: Int
    let onTap: () -> Void

    @State private var purchases = PurchaseService.shared

    var body: some View {
        // The countdown only needs minute resolution.
        TimelineView(.periodic(from: .now, by: 60)) { context in
            let isActive = isGrantActive(at: context.date)
            Button {
                HapticsService.shared.fire(.tap)
                onTap()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: isActive ? "checkmark.circle.fill" : "nosign")
                        .font(.system(size: 13, weight: .bold))
                    Text(title(at: context.date, isActive: isActive))
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .lineLimit(1)
                }
                .foregroundStyle(isActive ? .white : Color.primaryColor)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(
                    Capsule()
                        .fill(isActive ? Color.primaryColor : Color.surfacePrimary)
                        .overlay(Capsule().stroke(Color.surfaceBorder, lineWidth: 1))
                        .shadow(color: Color.shadowColor, radius: 8, x: 0, y: 3)
                )
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Strings.adFreeDayTitle)
            .accessibilityValue(title(at: context.date, isActive: isActive))
        }
    }

    private func isGrantActive(at date: Date) -> Bool {
        guard let expiry = purchases.rewardedRemoveAdsExpirationDate else { return false }
        return expiry > date
    }

    private func title(at date: Date, isActive: Bool) -> String {
        if isActive, let expiry = purchases.rewardedRemoveAdsExpirationDate {
            return Strings.adFreeDayPillActive(Self.remainingFormatter.string(from: date, to: expiry) ?? "")
        }
        if adsWatched > 0 {
            return Strings.adFreeDayPillProgress(adsWatched, of: AdFreeDayService.requiredAds)
        }
        return Strings.adFreeDayPillTitle
    }

    /// "18h" / "45m" (or "2d" for a stacked grant) — one unit is enough on a pill.
    private static let remainingFormatter: DateComponentsFormatter = {
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = [.day, .hour, .minute]
        formatter.unitsStyle = .abbreviated
        formatter.maximumUnitCount = 1
        formatter.zeroFormattingBehavior = .dropAll
        return formatter
    }()
}

#Preview("Pill states") {
    VStack(spacing: 16) {
        AdFreeDayPill(adsWatched: 0) {}
        AdFreeDayPill(adsWatched: 1) {}
    }
    .padding()
    .background(Color.appBackground)
}
