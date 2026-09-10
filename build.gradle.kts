import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "com.akbigchris.copyjob"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs)
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.akbigchris.copyjob.MainKt"

        nativeDistributions {
            // Windows is the primary target; other formats are included for convenience.
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "CopyJob"
            packageVersion = "1.0.0"
        }
    }
}
