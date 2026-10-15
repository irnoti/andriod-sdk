plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "ir.irnoti.sdk"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.firebase:firebase-messaging:24.0.0")
}

// JitPack: com.github.irnoti:andriod-sdk:<tag>
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.irnoti"
                artifactId = "andriod-sdk"
                version = project.findProperty("version")?.toString()?.takeIf { it != "unspecified" } ?: "1.1.0"
            }
        }
    }
}
