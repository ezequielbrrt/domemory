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
                            .frame(width: 40, height: 40, alignment: .center).padding()
                            .gesture(TapGesture()
                                        .onEnded { _ in
                                            //self.presentation.wrappedValue.dismiss()
                                            self.viewModel.showQuitView.toggle()
                                            //viewModel.presentation?.wrappedValue.dismiss()
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
                        Spacer()
                        
                    }
                    
                    Grid(viewModel.cards) { card in
                            CardView(card: card).onTapGesture {
                                withAnimation(.linear(duration: 1)) {
                                    self.viewModel.choose(card: card)
                                }
                            }
                    .padding(5)
                        }
                        .padding()
                        .foregroundColor(Color.primaryColor)
                    HStack {
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
                            if viewModel.closeView {
                                self.presentation.wrappedValue.dismiss()
                            }
                    }
                }
                
            }
            .navigationBarTitle("", displayMode: .inline)
            .navigationBarHidden(true)
            .onAppear {
                self.viewModel.resetGame()
                self.viewModel.timeRemaining = self.viewModel.getRemainingTime()
            }
        }
        
    }
}


struct MemorizeView_Previews: PreviewProvider {
    static var previews: some View {
        MemorizeView(viewModel: MemorizeViewModel(memorama: nil))
    }
}
