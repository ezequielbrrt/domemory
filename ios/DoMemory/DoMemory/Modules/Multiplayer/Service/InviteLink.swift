//
//  InviteLink.swift
//  DoMemory
//

import Foundation

/// Builds and parses multiplayer "challenge a friend" invite links.
///
/// Two link forms are supported:
/// - **Custom scheme** (`domemory://join/CODE`) — works today for users who
///   already have the app installed; no server setup required.
/// - **Universal link** (`https://<host>/join/CODE`) — the install-then-route
///   flow for *new* users. Requires hosting an `apple-app-site-association`
///   file on `universalHost` and adding the `associated-domains` entitlement.
///   The parser already accepts it, so enabling it later needs no code change.
enum InviteLink {
    static let scheme = "domemory"
    /// Configure AASA on this host (or change it to your domain) to enable the install flow.
    static let universalHost = "domemory.app"
    static let appStoreID = "1533115091"

    static var appStoreURL: URL {
        URL(string: "https://apps.apple.com/app/id\(appStoreID)")!
    }

    static func universalURL(code: String) -> URL {
        URL(string: "https://\(universalHost)/join/\(code)")!
    }

    static func schemeURL(code: String) -> URL {
        URL(string: "\(scheme)://join/\(code)")!
    }

    /// Extracts a 6-character room code from a custom-scheme link, a universal
    /// link, or a `?code=` query parameter. Returns nil if no valid code found.
    static func joinCode(from url: URL) -> String? {
        var tokens: [String] = []
        if url.scheme == scheme, let host = url.host {
            tokens.append(host)
        }
        tokens.append(contentsOf: url.pathComponents.filter { $0 != "/" })

        var candidate: String?
        if let joinIndex = tokens.firstIndex(where: { $0.lowercased() == "join" }), joinIndex + 1 < tokens.count {
            candidate = tokens[joinIndex + 1]
        }
        if candidate == nil {
            candidate = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                .queryItems?
                .first(where: { $0.name == "code" })?
                .value
        }

        guard let raw = candidate else { return nil }
        let code = raw.uppercased().filter { $0.isLetter || $0.isNumber }
        return code.count == 6 ? code : nil
    }

    /// Composes the share text: a caption, the invite link for installed users,
    /// the room code, and the App Store link so new users can install first.
    static func shareMessage(code: String) -> String {
        let caption = String(
            format: NSLocalizedString("multiplayer_invite_message", comment: "Invite caption, %@ = room code"),
            code
        )
        return "\(caption)\n\(schemeURL(code: code).absoluteString)\n\(appStoreURL.absoluteString)"
    }
}

/// Holds a join code captured from an invite link until the menu is ready to
/// route to the multiplayer room.
@MainActor
final class DeepLinkRouter: ObservableObject {
    static let shared = DeepLinkRouter()

    @Published var pendingJoinCode: String?
    @Published var shouldOpenDailyChallenge = false

    private init() {}

    func handle(url: URL) {
        // Widget tap: domemory://daily (or https://<host>/daily)
        if url.host == "daily" || url.pathComponents.contains("daily") {
            shouldOpenDailyChallenge = true
            return
        }
        guard let code = InviteLink.joinCode(from: url) else { return }
        pendingJoinCode = code
        AnalyticsService.log(.multiplayerInviteOpened)
    }
}
