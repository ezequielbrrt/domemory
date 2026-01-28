//
//  MemorizeView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI

struct MemorizeView: View {
    @State private var showSheet = false
    @State private var showLoseView = false
    @Environment(\.presentationMode) var presentation
    @ObservedObject var viewModel: MemorizeViewModel
    
    init(viewModel: MemorizeViewModel) {
        self.viewModel = viewModel
    }
    
    var body: some View {
        NavigationView {
            ZStack {
                VStack {
                    HStack {
                        Image("error")
                            .resizable()
                            .frame(width: 40, height: 40, alignment: .center)
                            .padding()
                            .gesture(TapGesture()
                                        .onEnded { _ in
                                            self.viewModel.showQuitView.toggle()
                                            self.viewModel.timer.upstream.connect().cancel()
                                        }
                                    )
                            
                        Spacer()
                        Text(Strings.time + " : \(self.viewModel.timeRemaining)")
                            .font(.patrickHand(size: 25))
                            .foregroundColor(.primaryColor)
                            .onReceive(self.viewModel.timer) { _ in
                                    if self.viewModel.timeRemaining > 0 {
                                        self.viewModel.timeRemaining -= 1
                                    }
                                }
                        Spacer()
                        Image("cronografo")
                            .resizable()
                            .frame(width: 45, height: 45, alignment: .center).padding()
                            .gesture(TapGesture()
                                        .onEnded { _ in
                                            self.viewModel.timer.upstream.connect().cancel()
                                            viewModel.showPauseView.toggle()
                                        }
                                    )
                        
                    }
                    
                    Grid(viewModel.cards) { card in
                        CardView(card: card, shouldShowPie: viewModel.shouldShowPie).onTapGesture {
                                withAnimation(.linear(duration: 1)) {
                                    self.viewModel.choose(card: card)
                                    self.viewModel.getIfAllAreMatched()
                                }
                            }.padding(5)
                        }
                        .padding()
                        .foregroundColor(Color.primaryColor)
                }
                
                
                // MODALS
                if viewModel.showPauseView {
                    PauseModal(listener: self.viewModel)
                }
                
                if viewModel.timeRemaining == 0 {
                    LoseModal(listener: self.viewModel)
                }
                
                if viewModel.showQuitView {
                    QuitModal(listener: self.viewModel)
                        .onDisappear {
                            viewModel.closeView ? self.presentation.wrappedValue.dismiss() : self.viewModel.reconnectTime()
                    }
                }
                
                if viewModel.showWinView {
                    WinModal(listener: self.viewModel)
                        .onAppear {
                            self.viewModel.timer.upstream.connect().cancel()
                        }
                        .onDisappear {
                            self.presentation.wrappedValue.dismiss()
                        }
                }
                
            }
            .background(Color.grayBackground)

            .navigationBarTitle("", displayMode: .inline)
            .navigationBarHidden(true)
            .onAppear {
                self.viewModel.resetGame()
                self.viewModel.timeRemaining = self.viewModel.getRemainingTime()
            }
        }.navigationViewStyle(.stack)
        
    }
}


struct MemorizeView_Previews: PreviewProvider {
    static var previews: some View {
        MemorizeView(viewModel: MemorizeViewModel(memorama: nil))
    }
}
