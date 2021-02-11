//
//  SettingsView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 24/09/20.
//

import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: SettingsViewModel
    
    @State private var isToggle : Bool = false
    @State private var selectedStrength: Int = 0
    
    let impactMed = UIImpactFeedbackGenerator(style: .medium)
        
    
    init(listener: SettingsListener?) {
        self.viewModel = SettingsViewModel(listener: listener)

    }

    var body: some View {
        ZStack {
            Color.grayBackground.ignoresSafeArea()
            VStack {
                
               Spacer()
                Text(Strings.askDifficulty).font(.patrickHand(size: 35)).padding()
                VStack {
                    Button(Strings.easy) {
                        impactMed.impactOccurred()
                        viewModel.saveDifficulty(difficultyIndex: 0)
                    }.foregroundColor((viewModel.difficulty == 0) ? .secundaryColor : .primaryColor)
                    Button(Strings.medium) {
                        impactMed.impactOccurred()
                        viewModel.saveDifficulty(difficultyIndex: 1)
                    }.foregroundColor((viewModel.difficulty == 1) ? .secundaryColor : .primaryColor)
                    Button(Strings.hard) {
                        impactMed.impactOccurred()
                        viewModel.saveDifficulty(difficultyIndex: 2)
                    }.foregroundColor((viewModel.difficulty == 2) ? .secundaryColor : .primaryColor)
                    Button(Strings.veryHard) {
                        impactMed.impactOccurred()
                        viewModel.saveDifficulty(difficultyIndex: 3)
                    }.foregroundColor((viewModel.difficulty == 3) ? .secundaryColor : .primaryColor)
                }.font(.patrickHand(size: 35))
                Spacer()
                Spacer()
            }
        }.onAppear {
            self.viewModel.difficulty = self.viewModel.getCurrentDifficulty()
        }.onDisappear {
            self.viewModel.viewWillDissapear()
        }
        
//        VStack {
//            Text(Strings.difficulty)
//                .foregroundColor(.primary)
//                .font(.righteous(size: 25))
//
//            Picker(selection: $selectedStrength, label: Text("Strength").foregroundColor(.white)) {
//                ForEach(Difficulty.allCases) { difficulty in
//                    Text(difficulty.rawValue)
//                }
//            }.onChange(of: selectedStrength) {
//                viewModel.saveDifficulty(difficultyIndex: $0)
//            }
//        }.onAppear {
//            self.selectedStrength = self.viewModel.getCurrentDifficulty()
//        }.onDisappear {
//            viewModel.viewWillDissapear()
//        }
    }
}

struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView(listener: nil)
    }
}

