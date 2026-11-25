import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val fittyVersionCode = providers.gradleProperty("fittyVersionCode").map { value ->
    requireNotNull(value.toIntOrNull()?.takeIf { it > 0 }) {
        "fittyVersionCode must be a positive integer"
    }
}.getOrElse(8)

val fittyVersionName = providers.gradleProperty("fittyVersionName").map { value ->
    require(value.isNotBlank() && value == value.trim()) {
        "fittyVersionName must be non-blank and have no surrounding whitespace"
    }
    value
}.getOrElse("1.2.0")

android {
    namespace = "com.delaynore.fitty"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.delaynore.fitty"
        minSdk = 26
        targetSdk = 36
        versionCode = fittyVersionCode
        versionName = fittyVersionName
        testInstrumentationRunner = "com.delaynore.fitty.FittyTestRunner"
    }

    buildTypes {
        debug {
            resValue("string", "app_name", "Fitty")
            buildConfigField("boolean", "PRERELEASE_UPDATES", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            resValue("string", "app_name", "Fitty")
            buildConfigField("boolean", "PRERELEASE_UPDATES", "false")
        }
        create("prerelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".prerelease"
            matchingFallbacks += listOf("release")
            resValue("string", "app_name", "Fitty Test")
            buildConfigField("boolean", "PRERELEASE_UPDATES", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    sourceSets["androidTest"].assets.srcDir(layout.projectDirectory.dir("schemas"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(composeBom)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.json)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    javaLauncher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
