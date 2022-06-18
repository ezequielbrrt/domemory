//
//  LoseModal.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 19/10/20.
//

import SwiftUI

struct LoseModal: View {
    var listener: LoseModalViewModelListener?
    
    var body: some View {
        ZStack {
            Blur()
                .frame(minWidth: 0, idealWidth: .infinity, maxWidth: .infinity, minHeight: 0, idealHeight: .infinity, maxHeight: .infinity, alignment: .center)
            VStack {
                Spacer()
                Text("😳").font(Font.system(size: 70))

                Text(Strings.youLose)
                    .foregroundColor(.secundaryColor)
                    .font(.patrickHand(size: 45))
                
                HStack {
                    Spacer()
                    
                    Button(action: {
                        listener?.tapOnTryAgain()
                    }) {
                        Text(Strings.tryAgain)
                            .fontWeight(.bold)
                            .font(.righteous(size: 18))
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

struct LoseModal_Previews: PreviewProvider {
    static var previews: some View {
        LoseModal()
    }
}
