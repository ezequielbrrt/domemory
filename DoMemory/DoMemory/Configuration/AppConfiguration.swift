//
//  AppConfiguration.swift
//  DoMemory
//
//  Created by Ezequiel Barreto on 23/09/20.
//

import SwiftUI

class AppConfiguration: NSObject {
    static var unitGame = "ca-app-pub-4297174845441653/5761017394"
    static var unitHome = "ca-app-pub-4297174845441653/2000067481"
}


// MARK:- FONTS
extension UIFont {
    class func righteous(size: CGFloat) -> UIFont {
        return UIFont(name: "Righteous-Regular", size: size)!
    }
    
    class func patrickHand(size: CGFloat) -> UIFont {
        return UIFont(name: "PatrickHand-Regular", size: size)!
    }
}

extension Font {
    static func righteous(size: CGFloat) -> Font {
        return .custom("Righteous-Regular", size: size)
    }
    
    static func patrickHand(size: CGFloat) -> Font {
        return .custom("PatrickHand-Regular", size: size)
    }
}

// MARK:- COLORS
extension Color {
    static var primaryColor: Color { return Color.make(162, 204, 79) }
    static var secundaryColor: Color { return Color.make(39, 104, 228) }
    static var grayBackground: Color { return Color.make(42, 42, 42) }
    static var darkGrayColor: Color { return Color.make(32, 32, 32) }
    
    static func make( _ red: Int, _ green: Int, _ blue: Int) -> Color {
        return Color(red: Double(red)/255.0,
                       green: Double(green)/255.0,
                       blue: Double(blue)/255.0)
    }
}
