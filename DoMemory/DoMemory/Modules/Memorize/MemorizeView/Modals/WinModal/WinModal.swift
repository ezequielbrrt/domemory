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
            Blur()
                .frame(minWidth: 0, idealWidth: .infinity, maxWidth: .infinity, minHeight: 0, idealHeight: .infinity, maxHeight: .infinity, alignment: .center)
            VStack {
                Spacer()
                Text("😎").font(Font.system(size: 70))

                Text(Strings.youWin)
                    .foregroundColor(.secundaryColor)
                    .font(.patrickHand(size: 45))
                Text(Strings.youWinDescription)
                    .foregroundColor(.secundaryColor)
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
                            .cornerRadius(40)
                            .foregroundColor(.white)
                    }.padding()
                    Spacer()
                }.padding()
                Spacer()
            }.padding(EdgeInsets(top: 100, leading: 20, bottom: 100, trailing: 20))
        }
        
    }
}

struct WinModal_Previews: PreviewProvider {
    static var previews: some View {
        WinModal()
    }
}
