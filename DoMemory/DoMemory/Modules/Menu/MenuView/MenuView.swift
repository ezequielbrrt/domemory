//
//  MenuView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import WaterfallGrid
import AppTrackingTransparency
import GoogleMobileAds

private enum GameTab { case all, mine }

struct MenuView: View {
    @State private var viewModel = MenuViewModel()
    @State var showNewView = false
    @State var showBanner = false
    @State private var showCreateSheet = false
    @State private var showJoinMultiplayerSheet = false
    @State private var selectedTab: GameTab = .all
    @State private var randomMemorama: Memorama?
    @State private var statsRefreshID = UUID()
    @State private var purchaseService = PurchaseService.shared

    private var displayedGames: [Memorama] {
        switch selectedTab {
        case .all:  return viewModel.memoramaArray.filter { !$0.id.hasPrefix("custom_") }
        case .mine: return viewModel.memoramaArray.filter {  $0.id.hasPrefix("custom_") }
        }
    }

    private var multiplayerAvailableGames: [Memorama] {
        viewModel.memoramaArray
    }

    var body: some View {
        Group {
            if viewModel.isLoading {
                ZStack {
                    Color.appBackground.ignoresSafeArea()
                    LoadingView().padding()
                }
            } else {
                NavigationStack {
                    ZStack {
                        Color.appBackground.ignoresSafeArea()

                        VStack(spacing: 0) {
                            // Header
                            HStack {
                                Text(Strings.appName)
                                    .font(.righteous(size: 30))
                                    .foregroundStyle(Color.primaryColor)

                                Spacer()

                                Button(action: { self.showJoinMultiplayerSheet = true }) {
                                    Image(systemName: "person.2.fill")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundStyle(Color.primaryColor)
                                        .frame(width: 38, height: 38)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(Color.surfacePrimary)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                        .stroke(Color.surfaceBorder, lineWidth: 1)
                                                )
                                                .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 3)
                                        )
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(Strings.multiplayerJoinRoom)

                                Button(action: { self.showCreateSheet = true }) {
                                    Image(systemName: "plus")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundStyle(Color.primaryColor)
                                        .frame(width: 38, height: 38)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(Color.surfacePrimary)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                        .stroke(Color.surfaceBorder, lineWidth: 1)
                                                )
                                                .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 3)
                                        )
                                }
                                .buttonStyle(.plain)

                                Button(action: { self.showNewView = true }) {
                                    Image(systemName: "gearshape.fill")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundStyle(Color.textPrimary.opacity(0.6))
                                        .frame(width: 38, height: 38)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(Color.surfacePrimary)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                        .stroke(Color.surfaceBorder, lineWidth: 1)
                                                )
                                                .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 3)
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(.horizontal, 16)
                            .padding(.top, 12)
                            .padding(.bottom, 8)

                            // Tab selector
                            GameTabPicker(selected: $selectedTab)
                                .padding(.horizontal, 16)
                                .padding(.bottom, 8)

                            // Difficulty badge (All tab only)
                            if selectedTab == .all,
                               let first = displayedGames.first {
                                let difficultyEnum = Difficulty(rawValue: first.difficulty) ?? .medium
                                HStack(spacing: 5) {
                                    Image(systemName: "brain.head.profile")
                                        .font(.system(size: 12, weight: .semibold))
                                    Text(difficultyEnum.rawValue.capitalized)
                                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                                }
                                .foregroundStyle(Color.primaryColor)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 6)
                                .background(Capsule().fill(Color.primaryColor.opacity(0.1)))
                                .padding(.horizontal, 16)
                                .padding(.bottom, 8)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }

