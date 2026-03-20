//
//  WinModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI

struct WinModal: View {
    
    var listener: WinModalListener?

    var body: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()
            VStack {
                Spacer()
                Text("😎").font(Font.system(size: 70))

                Text(Strings.youWin)
                    .foregroundStyle(Color.secundaryColor)
                    .font(.patrickHand(size: 45))
                Text(Strings.youWinDescription)
                    .foregroundStyle(Color.secundaryColor)
                    .font(.patrickHand(size: 25))
                    .multilineTextAlignment(.center)

                HStack {
                    Spacer()
                    Button(action: {
                        listener?.tapOnContinue()
                    }) {
                        Text(Strings.goToMenu)
                            .fontWeight(.bold)
                            .font(.righteous(size: 20))
                            .padding()
                            .background(Color.secundaryColor)
                            .clipShape(RoundedRectangle(cornerRadius: 40))
                            .foregroundStyle(.white)
                    }.padding()
                    Spacer()
                }.padding()
                Spacer()
            }.padding(EdgeInsets(top: 100, leading: 20, bottom: 100, trailing: 20))
        }
        
    }
}

#Preview {
    WinModal()
}
