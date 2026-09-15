//
//  MenuDifficultyPicker.swift
//  DoMemory
//
//  Inline difficulty control for the All-games catalog.
//

import SwiftUI

struct MenuDifficultyPicker: View {
    let selectedDifficulty: Difficulty
    let onSelect: (Difficulty) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ForEach(Difficulty.allCases) { difficulty in
                let isSelected = difficulty == selectedDifficulty

                Button(action: { onSelect(difficulty) }) {
                    Text(difficulty.displayName)
                        .font(.system(size: 13, weight: isSelected ? .bold : .regular, design: .rounded))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .foregroundStyle(isSelected ? Color.surfacePrimary : Color.textMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            Capsule().fill(isSelected ? Color.primaryColor : Color.surfaceSecondary)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
    }
}

#Preview {
    MenuDifficultyPicker(selectedDifficulty: .medium, onSelect: { _ in })
        .padding()
        .background(Color.appBackground)
}
