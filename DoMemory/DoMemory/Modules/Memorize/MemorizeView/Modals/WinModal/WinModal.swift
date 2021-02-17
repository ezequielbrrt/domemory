//
//  WinModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI

struct WinModal: View {
    
    var body: some View {
        ZStack {
            Blur()
                .frame(minWidth: 0, idealWidth: .infinity, maxWidth: .infinity, minHeight: 0, idealHeight: .infinity, maxHeight: .infinity, alignment: .center)
            VStack {
                Spacer()
                Text(Strings.youLose)
                    .foregroundColor(.secundaryColor)
                    .font(.patrickHand(size: 45))
                
                HStack {
                    Spacer()
    
                    Image("repetir")
                        .resizable()
                        .frame(width: 60, height: 60, alignment: .center)
                        .padding()
                        .gesture(TapGesture()
                                    .onEnded { _ in
                                        //listener?.tapOnTryAgain()
                                    }
                                )
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
