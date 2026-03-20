//
//  PauseModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI


struct PauseModal: View {
    
    var listener: PauseModalListener?
    
    var body: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()
            VStack {
                Spacer()
                Text("🧐").font(Font.system(size: 70))
                Text(Strings.pause)
                    .foregroundStyle(Color.secundaryColor)
                    .font(.patrickHand(size: 65))

                VStack {
                    Button(action: {
                        listener?.tapOnReloadGame()
                    }) {
                        Text(Strings.tryAgain)
                            .fontWeight(.bold)
                            .font(.righteous(size: 17))
                            .padding()
                            .background(Color.red)
                            .clipShape(RoundedRectangle(cornerRadius: 40))
                            .foregroundStyle(.white)
                    }.padding()

                    Button(action: {
                        listener?.tapOnResumeGame()
                    }) {
                        Text(Strings.continueGame)
                            .fontWeight(.bold)
                            .font(.righteous(size: 20))
                            .padding()
                            .background(Color.secundaryColor)
                            .clipShape(RoundedRectangle(cornerRadius: 40))
                            .foregroundStyle(.white)
                    }.padding()
                }.padding()
                Spacer()
            }
        }
        
    }
}

#Preview {
    PauseModal(listener: nil)
}
