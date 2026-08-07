plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":core:score"))
    // Bravura's own metadata JSON is the source of truth for glyph metrics;
    // the module parses it rather than hardcoding a drifting copy.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
