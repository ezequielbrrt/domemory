//
//  LivesRow.swift
//  DoMemory
//
//  Row of heart glyphs showing remaining Levels-mode lives. Reused by the
//  Levels map header and the out-of-lives prompts (LoseModal, OutOfLivesModal).
//

import SwiftUI

struct LivesRow: View {
    var remaining: Int
    var total: Int = LevelLivesService.maxLives
    var iconSize: CGFloat = 16

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<total, id: \.self) { index in
                Image(systemName: index < remaining ? "heart.fill" : "heart")
                    .font(.system(size: iconSize, weight: .semibold))
                    .foregroundStyle(index < remaining ? Color.secundaryColor : Color.textMuted.opacity(0.3))
            }
        }
        .accessibilityElement()
        .accessibilityLabel(Strings.livesRemainingFormat(remaining, total))
    }
}

#Preview {
    VStack(spacing: 16) {
        LivesRow(remaining: 4)
        LivesRow(remaining: 2)
        LivesRow(remaining: 0)
    }
    .padding()
}
