import org.jetbrains.kotlin.konan.properties.Properties
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.ksp)
    id("com.android.application")
    id("kotlin-android")
    id("android-app-dependencies")
    id("test-app-dependencies")
    id("jacoco-app-dependencies")
}

repositories {
    google()
    mavenCentral()
}

fun generateGitBuild(): String {
    try {
        val processBuilder = ProcessBuilder("git", "describe", "--always")
        val output = File.createTempFile("git-build", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().trim()
    } catch (_: Exception) {
        return "NoGitSystemAvailable"
    }
}

fun generateDate(): String {
    val stringBuilder: StringBuilder = StringBuilder()
    // showing only date prevents app to rebuild everytime
    stringBuilder.append(SimpleDateFormat("yyyy.MM.dd").format(Date()))
    return stringBuilder.toString()
}


android {
    namespace = "app.aaps.wear"

    defaultConfig {
        minSdk = Versions.wearMinSdk
        targetSdk = Versions.wearTargetSdk

        buildConfigField("String", "BUILDVERSION", "\"${generateGitBuild()}-${generateDate()}\"")
    }

    android {
        buildTypes {
            debug {
                enableUnitTestCoverage = true
                // Disable androidTest coverage, since it performs offline coverage
                // instrumentation and that causes online (JavaAgent) instrumentation
                // to fail in this project.
                enableAndroidTestCoverage = false
            }
        }
    }

    flavorDimensions.add("standard")
    productFlavors {
        create("full") {
            isDefault = true
            applicationId = "info.nightscout.androidaps"
            dimension = "standard"
            versionName = Versions.appVersion
        }
        create("pumpcontrol") {
            applicationId = "info.nightscout.aapspumpcontrol"
            dimension = "standard"
            versionName = Versions.appVersion + "-pumpcontrol"
        }
        create("aapsclient") {
            applicationId = "info.nightscout.aapsclient"
            dimension = "standard"
            versionName = Versions.appVersion + "-aapsclient"
        }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = "standard"
            versionName = Versions.appVersion + "-aapsclient2"
        }
    }

    signingConfigs {
        create("fullRelease") {
            try {
                val propertiesFile = File(System.getProperty("user.home") + File.separator + ".gradle" + File.separator + "gradle.properties")
                val properties = propertiesFile.inputStream().use {
                    Properties().apply { load(it) }
                }
                val propKeyAlias = properties.getValue("AAPS_KEY_ALIAS") as String
                val propStorePassword = properties.getValue("AAPS_STORE_PASS") as String
                val propKeyPassword = properties.getValue("AAPS_KEY_PASS") as String
                storeFile = file("$projectDir${File.separator}keys${File.separator}peter_keys.jks")
                storePassword = "$propStorePassword"
                keyAlias = "$propKeyAlias"
                keyPassword = "$propKeyPassword"
            } catch (e: Exception) {
                if (System.getenv("STORE_PASS")==null) {
                    throw GradleException("Can't find credentials")
                }
                storeFile = file("keystore.jks")
                storePassword = System.getenv("STORE_PASS")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASS")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("fullRelease")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

allprojects {
    repositories {
    }
}


dependencies {
    implementation(project(":shared:impl"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.legacy.support)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.constraintlayout)

    testImplementation(project(":shared:tests"))

    compileOnly(libs.com.google.android.wearable)
    implementation(libs.com.google.android.wearable.support)
    implementation(libs.com.google.android.gms.playservices.wearable)
    implementation(files("${rootDir}/wear/libs/ustwo-clockwise-debug.aar"))
    implementation(files("${rootDir}/wear/libs/wearpreferenceactivity-0.5.0.aar"))
    implementation(files("${rootDir}/wear/libs/hellocharts-library-1.5.8.aar"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlin.stdlib.jdk8)

    ksp(libs.com.google.dagger.android.processor)
    ksp(libs.com.google.dagger.compiler)
}
