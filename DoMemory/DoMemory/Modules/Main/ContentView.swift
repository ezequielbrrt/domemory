//
//  ContentView.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI
import Firebase

class ShowingView: ObservableObject {
    init(showingView: AppModules) {
        self.viewId = showingView
    }
    @Published var viewId : AppModules
}

struct ContentView: View {
    @ObservedObject var showingView: ShowingView
    
    init(showingView: ShowingView) {
        self.showingView = showingView
        //FirebaseApp.configure()
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { (allowed, error) in
             //This callback does not trigger on main loop be careful
            if allowed {
              print("allowed")
            } else {
              print("error")
            }
        }
        
        if UserManageObject().getUserSettings() != nil{
            showingView.viewId = .menuView
        }
    }
    
    var body: some View {
        Group {
            if showingView.viewId == .mainAppView  {
                HomeView(showingView: showingView)
            }
            if showingView.viewId == .menuView {
                MenuView()
            }
        }
        
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView(showingView: ShowingView(showingView: .mainAppView))
    }
}
 
