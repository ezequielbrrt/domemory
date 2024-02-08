//
//  MenuView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import WaterfallGrid

struct MenuView: View {
    @ObservedObject var viewModel = MenuViewModel()
    @State var showNewView = false
    @State var showBanner = false

    init() {
        UINavigationBar.appearance().largeTitleTextAttributes = [
            .foregroundColor: Color.secundaryColor.uiColor(), .font: UIFont.righteous(size: 35)]
                
        UINavigationBar.appearance().titleTextAttributes = [ .foregroundColor: Color.secundaryColor.uiColor(), .font: UIFont.righteous(size: 19)]
        
        UINavigationBar.appearance().backgroundColor = Color.grayBackground.uiColor()
        
    }
    
    var body: some View {
        Group {
            if viewModel.isLoading {
                LoadingView().padding()
            } else {
                NavigationView {
                    VStack {
                        NavigationLink(
                                    destination: SettingsView(listener: viewModel),
                                    isActive: $showNewView) {
                                    EmptyView()
                                }.isDetailLink(false)
                        
                        WaterfallGrid(viewModel.memoramaArray) { memorama in
                            NavigationLink(destination: MemorizeView(viewModel: MemorizeViewModel(memorama: memorama))
                                            .navigationBarTitle("")
                                            .navigationBarHidden(true)) {
                                MemoramaCard(memorama: memorama)
                            }.isDetailLink(true)
                        }
                        .padding()
                        
                        BannerH()
                
                        
                    }
                    .background(Color.darkGrayColor)
                    .navigationBarItems(leading:
                                            Button(action: {
                                                self.showNewView = true
                                            }, label: { Image("settings")
                                                .resizable()
                                                .frame(width: 25, height: 25, alignment: .center) }))
                    .navigationBarTitle(Text("DoMemory"))
                    
                }.navigationViewStyle(.stack)
            
            
            }
        }
    }
}

struct MemoramaCard: View {
    var memorama: Memorama
    
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(LinearGradient(
                    gradient: .init(colors: [Color.primaryColor, Color.secundaryColor]),
                    startPoint: .init(x: 0.5, y: 0),
                    endPoint: .init(x: 0.5, y: 0.6)
                  ))
                .frame(height: 95)

            VStack(alignment: .center) {
                Text(memorama.name)
                    .font(.patrickHand(size: 75))
            }
            
        }.padding()
    }   
}

struct MenuView_Previews: PreviewProvider {
    static var previews: some View {
        MenuView()
    }
}
