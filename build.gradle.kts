// Applying plugins in the root project prevents resolution issues with subprojects
plugins {
    alias(libs.plugins.android.app) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.dokka.javadoc) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
