// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) version libs.versions.agp.get() apply false
    alias(libs.plugins.kotlin.android) version libs.versions.kotlin.get() apply false
}