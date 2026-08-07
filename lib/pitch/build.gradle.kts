// Deliberately independent of :core:score — this is a standalone, reusable
// monophonic pitch-detection library that speaks in hertz, not in notation.
// The hertz-to-notated-pitch conversion is the adapter's job, not the DSP's.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":lib:common"))

    testImplementation(libs.junit)
}
