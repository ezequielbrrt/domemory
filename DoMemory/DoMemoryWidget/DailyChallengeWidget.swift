//
//  DailyChallengeWidget.swift
//  DoMemoryWidget
//
//  Add this file to the DoMemoryWidget extension target. It also needs
//  DailyChallengeShared.swift (shared with the app target) for the snapshot.
//

import WidgetKit
import SwiftUI

struct DailyChallengeEntry: TimelineEntry {
    let date: Date
    let streak: Int
    let isCompleted: Bool
}

struct DailyChallengeProvider: TimelineProvider {
    func placeholder(in context: Context) -> DailyChallengeEntry {
        DailyChallengeEntry(date: Date(), streak: 5, isCompleted: false)
    }

    func getSnapshot(in context: Context, completion: @escaping (DailyChallengeEntry) -> Void) {
        completion(currentEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<DailyChallengeEntry>) -> Void) {
        // Refresh at the next midnight so the "completed" lock resets for the new day.
        let nextMidnight = Calendar.current.nextDate(
            after: Date(),
            matching: DateComponents(hour: 0, minute: 0, second: 0),
            matchingPolicy: .nextTime
        ) ?? Date().addingTimeInterval(60 * 60)
        completion(Timeline(entries: [currentEntry()], policy: .after(nextMidnight)))
    }

    private func currentEntry() -> DailyChallengeEntry {
        let snapshot = DailyChallengeSnapshot.current()
        return DailyChallengeEntry(date: Date(), streak: snapshot.currentStreak, isCompleted: snapshot.isCompletedToday)
    }
}

struct DailyChallengeWidgetView: View {
    var entry: DailyChallengeEntry

    // Tweak to match the app's primary brand color.
    private let brand = Color(red: 0.45, green: 0.30, blue: 0.95)

    private var title: String {
        NSLocalizedString("daily_challenge_title", value: "Daily Challenge", comment: "")
    }

    private var subtitle: String {
        entry.isCompleted
            ? NSLocalizedString("daily_challenge_completed", value: "Done for today!", comment: "")
            : NSLocalizedString("daily_challenge_subtitle", value: "New board every day", comment: "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: entry.isCompleted ? "checkmark.circle.fill" : "calendar")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(brand)
                if entry.streak > 0 {
                    Text("🔥 \(entry.streak)")
                        .font(.system(size: 16, weight: .heavy, design: .rounded))
                        .foregroundStyle(.primary)
                }
                Spacer()
            }

            Spacer(minLength: 0)

            Text(title)
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)

            Text(subtitle)
                .font(.system(size: 12, weight: .semibold, design: .rounded))
                .foregroundStyle(.secondary)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .widgetURL(URL(string: "domemory://daily"))
    }
}

struct DailyChallengeWidget: Widget {
    // Must match WidgetReloader.dailyChallengeKind in the app target.
    let kind = "DoMemoryDailyWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: DailyChallengeProvider()) { entry in
            DailyChallengeWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName(NSLocalizedString("daily_challenge_title", value: "Daily Challenge", comment: ""))
        .description(NSLocalizedString("daily_challenge_subtitle", value: "New board every day", comment: ""))
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
