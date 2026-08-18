//
//  SettingsView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI
import UserNotifications

struct SettingsView: View {
    @State private var viewModel: SettingsViewModel
    @Bindable private var purchaseService = PurchaseService.shared
    @State private var showDifficultyPicker = false
    @State private var showThemePicker = false
    @State private var isRewardedAdInProgress = false
    @State private var showNotificationsDeniedAlert = false
    @State private var showAchievements = false
    @AppStorage(UserDefaultsKeys.themePreference) private var themePreference = AppTheme.system.rawValue
    @AppStorage(UserDefaultsKeys.notificationsEnabled) private var notificationsEnabled = false
    @AppStorage(UserDefaultsKeys.hapticsEnabled) private var hapticsEnabled = true


    init(listener: SettingsListener?) {
        self.viewModel = SettingsViewModel(listener: listener)
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

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
                        title: Strings.achievementsTitle,
                        subtitle: Strings.achievementsSubtitle,
                        actionTitle: Strings.achievementsView,
                        systemImage: "trophy.fill"
                    ) {
                        HapticsService.shared.fire(.tap)
                        showAchievements = true
                    }

                    SettingsOptionRow(
                        title: Strings.settingsThemeTitle,
                        subtitle: selectedThemeTitle,
                        actionTitle: Strings.settingsSelectTheme,
                        systemImage: "circle.lefthalf.filled"
                    ) {
                        HapticsService.shared.fire(.tap)
                        showThemePicker = true
                    }

                    SettingsOptionRow(
                        title: Strings.difficulty,
                        subtitle: selectedDifficultyTitle,
                        actionTitle: Strings.settingsSelectDifficulty,
                        systemImage: "slider.horizontal.3"
                    ) {
                        HapticsService.shared.fire(.tap)
                        showDifficultyPicker = true
                    }

                    SettingsOptionRow(
                        title: Strings.settingsNotificationsTitle,
                        subtitle: notificationsEnabled
                            ? Strings.settingsNotificationsDescriptionOn
                            : Strings.settingsNotificationsDescriptionOff,
                        actionTitle: notificationsEnabled
                            ? Strings.settingsNotificationsDisable
                            : Strings.settingsNotificationsEnable,
                        systemImage: "bell.fill"
                    ) {
                        HapticsService.shared.fire(.tap)
                        if notificationsEnabled {
                            notificationsEnabled = false
                            NotificationService.shared.cancelAll()
                        } else {
                            Task { await handleEnableNotifications() }
                        }
                    }

                    SettingsOptionRow(
                        title: Strings.settingsHapticsTitle,
                        subtitle: hapticsEnabled
                            ? Strings.settingsHapticsDescriptionOn
                            : Strings.settingsHapticsDescriptionOff,
                        actionTitle: hapticsEnabled
                            ? Strings.settingsHapticsDisable
                            : Strings.settingsHapticsEnable,
                        systemImage: "iphone.radiowaves.left.and.right"
                    ) {
                        // Fire before flipping the flag so turning haptics OFF
                        // still confirms the tap; turning them on is confirmed
                        // by the row's own tap handler on the next press.
                        HapticsService.shared.fire(.tap)
                        hapticsEnabled.toggle()
                    }

                    SettingsOptionRow(
                        title: Strings.settingsRemoveAdsTitle,
                        subtitle: removeAdsSubtitle,
                        actionTitle: removeAdsActionTitle,
                        systemImage: "nosign",
                        isDisabled: purchaseService.hasRemovedAds || purchaseService.isLoading
                    ) {
                        HapticsService.shared.fire(.tap)
                        Task {
                            await purchaseService.purchaseRemoveAds()
                        }
                    }

                    // Rewarded ads are no longer gated on the entitlement, so this
                    // row would otherwise appear — permanently disabled — for
                    // someone who already owns ad-free forever. This one reward
                    // is genuinely worthless to them, unlike the in-game ones.
                    if !purchaseService.hasPurchasedRemoveAds,
                       AdsService.shared.isRewardedConfigured(for: .settingsRewardedRemoveAds)
                        || purchaseService.hasActiveRewardedRemoveAds {
                        SettingsOptionRow(
                            title: Strings.settingsRewardedRemoveAdsTitle,
                            subtitle: rewardedRemoveAdsSubtitle,
                            actionTitle: rewardedRemoveAdsActionTitle,
                            systemImage: "play.rectangle.fill",
                            isDisabled: purchaseService.hasRemovedAds || isRewardedAdInProgress
                        ) {
                            HapticsService.shared.fire(.tap)
                            presentRewardedRemoveAds()
                        }
                    }

