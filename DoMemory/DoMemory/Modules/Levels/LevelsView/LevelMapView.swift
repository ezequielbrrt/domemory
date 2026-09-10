//
//  LevelMapView.swift
//  DoMemory
//
//  The level map shared by endless Levels and a finite Season: a scrollable
//  grid of numbered tiles — cleared (with stars, replayable), current
//  (highlighted), and locked — under a caller-supplied header.
//
//  The two modes differ only in that header and in which tiles they hand it,
//  so the grid, the tile styling and the tap rules live here once.
//

import SwiftUI

/// One tile in a level map.
///
/// Top-level rather than nested in `LevelsViewModel`, because a season's view
/// model builds the same tiles from its own progress store.
struct LevelTile: Identifiable, Hashable {
    enum State: Hashable {
        case cleared(stars: Int)
        case current
        case locked
    }

    let level: Int
    let state: State
    var id: Int { level }

    var isLocked: Bool {
        if case .locked = state { return true }
        return false
    }

    var isCurrent: Bool {
        if case .current = state { return true }
        return false
    }
}

struct LevelMapView<Header: View, Background: View>: View {
    let tiles: [LevelTile]
    /// Called as each tile scrolls into view. Endless Levels uses it to page
    /// the map lazily; a finite season renders all of its levels at once and
    /// leaves it at the default no-op.
    let onTileAppear: (LevelTile) -> Void
    /// Called for a tap on an *unlocked* tile. A locked tile is never
    /// selectable, so that rule is enforced here rather than in every caller.
    let onSelect: (LevelTile) -> Void
    let header: Header
    /// What the map is drawn on. Endless Levels uses the flat app background;
    /// a season passes its own Firebase-supplied artwork.
    let background: Background

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 18), count: 4)

    init(
        tiles: [LevelTile],
        onTileAppear: @escaping (LevelTile) -> Void = { _ in },
        onSelect: @escaping (LevelTile) -> Void,
        @ViewBuilder background: () -> Background,
        @ViewBuilder header: () -> Header
    ) {
        self.tiles = tiles
        self.onTileAppear = onTileAppear
        self.onSelect = onSelect
        self.background = background()
        self.header = header()
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                header

                LazyVGrid(columns: columns, spacing: 22) {
                    ForEach(tiles) { tile in
                        Button {
                            onSelect(tile)
                        } label: {
                            LevelTileView(tile: tile)
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(TileTapStyle())
                        .disabled(tile.isLocked)
                        .onAppear {
                            onTileAppear(tile)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 16)
            }
            .padding(.top, 8)
        }
        // The artwork is fixed while the tiles scroll over it, and reaches
        // under the safe areas so nothing bands at the top or bottom.
        .background {
            background.ignoresSafeArea()
        }
    }
}

extension LevelMapView where Background == Color {
    /// The map on the flat app background — the endless Levels map, and any
    /// season that carries no artwork.
    init(
        tiles: [LevelTile],
        onTileAppear: @escaping (LevelTile) -> Void = { _ in },
        onSelect: @escaping (LevelTile) -> Void,
        @ViewBuilder header: () -> Header
    ) {
        self.init(
            tiles: tiles,
            onTileAppear: onTileAppear,
            onSelect: onSelect,
            background: { Color.appBackground },
            header: header
        )
    }
}

/// Presses any tile down slightly and springs it back on release, so a tap
/// reads as physical contact rather than a silent jump to the next screen.
private struct TileTapStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.92 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

private struct LevelTileView: View {
    let tile: LevelTile

    private let nodeSize: CGFloat = 64

    /// Drives the current tile's idle pulse. Only ever animated for
    /// `.current` — every other state holds it at `false` and pays nothing.
    @State private var isPulsing = false

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                if tile.isCurrent {
                    // A ring that grows and fades outward, independent of the
                    // tile's own scale below — reads as an outward pulse
                    // rather than the whole tile breathing in place.
                    Circle()
                        .stroke(Color.primaryColor.opacity(0.55), lineWidth: 3)
                        .scaleEffect(isPulsing ? 1.45 : 1)
                        .opacity(isPulsing ? 0 : 0.7)
                }

                Circle()
                    .fill(fillColor)
                    .overlay(
                        Circle().stroke(borderColor, lineWidth: tile.isCurrent ? 3 : 1.5)
                    )
                    .shadow(color: Color.shadowColor, radius: tile.isLocked ? 0 : 8, x: 0, y: 4)
                    .scaleEffect(tile.isCurrent && isPulsing ? 1.06 : 1)

                content
            }
            .frame(width: nodeSize, height: nodeSize)

            starsRow
        }
        .opacity(tile.isLocked ? 0.55 : 1)
        .onAppear {
            guard tile.isCurrent else { return }
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) {
                isPulsing = true
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch tile.state {
        case .locked:
            Image(systemName: "lock.fill")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color.textMuted)

        case .current:
            Text("\(tile.level)")
                .font(.system(size: 26, weight: .heavy, design: .rounded))
                .foregroundStyle(.white)
                .minimumScaleFactor(0.6)
                .lineLimit(1)

        case .cleared:
            Text("\(tile.level)")
                .font(.system(size: 24, weight: .bold, design: .rounded))
                .foregroundStyle(Color.textPrimary)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
        }
    }

    /// Reserves a consistent row height across every tile so cleared tiles'
    /// star ratings don't shift the grid's row spacing.
    @ViewBuilder
    private var starsRow: some View {
        if case .cleared(let stars) = tile.state {
            HStack(spacing: 2) {
                ForEach(0..<3, id: \.self) { index in
                    Image(systemName: index < stars ? "star.fill" : "star")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(index < stars ? Color.hardAmber : Color.textMuted.opacity(0.3))
                }
            }
            .frame(height: 14)
        } else {
            Color.clear.frame(height: 14)
        }
    }

    private var fillColor: Color {
        switch tile.state {
        case .current: return Color.primaryColor
        case .cleared: return Color.surfacePrimary
        case .locked: return Color.surfaceSecondary
        }
    }

    private var borderColor: Color {
        switch tile.state {
        case .current: return Color.primaryColor
        case .cleared, .locked: return Color.surfaceBorder
        }
    }
}
