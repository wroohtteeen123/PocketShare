import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val apkBuildTimestamp = SimpleDateFormat(
    "yyyyMMdd-HHmmss",
    Locale.US,
).format(Date())

android {
    namespace = "io.pocketshare"
    compileSdk = 35
    bundle { language { enableSplit = false } }

    defaultConfig {
        applicationId = "io.pocketshare"
        minSdk = 28
        targetSdk = 34
        versionCode = 24
        versionName = "1.3.6"
    }

    signingConfigs {
        create("production") {
            storeFile = rootProject.file("signing/pocketshare-release.p12")
            storePassword = System.getenv("POCKETSHARE_SIGNING_PASSWORD")
            keyAlias = "pocketshare"
            keyPassword = System.getenv("POCKETSHARE_SIGNING_PASSWORD")
            storeType = "PKCS12"
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            if (rootProject.file("signing/pocketshare-release.p12").isFile && !System.getenv("POCKETSHARE_SIGNING_PASSWORD").isNullOrBlank()) { signingConfig = signingConfigs.getByName("production") }
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("org.tukaani:xz:1.10")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("androidx.mediarouter:mediarouter:1.8.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.zxing:core:3.5.3")
}

val archiveTimestampedDebugApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.buildDirectory.dir("outputs/apk/timestamped"))
    rename { "PocketShare-debug-$apkBuildTimestamp.apk" }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(archiveTimestampedDebugApk)
}

val archiveTimestampedReleaseApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("app-release.apk", "app-release-unsigned.apk")
    }
    into(layout.buildDirectory.dir("outputs/apk/timestamped"))
    rename { name -> "PocketShare-release-${if (name.contains("unsigned")) "unsigned-" else ""}$apkBuildTimestamp.apk" }
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(archiveTimestampedReleaseApk)
}
