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
                Text(Strings.pause)
                    .foregroundColor(.secundaryColor)
                    .font(.patrickHand(size: 65))

                VStack {
                    Image("repetir")
                        .resizable()
                        .frame(width: 45, height: 45, alignment: .center)
                        .padding()
                        .gesture(TapGesture()
                                    .onEnded { _ in
                                        listener?.tapOnReloadGame()
                                    }
                                )
                    Image("play-fill")
                        .resizable()
                        .frame(width: 65, height: 65, alignment: .center)
                        .gesture(TapGesture()
                                    .onEnded { _ in
                                        listener?.tapOnResumeGame()
                                    }
                                )
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