                            if !displayedGames.isEmpty {
                                Button {
                                    randomMemorama = viewModel.randomGame(for: selectedVisibleTab)
                                } label: {
                                    HStack(spacing: 10) {
                                        Image(systemName: "shuffle")
                                            .font(.system(size: 15, weight: .bold))
                                        Text(Strings.menuRandomGame)
                                            .font(.system(size: 16, weight: .bold, design: .rounded))
                                    }
                                    .foregroundStyle(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(
                                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                                            .fill(Color.primaryColor)
                                            .shadow(color: Color.primaryColor.opacity(0.25), radius: 12, x: 0, y: 4)
                                    )
                                }
                                .buttonStyle(.plain)
                                .padding(.horizontal, 16)
                                .padding(.bottom, 12)
                            }

                            // Grid or empty state
                            if displayedGames.isEmpty && selectedTab == .mine {
                                Spacer()
                                VStack(spacing: 16) {
                                    Image(systemName: "plus.square.dashed")
                                        .font(.system(size: 52))
                                        .foregroundStyle(Color.primaryColor.opacity(0.4))
                                    Text(Strings.emptyMyGames)
                                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                                        .foregroundStyle(Color.textMuted)
                                        .multilineTextAlignment(.center)
                                    Button(action: { showCreateSheet = true }) {
                                        Text(Strings.emptyMyGamesAction)
                                            .font(.system(size: 15, weight: .bold, design: .rounded))
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 24)
                                            .padding(.vertical, 12)
                                            .background(
                                                Capsule().fill(Color.primaryColor)
                                            )
                                    }
                                    .buttonStyle(.plain)
                                }
                                .padding(.horizontal, 40)
                                Spacer()
                            } else {
                                ScrollView {
                                    WaterfallGrid(displayedGames) { (memorama: Memorama) in
                                        MemoramaGridCell(
                                            memorama: memorama,
                                            availableMemoramas: multiplayerAvailableGames,
                                            stats: viewModel.stats(for: memorama.id),
                                            isFavorite: viewModel.isFavorite(id: memorama.id),
                                            onStatsChanged: { statsRefreshID = UUID() },
                                            onToggleFavorite: { viewModel.toggleFavorite(id: memorama.id) },
                                            onDelete: memorama.id.hasPrefix("custom_")
                                                ? { viewModel.deleteCustomMemorama(id: memorama.id) }
                                                : nil
                                        )
                                    }
                                    .gridStyle(
                                        columns: 2,
                                        spacing: 14,
                                        animation: Animation.spring(response: 0.35, dampingFraction: 0.85)
                                    )
                                    .id(statsRefreshID)
                                    .padding(.horizontal, 16)
                                    .padding(.bottom, 16)
                                }
                            }

                            if !purchaseService.hasRemovedAds,
                               AdsService.shared.isBannerConfigured(for: .homeBanner) {
                                AdMobBannerView(placement: .homeBanner)
                                    .frame(height: 50)
                                    .padding(.bottom, 8)
                            }
                        }
                    }
                    .navigationBarHidden(true)
                    .navigationDestination(isPresented: $showNewView) {
                        SettingsView(listener: viewModel)
                    }
                    .navigationDestination(item: $randomMemorama) { memorama in
                        MemorizeView(
                            viewModel: MemorizeViewModel(memorama: memorama),
                            gameStartSource: "random_menu_button"
                        )
                        .onDisappear {
                            statsRefreshID = UUID()
                        }
                    }
                    .sheet(isPresented: $showCreateSheet) {
                        CreateMemoramaView(
                            currentDifficulty: viewModel.currentDifficulty,
                            onSave: { viewModel.addCustomMemorama($0) }
                        )
                    }
                    .sheet(isPresented: $showJoinMultiplayerSheet) {
                        JoinMultiplayerRoomView()
                    }
                }
            }
        }
        .task {
            if UIApplication.shared.applicationState != .active {
                for await _ in NotificationCenter.default
                    .notifications(named: UIApplication.didBecomeActiveNotification)
                    .prefix(1) {}
            }
            await ATTrackingManager.requestTrackingAuthorization()
            await MobileAds.shared.start()
            _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
        }
        .onAppear {
            statsRefreshID = UUID()
            AdsService.shared.registerMenuReadyForAppOpenAds()
            AnalyticsService.log(.screenView(name: "menu", screenClass: "MenuView"))
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            AdsService.shared.presentAppOpenAdIfAvailable()
        }
    }

    private var selectedVisibleTab: MenuViewModel.VisibleTab {
        switch selectedTab {
        case .all:
            return .all
        case .mine:
            return .mine
        }
    }
}

private struct GameTabPicker: View {
    @Binding var selected: GameTab

