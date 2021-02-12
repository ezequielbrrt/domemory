//
//  QuitModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI

struct QuitModal: View {
    
    var listener: QuitModalListener?

    var body: some View {
        ZStack {
            Blur()
                .frame(minWidth: 0, idealWidth: .infinity, maxWidth: .infinity, minHeight: 0, idealHeight: .infinity, maxHeight: .infinity, alignment: .center)
            VStack {
                Spacer()
                Text(Strings.quit)
                    .foregroundColor(.secundaryColor)
                    .font(.patrickHand(size: 25))
                    .padding()

                HStack {
                    Button(action: {
                        listener?.tapOnExit()
                    }) {
                        Text(Strings.accept)
                            .fontWeight(.bold)
                            .font(.righteous(size: 18))
                            .padding()
                            .background(Color.secundaryColor)
                            .cornerRadius(40)
                            .foregroundColor(.white)
                    }.padding()
                    
                    Button(action: {
                        listener?.tapOnCancel()
                    }) {
                        Text(Strings.cancel)
                            .fontWeight(.bold)
                            .font(.righteous(size: 18))
                            .padding()
                            .background(Color.red)
                            .cornerRadius(40)
                            .foregroundColor(.white)
                            
                    }.padding()
                }.padding()
                Spacer()
            }
        }
    }
}

struct QuitModal_Previews: PreviewProvider {
    static var previews: some View {
        QuitModal()
    }
}
