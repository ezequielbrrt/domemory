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
    }

    private(set) var removeAdsProduct: Product?
    private(set) var isLoading = false
    private(set) var purchaseMessage: String?
    var purchaseAlert: PurchaseAlert?
    private(set) var hasRemovedAds: Bool {
        didSet {
            UserDefaults.standard.set(hasRemovedAds, forKey: StorageKey.hasRemovedAds)
        }
    }

    private var transactionUpdatesTask: Task<Void, Never>?

    private init() {
        hasRemovedAds = UserDefaults.standard.bool(forKey: StorageKey.hasRemovedAds)
        transactionUpdatesTask = listenForTransactionUpdates()

        Task {
            await refreshPurchasedProducts()
            await loadProducts()
        }
    }

    var removeAdsPriceText: String {
        removeAdsProduct?.displayPrice ?? "$0.99"
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
        guard !hasRemovedAds else { return }

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

        hasRemovedAds = hasValidRemoveAdsEntitlement
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
            hasRemovedAds = transaction.revocationDate == nil
            purchaseMessage = hasRemovedAds ? Strings.settingsRemoveAdsPurchased : nil
            if hasRemovedAds {
                purchaseAlert = PurchaseAlert(
                    title: Strings.settingsPurchaseSuccessTitle,
                    message: Strings.settingsPurchaseSuccessMessage
                )
            }
        }

        await transaction.finish()
    }
}
