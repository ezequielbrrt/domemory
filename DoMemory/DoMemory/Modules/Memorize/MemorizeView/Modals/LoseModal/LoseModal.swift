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
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()
            VStack {
                Spacer()
                Text("😳").font(Font.system(size: 70))

                Text(Strings.youLose)
                    .foregroundStyle(Color.secundaryColor)
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
    LoseModal()
}
