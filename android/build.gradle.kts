plugins {
    alias(libs.plugins.android.application) apply false
    // Same AGP artifact as the application plugin; declared here so the vendored :whatsnewkit
    // module (android/WhatsNewKit-Android) can apply it without a classpath version clash.
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