    var body: some View {
        HStack(spacing: 0) {
            tabButton(label: Strings.tabAll, tab: .all)
            tabButton(label: Strings.tabMine, tab: .mine)
        }
        .padding(4)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.surfaceSecondary)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.surfaceBorder, lineWidth: 1)
                )
        )
    }

    private func tabButton(label: String, tab: GameTab) -> some View {
        let isSelected = selected == tab
        return Button(action: { withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { selected = tab } }) {
            Text(label)
                .font(.system(size: 14, weight: isSelected ? .bold : .regular, design: .rounded))
                .foregroundStyle(isSelected ? Color.primaryColor : Color.textMuted)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(
                    Group {
                        if isSelected {
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.surfacePrimary)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .stroke(Color.surfaceBorder, lineWidth: 1)
                                )
                                .shadow(color: Color.shadowColor, radius: 4, x: 0, y: 2)
                        }
                    }
                )
        }
        .buttonStyle(.plain)
    }
}

private struct MemoramaGridCell: View {
    let memorama: Memorama
    let availableMemoramas: [Memorama]
    let stats: GameStats
    let isFavorite: Bool
    let onStatsChanged: () -> Void
    let onToggleFavorite: () -> Void
    let onDelete: (() -> Void)?

    @State private var isNavigating = false
    @State private var isStartingMultiplayer = false

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Button {
                isNavigating = true
            } label: {
                MemoramaCard(memorama: memorama, stats: stats)
            }
            .buttonStyle(.plain)
            .navigationDestination(isPresented: $isNavigating) {
                MemorizeView(
                    viewModel: MemorizeViewModel(memorama: memorama),
                    gameStartSource: "menu_card"
                )
                    .onDisappear(perform: onStatsChanged)
            }
            .contextMenu {
                Button {
                    isStartingMultiplayer = true
                } label: {
                    Label(Strings.multiplayerCreateRoom, systemImage: "person.2.fill")
                }

                if let onDelete {
                    Button(role: .destructive, action: onDelete) {
                        Label(Strings.delete, systemImage: "trash")
                    }
                }
            }

            VStack(spacing: 4) {
                Button(action: onToggleFavorite) {
                    Image(systemName: isFavorite ? "heart.fill" : "heart")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(isFavorite ? Color.secundaryColor : Color.textMuted)
                        .padding(8)
                }
                .buttonStyle(.plain)

                Button {
                    isStartingMultiplayer = true
                } label: {
                    Image(systemName: "person.2.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.primaryColor)
                        .padding(8)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Strings.multiplayerCreateRoom)
            }
            .padding(.top, 4)
        }
        .navigationDestination(isPresented: $isStartingMultiplayer) {
            MultiplayerRoomView(entryMode: .create(memorama), availableMemoramas: availableMemoramas)
        }
    }
}

struct MemoramaCard: View {
    var memorama: Memorama
    var stats: GameStats

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.surfacePrimary)
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(Color.surfaceBorder, lineWidth: 1)
                )
                .shadow(color: Color.shadowColor, radius: 10, x: 0, y: 4)
                .frame(height: 156)

            VStack(spacing: 10) {
                if let first = memorama.items.first {
                    Text(first)
                        .font(.system(size: 44))
                }
                Text(memorama.name)
                    .font(.system(size: 15, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)

                HStack(spacing: 8) {
                    StatBadge(label: Strings.statsPlayed, value: stats.playedCount, color: Color.primaryColor)
                    StatBadge(label: Strings.statsWon, value: stats.wonCount, color: Color.easyGreen)
                }
            }
            .padding(12)
        }
        .padding(.vertical, 4)
    }
}

private struct StatBadge: View {
    let label: String
    let value: Int
    let color: Color

    var body: some View {
        HStack(spacing: 4) {
            Text("\(value)")
                .font(.system(size: 11, weight: .heavy, design: .rounded))
                .foregroundStyle(color)

            Text(label)
                .font(.system(size: 10, weight: .semibold, design: .rounded))
                .foregroundStyle(Color.textMuted)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .background(
            Capsule()
                .fill(color.opacity(0.1))
        )
    }
}

// MARK: - Create Memorama Sheet

private struct CreateMemoramaView: View {
    let currentDifficulty: String
    let onSave: (Memorama) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var items: [String] = []
    @State private var newEmoji: String = ""
    @State private var isAddingEmoji = false

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && items.count >= 2
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        // Name field
                        VStack(alignment: .leading, spacing: 8) {
                            Text(Strings.createName)
                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                                .foregroundStyle(Color.textMuted)

                            TextField(
                                "",
                                text: $name,
                                prompt: Text(Strings.createNamePlaceholder)
                                    .foregroundStyle(Color.textMuted)
                            )
                                .font(.system(size: 17, weight: .semibold, design: .rounded))
                                .foregroundStyle(Color.textPrimary)
                                .padding(14)
                                .background(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(Color.surfacePrimary)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                                .stroke(Color.surfaceBorder, lineWidth: 1)
                                        )
                                        .shadow(color: Color.shadowColor, radius: 6, x: 0, y: 2)
                                )
                        }

