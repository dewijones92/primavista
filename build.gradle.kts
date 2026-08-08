import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
}

val detektFormatting = libs.detekt.formatting

// Ratchet upwards as coverage grows; never downwards without a recorded reason.
val MIN_LOGIC_MODULE_COVERAGE_PERCENT = 75

// One lint policy for every Android module, current and future.
val lintPolicy: Lint.() -> Unit = {
    warningsAsErrors = true
    abortOnError = true
    // Version-freshness nags break CI on every upstream release, not on code changes.
    disable += listOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
}

// One set of Android build defaults; modules declare only what is theirs
// (namespace, dependencies, features).
val projectCompileSdk = libs.versions.compileSdk.get().toInt()
val projectMinSdk = libs.versions.minSdk.get().toInt()

val androidDefaults: com.android.build.api.dsl.CommonExtension.() -> Unit = {
    compileSdk = projectCompileSdk
    defaultConfig.minSdk = projectMinSdk
    compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    compileOptions.targetCompatibility = JavaVersion.VERSION_17
    lint.lintPolicy()
}

// Every module gets the same static-analysis gate; adding a module adds its gate.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    // Shift left (CLAUDE.md): the compiler is the cheapest place to catch anything, so it is
    // configured to stop rather than to advise. Set here rather than per module, so a new module
    // cannot be strict-by-forgetting-to-opt-out.
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            // A warning is the compiler saying it found something and decided not to stop you.
            // Nobody triages a warning list on a one-person app, so it has to stop you.
            allWarningsAsErrors.set(true)
            // Trust JSR-305 nullability annotations on Java interop instead of treating those
            // types as platform types the compiler cannot check.
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom("$rootDir/config/detekt/detekt.yml")
        source.setFrom(
            "src/main/java",
            "src/main/kotlin",
            "src/test/java",
            "src/test/kotlin",
            "src/androidTest/java",
            "src/androidTest/kotlin",
        )
        parallel = true
        autoCorrect = true
    }

    dependencies {
        add("detektPlugins", detektFormatting)
    }

    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> { androidDefaults() }
    }
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> { androidDefaults() }
    }

    // Coverage gate on logic modules; :app is report-only (Compose UI distorts numbers).
    // Adapter modules are exempt: thin bridges to on-device machinery (Room, AudioTrack,
    // AudioRecord) verified by instrumented tests, whose coverage the JVM gate cannot see.
    val koverExemptAdapters = setOf(":core:database", ":core:audio")
    if ((path.startsWith(":core") || path.startsWith(":lib")) && path !in koverExemptAdapters) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
        extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
            reports {
                verify {
                    rule {
                        minBound(MIN_LOGIC_MODULE_COVERAGE_PERCENT)
                    }
                }
            }
        }
    }
}