                    SettingsOptionRow(
                        title: Strings.settingsRestorePurchasesTitle,
                        subtitle: Strings.settingsRestorePurchasesDescription,
                        actionTitle: Strings.settingsRestorePurchasesAction,
                        systemImage: "arrow.clockwise",
                        isDisabled: purchaseService.isLoading
                    ) {
                        HapticsService.shared.fire(.tap)
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
        .navigationDestination(isPresented: $showAchievements) {
            AchievementsView()
        }
        .alert(item: $purchaseService.purchaseAlert) { alert in
            Alert(
                title: Text(alert.title),
                message: Text(alert.message),
                dismissButton: .default(Text(Strings.ok))
            )
        }
        .onAppear {
            viewModel.difficulty = viewModel.getCurrentDifficulty()
            Task {
                await purchaseService.refreshPurchasedProducts()
                await purchaseService.loadProducts()
                AdsService.shared.loadRewardedAd(for: .settingsRewardedRemoveAds)
                await NotificationService.shared.syncAuthorizationStatus()
            }
        }
        .alert(Strings.settingsNotificationsDeniedTitle, isPresented: $showNotificationsDeniedAlert) {
            Button(Strings.settingsNotificationsOpenSettings) {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            Button(Strings.cancel, role: .cancel) {}
        } message: {
            Text(Strings.settingsNotificationsDeniedMessage)
        }
        .onDisappear {
            viewModel.viewWillDissapear()
        }
        .sheet(isPresented: $showDifficultyPicker) {
            DifficultyPickerSheet(
                selectedDifficulty: viewModel.difficulty,
                onSelect: { difficultyIndex in
                    HapticsService.shared.fire(.tap)
                    viewModel.saveDifficulty(difficultyIndex: difficultyIndex)
                    showDifficultyPicker = false
                }
            )
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showThemePicker) {
            ThemePickerSheet(
                selectedTheme: AppTheme(rawValue: themePreference) ?? .system,
                onSelect: { theme in
                    HapticsService.shared.fire(.tap)
                    themePreference = theme.rawValue
                    showThemePicker = false
                }
            )
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
    }

    private func handleEnableNotifications() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        switch settings.authorizationStatus {
        case .denied:
            showNotificationsDeniedAlert = true
        case .notDetermined:
            let granted = await NotificationService.shared.requestPermission()
            if granted {
                notificationsEnabled = true
                NotificationService.shared.scheduleInactivityReminder()
                NotificationService.shared.refreshStreakAtRiskReminder()
            }
        case .authorized, .provisional, .ephemeral:
            notificationsEnabled = true
            NotificationService.shared.scheduleInactivityReminder()
            NotificationService.shared.refreshStreakAtRiskReminder()
        @unknown default:
            break
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
        if purchaseService.hasPurchasedRemoveAds {
            return Strings.settingsRemoveAdsPurchasedDescription
        }

        return Strings.settingsRemoveAdsDescription
    }

    private var removeAdsActionTitle: String {
        if purchaseService.hasPurchasedRemoveAds {
            return Strings.settingsRemoveAdsPurchased
        }

        if purchaseService.isLoading {
            return Strings.settingsRemoveAdsLoading
        }

        return String(format: Strings.settingsRemoveAdsActionFormat, purchaseService.removeAdsPriceText)
    }

    private var rewardedRemoveAdsSubtitle: String {
        if purchaseService.hasActiveRewardedRemoveAds {
            return String(
                format: Strings.settingsRewardedRemoveAdsActiveFormat,
                purchaseService.rewardedRemoveAdsExpirationText
            )
        }

        return Strings.settingsRewardedRemoveAdsDescription
    }

    private var rewardedRemoveAdsActionTitle: String {
        if purchaseService.hasActiveRewardedRemoveAds {
            return Strings.settingsRewardedRemoveAdsActive
        }

        if isRewardedAdInProgress {
            return Strings.adLoading
        }

        return Strings.settingsRewardedRemoveAdsAction
    }

    private var selectedThemeTitle: String {
        AppTheme(rawValue: themePreference)?.title ?? Strings.themeSystem
    }

    private func presentRewardedRemoveAds() {
        guard !isRewardedAdInProgress else { return }
        isRewardedAdInProgress = true
        AnalyticsService.log(.adLifecycle(placement: AdPlacement.settingsRewardedRemoveAds.rawValue, action: "requested"))
        AdsService.shared.presentRewardedAd(
            for: .settingsRewardedRemoveAds,
            rewardHandler: {
                purchaseService.grantRewardedRemoveAds()
                AnalyticsService.log(.adLifecycle(placement: AdPlacement.settingsRewardedRemoveAds.rawValue, action: "reward_earned"))
            },
            completion: { didEarnReward in
                isRewardedAdInProgress = false
                AnalyticsService.log(.adLifecycle(placement: AdPlacement.settingsRewardedRemoveAds.rawValue, action: didEarnReward ? "dismissed_rewarded" : "dismissed_unrewarded"))
            }
        )
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
                    .fill(Color.primaryColor.opacity(0.14))
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
                    .background(Capsule().fill(isDisabled ? Color.surfaceSecondary : Color.primaryColor))
            }
            .buttonStyle(.plain)
            .disabled(isDisabled)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.surfacePrimary)
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(Color.surfaceBorder, lineWidth: 1)
                )
                .shadow(color: Color.shadowColor, radius: 10, x: 0, y: 4)
        )
    }
}

