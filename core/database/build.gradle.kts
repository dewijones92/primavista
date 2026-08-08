plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

val schemaFolder = "schemas"
val schemaDirectory = layout.projectDirectory.dir(schemaFolder).asFile.path

android {
    namespace = "com.dewijones92.primavista.database"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

// Schemas are committed, not generated-and-forgotten: a schema change has to show up as a diff
// in review, because the migration that carries Dewi's practice history forward is written
// against it (docs/spec.md I4).
ksp {
    arg("room.schemaLocation", schemaDirectory)
}

// The instrumented migration test builds the OLD database from its exported schema, so the
// schema folder has to be readable on-device. `android.sourceSets["androidTest"].assets` is the
// AGP 8 spelling and throws a ClassCastException on the AGP 9 decorated source set; the variant
// API below is the supported route, and it also survives androidTest being renamed to a device
// test. See .claude/CODE-NOTES.md.
androidComponents {
    onVariants { variant ->
        variant.deviceTests.values.forEach { deviceTest ->
            deviceTest.sources.assets?.addStaticSourceDirectory(schemaFolder)
        }
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    // The only place Room entities meet domain types.
    api(project(":core:practice"))
    // api: PrimaVistaDatabase extends RoomDatabase, so Room is part of this module's ABI.
    api(libs.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
