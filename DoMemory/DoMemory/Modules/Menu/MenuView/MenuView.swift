//
//  MenuView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import WaterfallGrid

private enum GameTab { case all, mine }

struct MenuView: View {
    @State private var viewModel = MenuViewModel()
    @State var showNewView = false
    @State var showBanner = false
    @State private var showCreateSheet = false
    @State private var selectedTab: GameTab = .all

    private var displayedGames: [Memorama] {
        switch selectedTab {
        case .all:  return viewModel.memoramaArray.filter { !$0.id.hasPrefix("custom_") }
        case .mine: return viewModel.memoramaArray.filter {  $0.id.hasPrefix("custom_") }
        }
    }

    var body: some View {
        Group {
            if viewModel.isLoading {
                ZStack {
                    LinearGradient(
                        gradient: Gradient(colors: [Color.grayBackground, Color.darkGrayColor]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .ignoresSafeArea()
                    LoadingView().padding()
                }
            } else {
                NavigationStack {
                    ZStack {
                        // Background gradient + decoration
                        LinearGradient(
                            gradient: Gradient(colors: [Color.grayBackground, Color.darkGrayColor]),
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                        .ignoresSafeArea()

                        ZStack {
                            Circle()
                                .fill(Color.secundaryColor.opacity(0.08))
                                .frame(width: 300, height: 300)
                                .offset(x: -150, y: -280)
                            Circle()
                                .fill(Color.primaryColor.opacity(0.06))
                                .frame(width: 220, height: 220)
                                .offset(x: 160, y: 260)
                        }
                        .allowsHitTesting(false)

                        VStack(spacing: 0) {
                            // Header Title
                            HStack {
                                Text("DoMemory")
                                    .font(.righteous(size: 34))
                                    .foregroundStyle(Color.secundaryColor)
                                    .shadow(color: .black.opacity(0.2), radius: 6, x: 0, y: 4)
                                Spacer()
                                Button(action: { self.showCreateSheet = true }) {
                                    Image(systemName: "plus")
                                        .font(.system(size: 18, weight: .semibold))
                                        .foregroundStyle(Color.secundaryColor)
                                        .padding(10)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(Color.darkGrayColor.opacity(0.6))
                                        )
                                }
                                .buttonStyle(.plain)

                                Button(action: { self.showNewView = true }) {
                                    Image(systemName: "gearshape.fill")
                                        .font(.system(size: 18, weight: .semibold))
                                        .foregroundStyle(Color.primaryColor)
                                        .padding(10)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(Color.darkGrayColor.opacity(0.6))
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(.horizontal)
                            .padding(.top, 8)

                            // Tab selector
                            GameTabPicker(selected: $selectedTab)
                                .padding(.horizontal)
                                .padding(.top, 12)

                            // Difficulty badge (All tab only)
                            if selectedTab == .all,
                               let first = displayedGames.first {
                                let difficultyEnum = Difficulty(rawValue: first.difficulty) ?? .medium
                                HStack(spacing: 6) {
                                    Image(systemName: "brain.head.profile")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(Color.primaryColor.opacity(0.8))
                                    Text(difficultyEnum.rawValue.capitalized)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(Color.primaryColor.opacity(0.8))
                                }
                                .padding(.horizontal, 16)
                                .padding(.top, 6)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }

                            // Grid or empty state
                            if displayedGames.isEmpty && selectedTab == .mine {
                                Spacer()
                                VStack(spacing: 16) {
                                    Image(systemName: "plus.square.dashed")
                                        .font(.system(size: 52))
                                        .foregroundStyle(Color.secundaryColor.opacity(0.5))
                                    Text(Strings.emptyMyGames)
                                        .font(.patrickHand(size: 18))
                                        .foregroundStyle(Color.white.opacity(0.5))
                                        .multilineTextAlignment(.center)
                                    Button(action: { showCreateSheet = true }) {
                                        Text(Strings.emptyMyGamesAction)
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 24)
                                            .padding(.vertical, 10)
                                            .background(
                                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                    .fill(Color.secundaryColor)
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
                                            isFavorite: viewModel.isFavorite(id: memorama.id),
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
                                    .padding(.horizontal)
                                    .padding(.bottom, 16)
                                }
                            }
                        }
                    }
                    .navigationBarHidden(true)
                    .navigationDestination(isPresented: $showNewView) {
                        SettingsView(listener: viewModel)
                    }
                    .sheet(isPresented: $showCreateSheet) {
                        CreateMemoramaView(
                            currentDifficulty: viewModel.currentDifficulty,
                            onSave: { viewModel.addCustomMemorama($0) }
                        )
                    }
                }
            }
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
                .fill(Color.darkGrayColor.opacity(0.8))
        )
    }

    private func tabButton(label: String, tab: GameTab) -> some View {
        let isSelected = selected == tab
        return Button(action: { withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) { selected = tab } }) {
            Text(label)
                .font(.system(size: 14, weight: isSelected ? .semibold : .regular))
                .foregroundStyle(isSelected ? .white : Color.white.opacity(0.45))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(
                    Group {
                        if isSelected {
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.secundaryColor.opacity(0.85))
                        }
                    }
                )
        }
        .buttonStyle(.plain)
    }
}

private struct MemoramaGridCell: View {
    let memorama: Memorama
    let isFavorite: Bool
    let onToggleFavorite: () -> Void
    let onDelete: (() -> Void)?

    var body: some View {
        ZStack(alignment: .topTrailing) {
            NavigationLink {
                MemorizeView(viewModel: MemorizeViewModel(memorama: memorama))
            } label: {
                MemoramaCard(memorama: memorama)
            }
            .contextMenu {
                if let onDelete {
                    Button(role: .destructive, action: onDelete) {
                        Label(Strings.delete, systemImage: "trash")
                    }
                }
            }

            Button(action: onToggleFavorite) {
                Image(systemName: isFavorite ? "heart.fill" : "heart")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(isFavorite ? Color.red : Color.white.opacity(0.7))
                    .padding(8)
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
    }
}

struct MemoramaCard: View {
    var memorama: Memorama

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(
                    LinearGradient(
                        gradient: Gradient(colors: [Color.primaryColor, Color.secundaryColor]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.12), lineWidth: 1)
                )
                .frame(height: 140)
                .shadow(color: .black.opacity(0.25), radius: 8, x: 0, y: 6)

            VStack(spacing: 8) {
                if let first = memorama.items.first {
                    Text(first)
                        .font(.system(size: 52))
                }
                Text(memorama.name)
                    .font(.patrickHand(size: 18))
                    .foregroundStyle(.white.opacity(0.9))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .padding(12)
        }
        .padding(.vertical, 4)
    }
}

private struct ScaleCardButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
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
                LinearGradient(
                    gradient: Gradient(colors: [Color.grayBackground, Color.darkGrayColor]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        // Name field
                        VStack(alignment: .leading, spacing: 8) {
                            Text(Strings.createName)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Color.primaryColor.opacity(0.8))

                            TextField(Strings.createNamePlaceholder, text: $name)
                                .font(.patrickHand(size: 18))
                                .foregroundStyle(.white)
                                .padding(12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(Color.darkGrayColor.opacity(0.8))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .strokeBorder(Color.white.opacity(0.1), lineWidth: 1)
                                        )
                                )
                        }

                        // Items section
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Text("\(Strings.createEmojisCount) (\(items.count))")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(Color.primaryColor.opacity(0.8))
                                Spacer()
                                Text(Strings.createMinimum)
                                    .font(.system(size: 11))
                                    .foregroundStyle(Color.white.opacity(0.4))
                            }

                            // Existing items
                            if !items.isEmpty {
                                VStack(spacing: 8) {
                                    ForEach(Array(items.enumerated()), id: \.offset) { index, emoji in
                                        HStack {
                                            Text(emoji)
                                                .font(.system(size: 36))
                                                .frame(width: 50)
                                            Text(emoji)
                                                .font(.patrickHand(size: 16))
                                                .foregroundStyle(.white.opacity(0.7))
                                            Spacer()
                                            Button {
                                                items.remove(at: index)
                                            } label: {
                                                Image(systemName: "minus.circle.fill")
                                                    .foregroundStyle(.red.opacity(0.8))
                                                    .font(.system(size: 20))
                                            }
                                            .buttonStyle(.plain)
                                        }
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(
                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                .fill(Color.darkGrayColor.opacity(0.8))
                                        )
                                    }
                                }
                            }

                            // Add emoji input
                            if isAddingEmoji {
                                HStack(spacing: 10) {
                                    TextField("😀", text: $newEmoji)
                                        .font(.system(size: 32))
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
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(newEmoji.isEmpty ? Color.white.opacity(0.3) : Color.secundaryColor)
                                    .disabled(newEmoji.isEmpty)

                                    Button(Strings.cancel) {
                                        newEmoji = ""
                                        isAddingEmoji = false
                                    }
                                    .font(.system(size: 15))
                                    .foregroundStyle(Color.white.opacity(0.5))
                                }
                                .padding(12)
                                .background(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(Color.darkGrayColor.opacity(0.8))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                .strokeBorder(Color.secundaryColor.opacity(0.4), lineWidth: 1)
                                        )
                                )
                            }

                            // Add button
                            if !isAddingEmoji {
                                Button {
                                    isAddingEmoji = true
                                } label: {
                                    HStack {
                                        Image(systemName: "plus.circle.fill")
                                        Text(Strings.createAddEmoji)
                                            .font(.system(size: 15, weight: .medium))
                                    }
                                    .foregroundStyle(Color.secundaryColor)
                                    .padding(12)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(
                                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                                            .fill(Color.darkGrayColor.opacity(0.5))
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                    .strokeBorder(Color.secundaryColor.opacity(0.3), lineWidth: 1)
                                            )
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, 8)
                    .padding(.bottom, 32)
                }
            }
            .navigationTitle(Strings.createTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(Strings.cancel) { dismiss() }
                        .foregroundStyle(Color.white.opacity(0.7))
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
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(canSave ? Color.secundaryColor : Color.white.opacity(0.3))
                    .disabled(!canSave)
                }
            }
        }
    }
}

#Preview {
    MenuView()
}
