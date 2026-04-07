//
//  SettingsView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI

struct SettingsView: View {
    @State private var viewModel: SettingsViewModel
    @State private var purchaseService = PurchaseService.shared
    @State private var showDifficultyPicker = false

    let impactMed = UIImpactFeedbackGenerator(style: .medium)

    init(listener: SettingsListener?) {
        self.viewModel = SettingsViewModel(listener: listener)
    }

    var body: some View {
        ZStack {
            Color.grayBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                VStack(spacing: 4) {
                    Text(Strings.settingsTitle)
                        .font(.righteous(size: 38))
                        .foregroundStyle(Color.primaryColor)

                    Text(Strings.settingsDescription)
                        .font(.patrickHand(size: 17))
                        .foregroundStyle(Color.textMuted)
                }
                .padding(.top, 32)
                .padding(.bottom, 28)

                VStack(spacing: 12) {
                    SettingsOptionRow(
                        title: Strings.difficulty,
                        subtitle: selectedDifficultyTitle,
                        actionTitle: Strings.settingsSelectDifficulty,
                        systemImage: "slider.horizontal.3"
                    ) {
                        impactMed.impactOccurred()
                        showDifficultyPicker = true
                    }

                    SettingsOptionRow(
                        title: Strings.settingsRemoveAdsTitle,
                        subtitle: removeAdsSubtitle,
                        actionTitle: removeAdsActionTitle,
                        systemImage: "nosign",
                        isDisabled: purchaseService.hasRemovedAds || purchaseService.isLoading
                    ) {
                        impactMed.impactOccurred()
                        Task {
                            await purchaseService.purchaseRemoveAds()
                        }
                    }

                    SettingsOptionRow(
                        title: Strings.settingsRestorePurchasesTitle,
                        subtitle: Strings.settingsRestorePurchasesDescription,
                        actionTitle: Strings.settingsRestorePurchasesAction,
                        systemImage: "arrow.clockwise",
                        isDisabled: purchaseService.isLoading
                    ) {
                        impactMed.impactOccurred()
                        Task {
                            await purchaseService.restorePurchases()
                        }
                    }

                    if let purchaseMessage = purchaseService.purchaseMessage {
                        Text(purchaseMessage)
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundStyle(Color.textMuted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.horizontal, 20)

                Spacer()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            viewModel.difficulty = viewModel.getCurrentDifficulty()
            Task {
                await purchaseService.refreshPurchasedProducts()
                await purchaseService.loadProducts()
            }
        }
        .onDisappear {
            viewModel.viewWillDissapear()
        }
        .sheet(isPresented: $showDifficultyPicker) {
            DifficultyPickerSheet(
                selectedDifficulty: viewModel.difficulty,
                onSelect: { difficultyIndex in
                    impactMed.impactOccurred()
                    viewModel.saveDifficulty(difficultyIndex: difficultyIndex)
                    showDifficultyPicker = false
                }
            )
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
    }

    private var selectedDifficultyTitle: String {
        switch viewModel.difficulty {
        case 0: return Strings.easy
        case 1: return Strings.medium
        case 2: return Strings.hard
        case 3: return Strings.veryHard
        default: return Strings.medium
        }
    }

    private var removeAdsSubtitle: String {
        if purchaseService.hasRemovedAds {
            return Strings.settingsRemoveAdsPurchasedDescription
        }

        return Strings.settingsRemoveAdsDescription
    }

    private var removeAdsActionTitle: String {
        if purchaseService.hasRemovedAds {
            return Strings.settingsRemoveAdsPurchased
        }

        if purchaseService.isLoading {
            return Strings.settingsRemoveAdsLoading
        }

        return String(format: Strings.settingsRemoveAdsActionFormat, purchaseService.removeAdsPriceText)
    }
}

private struct SettingsOptionRow: View {
    let title: String
    let subtitle: String
    let actionTitle: String
    let systemImage: String
    var isDisabled = false
    let action: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(Color.primaryColor.opacity(0.12))
                    .frame(width: 44, height: 44)

                Image(systemName: systemImage)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.primaryColor)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.textPrimary)

                Text(subtitle)
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundStyle(Color.textMuted)
            }

