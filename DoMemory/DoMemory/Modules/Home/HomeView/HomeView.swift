//
//  HomeView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI

struct HomeView: View {
    @ObservedObject var showingView: ShowingView

    var viewModel: HomeViewModel
    init(showingView: ShowingView) {
        self.viewModel = HomeViewModel(showingView: showingView)
        self.showingView = showingView
    }
    
    var body: some View {
        ZStack {
            Color.grayBackground.ignoresSafeArea()
            VStack {
                
                Text("DoMemory")
                    .font(.righteous(size: 55))
                    .foregroundColor(.secundaryColor)
                    .padding()
                Spacer()
                
                ZStack {
                    Color.darkGrayColor.ignoresSafeArea()
                    VStack {
                        Spacer()
                        Text(Strings.askDifficulty)
                            .font(.patrickHand(size: 28))
                            .padding()
                        VStack {
                            Button(Strings.easy) {
                                viewModel.showMenuViewWithDifficulty(difficulty: .easy)
                            }.foregroundColor(.primaryColor)
                            Button(Strings.medium) {
                                viewModel.showMenuViewWithDifficulty(difficulty: .medium)
                            }.foregroundColor(.primaryColor)
                            Button(Strings.hard) {
                                viewModel.showMenuViewWithDifficulty(difficulty: .hard)
                            }.foregroundColor(.primaryColor)
                            Button(Strings.veryHard) {
                                viewModel.showMenuViewWithDifficulty(difficulty: .veryHard)
                            }.foregroundColor(.primaryColor)
                        }.font(.patrickHand(size: 24))
                        Spacer()
                        Spacer()
                    }
                }
                
            }
        }
    }
}

struct HomeView_Previews: PreviewProvider {
    static var previews: some View {
        HomeView(showingView: ShowingView(showingView: .mainAppView))
    }
}
