//
//  JoinMultiplayerRoomView.swift
//  DoMemory
//

import SwiftUI

struct JoinMultiplayerRoomView: View {
    @State private var code = ""
    @State private var isJoining = false
    @Environment(\.dismiss) private var dismiss

    private var normalizedCode: String {
        code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    }

    private var canJoin: Bool {
        normalizedCode.count == 6
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()

                VStack(alignment: .leading, spacing: 22) {
                    Text(Strings.multiplayerJoinDescription)
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundStyle(Color.textMuted)
                        .padding(.top, 8)

                    TextField(
                        "",
                        text: $code,
                        prompt: Text(Strings.multiplayerCodePlaceholder)
                            .foregroundStyle(Color.textMuted)
                    )
                    .font(.system(size: 30, weight: .heavy, design: .monospaced))
                    .foregroundStyle(Color.textPrimary)
                    .tracking(4)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .multilineTextAlignment(.center)
                    .padding(18)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(Color.surfacePrimary)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .stroke(Color.surfaceBorder, lineWidth: 1)
                            )
                    )
                    .onChange(of: code) { _, newValue in
                        code = String(newValue.uppercased().filter { $0.isLetter || $0.isNumber }.prefix(6))
                    }
                    .onSubmit {
                        if canJoin { isJoining = true }
                    }

                    Button {
                        HapticsService.shared.fire(.tap)
                        isJoining = true
                    } label: {
                        Label(Strings.multiplayerJoinRoom, systemImage: "arrow.right.circle.fill")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 15)
                            .background(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .fill(canJoin ? Color.primaryColor : Color.textMuted.opacity(0.45))
                            )
                    }
                    .buttonStyle(.plain)
                    .disabled(!canJoin)

                    Spacer()
                }
                .padding(.horizontal, 16)
            }
            .navigationTitle(Strings.multiplayerJoinRoom)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(Strings.cancel) { dismiss() }
                        .foregroundStyle(Color.textMuted)
                }
            }
            .navigationDestination(isPresented: $isJoining) {
                MultiplayerRoomView(entryMode: .join(normalizedCode))
            }
        }
    }
}