            Spacer()

            Button(action: action) {
                Text(actionTitle)
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 9)
                    .background(Capsule().fill(isDisabled ? Color.textMuted.opacity(0.45) : Color.primaryColor))
            }
            .buttonStyle(.plain)
            .disabled(isDisabled)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.darkGrayColor)
                .shadow(color: Color.textPrimary.opacity(0.08), radius: 10, x: 0, y: 4)
        )
    }
}

private struct DifficultyPickerSheet: View {
    let selectedDifficulty: Int
    let onSelect: (Int) -> Void

    var body: some View {
        ZStack {
            Color.grayBackground.ignoresSafeArea()

            VStack(spacing: 18) {
                VStack(spacing: 4) {
                    Text(Strings.difficulty)
                        .font(.righteous(size: 32))
                        .foregroundStyle(Color.primaryColor)

                    Text(Strings.askDifficulty)
                        .font(.patrickHand(size: 17))
                        .foregroundStyle(Color.textMuted)
                }

                VStack(spacing: 0) {
                    SelectableRow(
                        title: Strings.easy,
                        subtitle: Strings.easySubtitle,
                        accentColor: Color.easyGreen,
                        bgColor: Color.make(230, 249, 241),
                        systemImage: "leaf.fill",
                        isSelected: selectedDifficulty == 0,
                        isLast: false
                    ) {
                        onSelect(0)
                    }

                    SelectableRow(
                        title: Strings.medium,
                        subtitle: Strings.mediumSubtitle,
                        accentColor: Color.primaryColor,
                        bgColor: Color.make(235, 233, 252),
                        systemImage: "circle.grid.2x2.fill",
                        isSelected: selectedDifficulty == 1,
                        isLast: false
                    ) {
                        onSelect(1)
                    }

                    SelectableRow(
                        title: Strings.hard,
                        subtitle: Strings.hardSubtitle,
                        accentColor: Color.hardAmber,
                        bgColor: Color.make(254, 245, 228),
                        systemImage: "flame.fill",
                        isSelected: selectedDifficulty == 2,
                        isLast: false
                    ) {
                        onSelect(2)
                    }

                    SelectableRow(
                        title: Strings.veryHard,
                        subtitle: Strings.veryHardSubtitle,
                        accentColor: Color.secundaryColor,
                        bgColor: Color.make(254, 240, 236),
                        systemImage: "bolt.trianglebadge.exclamationmark.fill",
                        isSelected: selectedDifficulty == 3,
                        isLast: true
                    ) {
                        onSelect(3)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .shadow(color: Color.black.opacity(0.07), radius: 10, x: 0, y: 4)
            }
            .padding(.horizontal, 20)
        }
    }
}

private struct SelectableRow: View {
    let title: String
    let subtitle: String
    let accentColor: Color
    let bgColor: Color
    let systemImage: String
    let isSelected: Bool
    let isLast: Bool
    let action: () -> Void

    @State private var isPressed = false

    var body: some View {
        Button(action: trigger) {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(accentColor.opacity(0.15))
                        .frame(width: 36, height: 36)
                    Image(systemName: systemImage)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(accentColor)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(Color.textPrimary)
                    Text(subtitle)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(Color.textMuted)
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(accentColor)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity)
            .background(bgColor)
            .overlay(alignment: .bottom) {
                if !isLast {
                    Divider().padding(.leading, 66)
                }
            }
            .overlay(
                RoundedRectangle(cornerRadius: 0)
                    .strokeBorder(isSelected ? accentColor.opacity(0.3) : .clear, lineWidth: isSelected ? 1 : 0)
            )
            .scaleEffect(isPressed ? 0.98 : 1.0)
            .animation(.spring(response: 0.28, dampingFraction: 0.8), value: isPressed)
            .animation(.spring(response: 0.3, dampingFraction: 0.8), value: isSelected)
        }
        .buttonStyle(.plain)
        .onLongPressGesture(minimumDuration: .infinity, pressing: { pressing in
            withAnimation { isPressed = pressing }
        }, perform: {})
    }

    private func trigger() {
        #if os(iOS)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        #endif
        action()
    }
}

#Preview {
    SettingsView(listener: nil)
}