                        // Items section
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Text("\(Strings.createEmojisCount) (\(items.count))")
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                                    .foregroundStyle(Color.textMuted)
                                Spacer()
                                Text(Strings.createMinimum)
                                    .font(.system(size: 11))
                                    .foregroundStyle(Color.textMuted.opacity(0.6))
                            }

                            if !items.isEmpty {
                                VStack(spacing: 8) {
                                    ForEach(Array(items.enumerated()), id: \.offset) { index, emoji in
                                        HStack {
                                            Text(emoji)
                                                .font(.system(size: 36))
                                                .frame(width: 50)
                                            Text(emoji)
                                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                                .foregroundStyle(Color.textMuted)
                                            Spacer()
                                            Button {
                                                items.remove(at: index)
                                            } label: {
                                                Image(systemName: "minus.circle.fill")
                                                    .foregroundStyle(Color.secundaryColor.opacity(0.8))
                                                    .font(.system(size: 20))
                                            }
                                            .buttonStyle(.plain)
                                        }
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 10)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(Color.surfacePrimary)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                        .stroke(Color.surfaceBorder, lineWidth: 1)
                                                )
                                        )
                                    }
                                }
                            }

                            if isAddingEmoji {
                                HStack(spacing: 10) {
                                    TextField(
                                        "",
                                        text: $newEmoji,
                                        prompt: Text("😀")
                                            .foregroundStyle(Color.textMuted)
                                    )
                                        .font(.system(size: 32))
                                        .foregroundStyle(Color.textPrimary)
                                        .frame(width: 50)
                                        .multilineTextAlignment(.center)
                                        .onChange(of: newEmoji) { _, value in
                                            if value.count > 1 {
                                                newEmoji = String(value.prefix(1))
                                            }
                                        }

                                    Button(Strings.createAdd) {
                                        let trimmed = String(newEmoji.prefix(1))
                                        guard !trimmed.isEmpty, !items.contains(trimmed) else { return }
                                        items.append(trimmed)
                                        newEmoji = ""
                                        isAddingEmoji = false
                                    }
                                    .font(.system(size: 15, weight: .bold, design: .rounded))
                                    .foregroundStyle(newEmoji.isEmpty ? Color.textMuted : Color.primaryColor)
                                    .disabled(newEmoji.isEmpty)

                                    Button(Strings.cancel) {
                                        newEmoji = ""
                                        isAddingEmoji = false
                                    }
                                    .font(.system(size: 15, design: .rounded))
                                    .foregroundStyle(Color.textMuted)
                                }
                                .padding(14)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(Color.surfacePrimary)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .strokeBorder(Color.surfaceBorder, lineWidth: 1)
                                        )
                                )
                            }

                            if !isAddingEmoji {
                                Button {
                                    isAddingEmoji = true
                                } label: {
                                    HStack {
                                        Image(systemName: "plus.circle.fill")
                                        Text(Strings.createAddEmoji)
                                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                                    }
                                    .foregroundStyle(Color.primaryColor)
                                    .padding(14)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .fill(Color.primaryColor.opacity(0.12))
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 32)
                }
            }
            .navigationTitle(Strings.createTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(Strings.cancel) { dismiss() }
                        .foregroundStyle(Color.textMuted)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(Strings.createSave) {
                        let memorama = Memorama(
                            id: "custom_" + UUID().uuidString,
                            name: name.trimmingCharacters(in: .whitespaces),
                            category: "custom",
                            difficulty: currentDifficulty,
                            description: "",
                            publishedDate: ISO8601DateFormatter().string(from: Date()),
                            items: items,
                            itemType: "string",
                            isDoubleItem: false
                        )
                        onSave(memorama)
                        dismiss()
                    }
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .foregroundStyle(canSave ? Color.primaryColor : Color.textMuted)
                    .disabled(!canSave)
                }
            }
        }
    }
}

#Preview {
    MenuView()
}
