package com.ezequielbrrt.domemory.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The `AppWidgetProvider` Glance generates a `BroadcastReceiver` shape for. Registered in
 * `AndroidManifest.xml` against `res/xml/daily_challenge_widget_info.xml`, which is where
 * the actual size/preview/update-policy metadata lives (a Glance widget still needs a real
 * `AppWidgetProviderInfo` — Glance replaces the *content* API, not the OS registration).
 */
class DailyChallengeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyChallengeGlanceWidget()
}
