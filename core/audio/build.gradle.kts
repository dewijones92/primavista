plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dewijones92.primavista.audio"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    // The adapter module where audio hardware meets practice: it supplies the
    // Conductor's clock, the metronome, and the mic AnswerSource.
    api(project(":core:practice"))
    api(project(":lib:pitch"))
    implementation(libs.kotlinx.coroutines.core)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
