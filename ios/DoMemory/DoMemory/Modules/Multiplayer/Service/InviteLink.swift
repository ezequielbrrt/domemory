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

/// A request to open a season's level map: `domemory://season/<id>`, or
/// `https://<host>/season/<id>`. The App Store in-app event for a season opens
/// the app through this link.
///
/// The id is optional — `domemory://season` asks for whichever season is
/// active — and it is only ever matched against the *active* season, so a link
/// for a season that has ended, or one this device has never heard of, leaves
/// the player on the menu instead of opening something else.
struct SeasonDeepLink: Equatable {
    /// How long a request may wait for the season catalog. On a first launch
    /// there is no cached catalog and the active season arrives from the
    /// network a moment after the link does; past this window the link is
    /// dropped, so a season becoming active much later (say, at midnight) can
    /// never pull the player into it unasked.
    static let maxWait: TimeInterval = 30

    let seasonID: String?
    let receivedAt: Date

    enum Resolution: Equatable {
        case open(Season)
        /// No season is known yet; keep the request and try again when the
        /// catalog changes.
        case wait
        /// Expired, or it names a season that is not the active one.
        case drop
    }

    /// Nil unless `url` is a season link. Gathers the host (custom scheme only)
    /// plus the path, like `InviteLink.joinCode(from:)`, but `season` must be
    /// the *first* token: `domemory://join/season` is an invite to room
    /// `SEASON`, not a season link.
    static func parse(_ url: URL, receivedAt: Date = Date()) -> SeasonDeepLink? {
        var tokens: [String] = []
        if url.scheme == InviteLink.scheme, let host = url.host {
            tokens.append(host)
        }
        tokens.append(contentsOf: url.pathComponents.filter { $0 != "/" })

        guard tokens.first?.lowercased() == "season" else { return nil }
        let id = tokens.count > 1 ? tokens[1] : nil
        return SeasonDeepLink(seasonID: id?.isEmpty == false ? id : nil, receivedAt: receivedAt)
    }

    func resolve(activeSeason: Season?, now: Date = Date()) -> Resolution {
        guard now.timeIntervalSince(receivedAt) <= Self.maxWait else { return .drop }
        guard let activeSeason else { return .wait }
        guard seasonID == nil || seasonID == activeSeason.id else { return .drop }
        return .open(activeSeason)
    }
}

/// Holds a link's request until the menu is ready to route it: a join code for
/// the multiplayer room, today's Daily Challenge, or a season's level map.
@MainActor
final class DeepLinkRouter: ObservableObject {
    static let shared = DeepLinkRouter()

    @Published var pendingJoinCode: String?
    @Published var shouldOpenDailyChallenge = false
    @Published var pendingSeasonLink: SeasonDeepLink?

    private init() {}

    func handle(url: URL) {
        // Widget tap: domemory://daily (or https://<host>/daily)
        if url.host == "daily" || url.pathComponents.contains("daily") {
            shouldOpenDailyChallenge = true
            return
        }
        // In-app event: domemory://season/<id> (or https://<host>/season/<id>)
        if let seasonLink = SeasonDeepLink.parse(url) {
            pendingSeasonLink = seasonLink
            return
        }
        guard let code = InviteLink.joinCode(from: url) else { return }
        pendingJoinCode = code
        AnalyticsService.log(.multiplayerInviteOpened)
    }
}
