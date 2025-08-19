plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.app) apply false
    id("com.facebook.react.rootproject")
}
