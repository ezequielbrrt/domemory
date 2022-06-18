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
        
            Blur()
                .frame(minWidth: 0, idealWidth: .infinity, maxWidth: .infinity, minHeight: 0, idealHeight: .infinity, maxHeight: .infinity, alignment: .center)
            VStack {
                Spacer()
                Text("🧐").font(Font.system(size: 70))
                Text(Strings.pause)
                    .foregroundColor(.secundaryColor)
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
                            .cornerRadius(40)
                            .foregroundColor(Color.white)
                    }.padding()
                    
                    
                    Button(action: {
                        listener?.tapOnResumeGame()
                    }) {
                        Text(Strings.continueGame)
                            .fontWeight(.bold)
                            .font(.righteous(size: 20))
                            .padding()
                            .background(Color.secundaryColor)
                            .cornerRadius(40)
                            .foregroundColor(.white)
                    }.padding()
                    
                }.padding()
                Spacer()
            }
        }
        
    }
}

struct PauseModal_Previews: PreviewProvider {
    static var previews: some View {
        PauseModal(listener: nil)
    }
}
