//
//  MemorizeView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI

struct MemorizeView: View {
    @State var viewModel: MemorizeViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: MemorizeViewModel) {
        self._viewModel = State(initialValue: viewModel)
    }

    private var gridColumns: Int {
        max(1, Int(ceil(sqrt(Double(viewModel.cards.count)))))
    }

    var body: some View {
        ZStack {
            VStack {
                HStack {
                    Image("error")
                        .resizable()
                        .frame(width: 40, height: 40, alignment: .center)
                        .padding()
                        .onTapGesture {
                            viewModel.stopTimer()
                            viewModel.showQuitView.toggle()
                        }

                    Spacer()
                    HStack(spacing: 4) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.red.opacity(0.8))
                        Text("\(viewModel.failedTries)")
                            .font(.patrickHand(size: 25))
                            .foregroundStyle(Color.primaryColor)
                    }
                    Spacer()
                    Text(Strings.time + " : \(viewModel.timeRemaining)")
                        .font(.patrickHand(size: 25))
                        .foregroundStyle(Color.primaryColor)
                    Spacer()
                    Image("cronografo")
                        .resizable()
                        .frame(width: 45, height: 45, alignment: .center).padding()
                        .onTapGesture {
                            viewModel.stopTimer()
                            viewModel.showPauseView.toggle()
                        }
                }

                GeometryReader { geo in
                    let cols = gridColumns
                    let rows = max(1, Int(ceil(Double(viewModel.cards.count) / Double(cols))))
                    let spacing: CGFloat = 8
                    let padding: CGFloat = 16
                    let cardWidth  = (geo.size.width  - padding * 2 - spacing * CGFloat(cols - 1)) / CGFloat(cols)
                    let cardHeight = (geo.size.height - padding * 2 - spacing * CGFloat(rows - 1)) / CGFloat(rows)
                    LazyVGrid(
                        columns: Array(repeating: GridItem(.fixed(cardWidth)), count: cols),
                        spacing: spacing
                    ) {
                        ForEach(viewModel.cards) { card in
                            CardView(card: card, shouldShowPie: viewModel.shouldShowPie)
                                .frame(width: cardWidth, height: cardHeight)
                                .onTapGesture {
                                    withAnimation(.linear(duration: 1)) {
                                        viewModel.choose(card: card)
                                        viewModel.getIfAllAreMatched()
                                    }
                                }
                        }
                    }
                    .padding(padding)
                }
                .foregroundStyle(Color.primaryColor)
            }

            // MODALS
            if viewModel.showPauseView {
                PauseModal(listener: viewModel)
            }

            if viewModel.timeRemaining == 0 {
                LoseModal(listener: viewModel)
            }

            if viewModel.showQuitView {
                QuitModal(listener: viewModel)
                    .onDisappear {
                        if viewModel.closeView {
                            dismiss()
                        } else {
                            viewModel.startTimer()
                        }
                    }
            }

            if viewModel.showWinView {
                WinModal(listener: viewModel)
                    .onAppear {
                        viewModel.stopTimer()
                    }
                    .onDisappear {
                        dismiss()
                    }
            }
        }
        .background(Color.grayBackground)
        .navigationBarHidden(true)
        .onAppear {
            viewModel.resetGame()
            viewModel.timeRemaining = viewModel.getRemainingTime()
            viewModel.startTimer()
        }
        .onDisappear {
            viewModel.stopTimer()
        }
    }
}

#Preview {
    MemorizeView(viewModel: MemorizeViewModel(memorama: nil))
}
