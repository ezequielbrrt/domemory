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
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea()
            VStack {
                Spacer()
                Text("🥺").font(Font.system(size: 70))

                Text(Strings.quit)
                    .foregroundStyle(Color.secundaryColor)
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
                            .clipShape(RoundedRectangle(cornerRadius: 40))
                            .foregroundStyle(.white)
                    }.padding()

                    Button(action: {
                        listener?.tapOnCancel()
                    }) {
                        Text(Strings.cancel)
                            .fontWeight(.bold)
                            .font(.righteous(size: 18))
                            .padding()
                            .background(Color.red)
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
    QuitModal()
}
