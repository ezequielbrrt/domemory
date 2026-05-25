//
//  PurchaseService.swift
//  DoMemory
//
//  Created by OpenAI on 06/04/26.
//

import Foundation
import Observation
import StoreKit

struct PurchaseAlert: Identifiable {
    let id = UUID()
    let title: String
    let message: String
}

@MainActor
@Observable
final class PurchaseService {
    static let shared = PurchaseService()

    static let removeAdsProductID = "com.ezequielbrrt.domemory.removeads"

    private enum StorageKey {
        static let hasRemovedAds = "purchases.has_removed_ads"
        static let rewardedRemoveAdsExpirationDate = "purchases.rewarded_remove_ads_expiration_date"
    }

    private(set) var removeAdsProduct: Product?
    private(set) var isLoading = false
    private(set) var purchaseMessage: String?
    var purchaseAlert: PurchaseAlert?
    private(set) var hasPurchasedRemoveAds: Bool {
        didSet {
            UserDefaults.standard.set(hasPurchasedRemoveAds, forKey: StorageKey.hasRemovedAds)
        }
    }
    private(set) var rewardedRemoveAdsExpirationDate: Date? {
        didSet {
            if let rewardedRemoveAdsExpirationDate {
                UserDefaults.standard.set(rewardedRemoveAdsExpirationDate.timeIntervalSince1970, forKey: StorageKey.rewardedRemoveAdsExpirationDate)
            } else {
                UserDefaults.standard.removeObject(forKey: StorageKey.rewardedRemoveAdsExpirationDate)
            }
        }
    }

    private var transactionUpdatesTask: Task<Void, Never>?

    private init() {
        hasPurchasedRemoveAds = UserDefaults.standard.bool(forKey: StorageKey.hasRemovedAds)
        let temporaryExpirationTimestamp = UserDefaults.standard.double(forKey: StorageKey.rewardedRemoveAdsExpirationDate)
        rewardedRemoveAdsExpirationDate = temporaryExpirationTimestamp > 0 ? Date(timeIntervalSince1970: temporaryExpirationTimestamp) : nil
        transactionUpdatesTask = listenForTransactionUpdates()
        clearExpiredRewardedRemoveAdsIfNeeded()

        Task {
            await refreshPurchasedProducts()
            await loadProducts()
        }
    }

    var removeAdsPriceText: String {
        removeAdsProduct?.displayPrice ?? "$0.99"
    }

    var hasRemovedAds: Bool {
        hasPurchasedRemoveAds || hasActiveRewardedRemoveAds
    }

    var hasActiveRewardedRemoveAds: Bool {
        guard let rewardedRemoveAdsExpirationDate else { return false }
        return rewardedRemoveAdsExpirationDate > Date()
    }

    var rewardedRemoveAdsExpirationText: String {
        guard let rewardedRemoveAdsExpirationDate, hasActiveRewardedRemoveAds else { return "" }
        return rewardedRemoveAdsExpirationDate.formatted(date: .omitted, time: .shortened)
    }

    func loadProducts() async {
        isLoading = true
        defer { isLoading = false }

        do {
            removeAdsProduct = try await Product.products(for: [Self.removeAdsProductID]).first
            if removeAdsProduct == nil {
                purchaseMessage = Strings.settingsRemoveAdsUnavailable
            }
        } catch {
            purchaseMessage = error.localizedDescription
        }
    }

    func purchaseRemoveAds() async {
        guard !hasPurchasedRemoveAds else { return }

        isLoading = true
        purchaseMessage = nil
        defer { isLoading = false }

        do {
            if removeAdsProduct == nil {
                await loadProducts()
            }

            guard let removeAdsProduct else {
                purchaseMessage = Strings.settingsRemoveAdsUnavailable
                return
            }

            let result = try await removeAdsProduct.purchase()

            switch result {
            case .success(let verificationResult):
                await handle(verificationResult)
            case .userCancelled:
                break
            case .pending:
                purchaseMessage = Strings.settingsRemoveAdsPending
            @unknown default:
                purchaseMessage = Strings.settingsRemoveAdsUnavailable
            }
        } catch {
            purchaseMessage = error.localizedDescription
        }
    }

    func restorePurchases() async {
        isLoading = true
        purchaseMessage = nil
        defer { isLoading = false }

        do {
            try await AppStore.sync()
            await refreshPurchasedProducts()
            if hasRemovedAds {
                purchaseAlert = PurchaseAlert(
                    title: Strings.settingsRestoreSuccessTitle,
                    message: Strings.settingsRestoreSuccessMessage
                )
            } else {
                purchaseMessage = Strings.settingsRemoveAdsNoRestore
                purchaseAlert = PurchaseAlert(
                    title: Strings.settingsRestorePurchasesTitle,
                    message: Strings.settingsRemoveAdsNoRestore
                )
            }
        } catch {
            purchaseMessage = error.localizedDescription
        }
    }

    func refreshPurchasedProducts() async {
        var hasValidRemoveAdsEntitlement = false

        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else { continue }
            guard transaction.productID == Self.removeAdsProductID else { continue }

            hasValidRemoveAdsEntitlement = transaction.revocationDate == nil
            if hasValidRemoveAdsEntitlement {
                break
            }
        }

        hasPurchasedRemoveAds = hasValidRemoveAdsEntitlement
        clearExpiredRewardedRemoveAdsIfNeeded()
    }

    func grantRewardedRemoveAds(duration: TimeInterval = 24 * 60 * 60) {
        let currentExpiration = hasActiveRewardedRemoveAds ? rewardedRemoveAdsExpirationDate ?? Date() : Date()
        rewardedRemoveAdsExpirationDate = currentExpiration.addingTimeInterval(duration)
        purchaseAlert = PurchaseAlert(
            title: Strings.settingsRewardedRemoveAdsSuccessTitle,
            message: Strings.settingsRewardedRemoveAdsSuccessMessage
        )
    }

    private func listenForTransactionUpdates() -> Task<Void, Never> {
        Task.detached { [weak self] in
            for await result in Transaction.updates {
                guard let self else { return }
                await self.handle(result)
            }
        }
    }

    private func handle(_ verificationResult: VerificationResult<Transaction>) async {
        guard case .verified(let transaction) = verificationResult else {
            purchaseMessage = Strings.settingsRemoveAdsUnavailable
            return
        }

        if transaction.productID == Self.removeAdsProductID {
            hasPurchasedRemoveAds = transaction.revocationDate == nil
            purchaseMessage = hasRemovedAds ? Strings.settingsRemoveAdsPurchased : nil
            if hasPurchasedRemoveAds {
                purchaseAlert = PurchaseAlert(
                    title: Strings.settingsPurchaseSuccessTitle,
                    message: Strings.settingsPurchaseSuccessMessage
                )
            }
        }

        await transaction.finish()
    }

    private func clearExpiredRewardedRemoveAdsIfNeeded() {
        guard let rewardedRemoveAdsExpirationDate else { return }
        if rewardedRemoveAdsExpirationDate <= Date() {
            self.rewardedRemoveAdsExpirationDate = nil
        }
    }
}
