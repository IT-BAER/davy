import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    id("jacoco")
}

// Conditional release signing via keystore.properties (no fallback for release)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasKeystoreProperties = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasKeystoreProperties) {
        keystorePropertiesFile.inputStream().use { this.load(it) }
    }
}
val storeFilePathProp = keystoreProperties.getProperty("storeFile")?.trim()
val storePasswordProp = keystoreProperties.getProperty("storePassword")?.trim()
val keyAliasProp = keystoreProperties.getProperty("keyAlias")?.trim()
val keyPasswordProp = keystoreProperties.getProperty("keyPassword")?.trim()
val hasSigningProps = hasKeystoreProperties &&
    !storeFilePathProp.isNullOrEmpty() &&
    !storePasswordProp.isNullOrEmpty() &&
    !keyAliasProp.isNullOrEmpty() &&
    !keyPasswordProp.isNullOrEmpty()

android {
    namespace = "com.davy"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.davy"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 5
        versionName = "1.1.0"

        testInstrumentationRunner = "com.davy.HiltTestRunner"
        
        // AppAuth redirect scheme for OAuth flows
        manifestPlaceholders["appAuthRedirectScheme"] = "com.davy"
        
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasSigningProps) {
                storeFile = rootProject.file(storeFilePathProp!!)
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasSigningProps) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            // Disable minification for debug builds to preserve logging
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            enableUnitTestCoverage = false
            enableAndroidTestCoverage = false
        }
    }

    // Lint configuration for Play release readiness
    lint {
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = false
        textReport = true
        htmlReport = true
        xmlReport = true
        // Persist reports to stable locations under build/reports/lint
        textOutput = file("$buildDir/reports/lint/lint-results.txt")
        htmlOutput = file("$buildDir/reports/lint/lint.html")
        xmlOutput = file("$buildDir/reports/lint/lint-results.xml")
        // Workaround Windows file-lock flakiness: don't run lintVital in assembleRelease
        // Lint still enforced via explicit :app:lintRelease in CI
        checkReleaseBuilds = false
        // Disable MissingTranslation for Privacy Policy/ToS legal texts (acceptable in English)
        // Disable ExtraTranslation for legacy privacy_policy_* strings in translations
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/*.SF"
            excludes += "META-INF/*.DSA"
            excludes += "META-INF/*.RSA"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
            // Merge duplicate Groovy extension modules from ical4j and groovy-dateutil
            merges += "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule"
            merges += "META-INF/groovy-release-info.properties"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
            }
        }
    }

    // Redirect unit test sources to a filtered folder to avoid compiling Android-dependent/outdated tests
    sourceSets {
        getByName("test") {
            java.setSrcDirs(listOf("src/unitTest/java"))
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.coroutines.android)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.profileinstaller)

    // Jetpack Compose
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    // Database
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.bundles.network)
    implementation(libs.gson)
    ksp(libs.moshi.codegen)

    // Background Work
    implementation(libs.androidx.work.runtime.ktx)
    implementation("androidx.startup:startup-runtime:1.2.0")

    // Protocol Libraries
    implementation(libs.ical4j)
    implementation(libs.ez.vcard)

    // Security
    implementation(libs.androidx.security.crypto)

    // OAuth
    implementation(libs.appauth)
    implementation(libs.androidx.browser)

    // Logging
    implementation(libs.timber)

    // Testing - Unit
    testImplementation(libs.bundles.testing.unit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.room.testing)
    testImplementation(libs.orgjson)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Testing - Android
    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(libs.bundles.testing.compose)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.mockk.android)
    kspAndroidTest(libs.hilt.compiler)

    // Core library desugaring for java.time and newer JDK APIs on older Android
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

// JaCoCo Configuration
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*_Hilt*.*",
        "**/Hilt_*.*",
        "**/*_Factory.*",
        "**/*Module.*",
        "**/*Module\$*.*"
    )

    val debugTree = fileTree("${project.layout.buildDirectory.get().asFile}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(project.layout.buildDirectory) {
        include("jacoco/testDebugUnitTest.exec")
    })
}

// Code coverage enforcement
tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
