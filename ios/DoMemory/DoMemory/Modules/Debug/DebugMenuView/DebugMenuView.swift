//
//  DebugMenuView.swift
//  DoMemory
//
//  Simulator-only QA panel, reachable via `debugMenuTapTrigger` on the
//  Settings title. Every row is a single call into an existing service
//  singleton — there is no local state to own beyond which nested preview
//  cover is showing and the lives-reset feedback label below.
//
//  Strings here are deliberately plain English literals rather than routed
//  through Strings.swift/Localizable.strings: this view is unreachable by any
//  real user, reviewer or translator, so satisfying `LocalizationParityTests`
//  for QA-only copy would mean translating text nobody will ever see into all
//  ten locales.
//

#if targetEnvironment(simulator)
import ReviewFlow
import StoreKit
import SwiftUI

struct DebugMenuView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.requestReview) private var requestReview
    @Bindable private var purchaseService = PurchaseService.shared

    @State private var showOnboardingPreview = false
    @State private var showLevelsIntroPreview = false
    @State private var showNotificationPrimerPreview = false
    @State private var showReviewInvitationPreview = false
    @State private var livesStatus = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Toggle(isOn: Binding(
                        get: { purchaseService.hasRemovedAds },
                        set: { isOn in
                            if isOn {
                                purchaseService.grantRewardedRemoveAds(duration: 365 * 24 * 60 * 60)
                            } else {
                                purchaseService.debugClearRemoveAds()
                            }
                        }
                    )) {
                        DebugRowLabel(
                            title: "Disable ads",
                            subtitle: "Grants/clears a year-long rewarded remove-ads window"
                        )
                    }
                }

                Section {
                    Button {
                        resetOnboarding()
                        showOnboardingPreview = true
                    } label: {
                        DebugRowLabel(
                            title: "Start onboarding",
                            subtitle: "Resets onboarding state and replays the first-launch carousel"
                        )
                    }

                    Button {
                        showLevelsIntroPreview = true
                    } label: {
                        DebugRowLabel(
                            title: "Start Levels onboarding",
                            subtitle: "Replays the Levels intro carousel, same as the \"?\" button"
                        )
                    }

                    Button {
                        showNotificationPrimerPreview = true
                    } label: {
                        DebugRowLabel(
                            title: "Show notifications view",
                            subtitle: "Replays the reminder permission primer"
                        )
                    }

                    Button {
                        requestReview()
                    } label: {
                        DebugRowLabel(
                            title: "Show ask-for-review view (native)",
                            subtitle: "Bypasses the eligibility gate. Apple's review UI does not render on Simulator — this only confirms the call fires."
                        )
                    }

                    Button {
                        showReviewInvitationPreview = true
                    } label: {
                        DebugRowLabel(
                            title: "Show ReviewFlow invitation view",
                            subtitle: "ReviewFlow's own SwiftUI pre-prompt sheet — not currently adopted by the app, but renders normally on Simulator, unlike the native prompt above"
                        )
                    }
                }

                Section {
                    Button {
                        LevelLivesService.shared.restoreFullLives()
                        livesStatus = "Restored: \(LevelLivesService.shared.livesRemaining())/\(LevelLivesService.maxLives) lives"
                    } label: {
                        DebugRowLabel(
                            title: "Restart lives",
                            subtitle: livesStatus.isEmpty ? "Refills today's Levels lives budget" : livesStatus
                        )
                    }
                }
            }
            .navigationTitle("Debug Menu")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .fullScreenCover(isPresented: $showOnboardingPreview) {
            HomeView(onDidComplete: { showOnboardingPreview = false })
        }
        .fullScreenCover(isPresented: $showLevelsIntroPreview) {
            LevelsIntroView(source: "debug_menu") { showLevelsIntroPreview = false }
        }
        .sheet(isPresented: $showNotificationPrimerPreview) {
            NotificationPrimerView(source: "debug_menu") { showNotificationPrimerPreview = false }
        }
        .sheet(isPresented: $showReviewInvitationPreview) {
            ReviewInvitationSheet(appID: InviteLink.appStoreID)
        }
    }

    private func resetOnboarding() {
        UserManageObject().clearAll()
        UserDefaults.standard.removeObject(forKey: UserDefaultsKeys.onboardingIntroShown)
    }
}

private struct DebugRowLabel: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
            Text(subtitle)
                .font(.system(size: 12))
                .foregroundStyle(.secondary)
        }
    }
}

#Preview {
    DebugMenuView()
}
#endif
