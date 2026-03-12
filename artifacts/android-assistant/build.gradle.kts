// build.gradle.kts (root)
// Root-level build file. Defines which plugins are available to sub-projects.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