private struct DifficultyPickerSheet: View {
    let selectedDifficulty: Int
    let onSelect: (Int) -> Void

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

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
                        bgColor: Color.easyGreen.opacity(0.14),
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
                        bgColor: Color.primaryColor.opacity(0.14),
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
                        bgColor: Color.hardAmber.opacity(0.14),
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
                        bgColor: Color.secundaryColor.opacity(0.14),
                        systemImage: "bolt.trianglebadge.exclamationmark.fill",
                        isSelected: selectedDifficulty == 3,
                        isLast: true
                    ) {
                        onSelect(3)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(Color.surfaceBorder, lineWidth: 1)
                )
                .shadow(color: Color.shadowColor, radius: 10, x: 0, y: 4)
            }
            .padding(.horizontal, 20)
        }
    }
}

private struct ThemePickerSheet: View {
    let selectedTheme: AppTheme
    let onSelect: (AppTheme) -> Void

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 18) {
                VStack(spacing: 4) {
                    Text(Strings.settingsThemeTitle)
                        .font(.righteous(size: 32))
                        .foregroundStyle(Color.primaryColor)

                    Text(Strings.settingsThemePrompt)
                        .font(.patrickHand(size: 17))
                        .foregroundStyle(Color.textMuted)
                }

                VStack(spacing: 0) {
                    ThemeOptionRow(
                        title: Strings.themeSystem,
                        icon: "gearshape.2.fill",
                        accentColor: Color.primaryColor,
                        isSelected: selectedTheme == .system,
                        isLast: false
                    ) {
                        onSelect(.system)
                    }

                    ThemeOptionRow(
                        title: Strings.themeLight,
                        icon: "sun.max.fill",
                        accentColor: Color.hardAmber,
                        isSelected: selectedTheme == .light,
                        isLast: false
                    ) {
                        onSelect(.light)
                    }

                    ThemeOptionRow(
                        title: Strings.themeDark,
                        icon: "moon.fill",
                        accentColor: Color.secundaryColor,
                        isSelected: selectedTheme == .dark,
                        isLast: true
                    ) {
                        onSelect(.dark)
                    }
                }
                .background(Color.surfacePrimary)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(Color.surfaceBorder, lineWidth: 1)
                )
                .shadow(color: Color.shadowColor, radius: 10, x: 0, y: 4)
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
        HapticsService.shared.fire(.tap)
        action()
    }
}

private struct ThemeOptionRow: View {
    let title: String
    let icon: String
    let accentColor: Color
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
                    Image(systemName: icon)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(accentColor)
                }

                Text(title)
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.textPrimary)

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
            .background(Color.surfacePrimary)
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
        HapticsService.shared.fire(.tap)
        action()
    }
}

#Preview {
    SettingsView(listener: nil)
}
