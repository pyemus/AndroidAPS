import org.jetbrains.kotlin.konan.properties.Properties
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.ksp)
    id("com.android.application")
    id("kotlin-android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("android-app-dependencies")
    id("test-app-dependencies")
    id("jacoco-app-dependencies")
}

repositories {
    mavenCentral()
    google()
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

fun generateGitRemote(): String {
    try {
        val processBuilder = ProcessBuilder("git", "remote", "get-url", "origin")
        val output = File.createTempFile("git-remote", "")
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

fun isMaster(): Boolean = !Versions.appVersion.contains("-")

fun gitAvailable(): Boolean {
    try {
        val processBuilder = ProcessBuilder("git", "--version")
        val output = File.createTempFile("git-version", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().isNotEmpty()
    } catch (_: Exception) {
        return false
    }
}

fun allCommitted(): Boolean {
    try {
        val processBuilder = ProcessBuilder("git", "status", "-s")
        val output = File.createTempFile("git-comited", "")
        processBuilder.redirectOutput(output)
        val process = processBuilder.start()
        process.waitFor()
        return output.readText().replace(Regex("""(?m)^\s*(M|A|D|\?\?)\s*.*?\.idea\/codeStyles\/.*?\s*$"""), "")
            // ignore all files added to project dir but not staged/known to GIT
            .replace(Regex("""(?m)^\s*(\?\?)\s*.*?\s*$"""), "").trim().isEmpty()
    } catch (_: Exception) {
        return false
    }
}

android {

    namespace = "app.aaps"
    ndkVersion = Versions.ndkVersion

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk

        buildConfigField("String", "VERSION", "\"$version\"")
        buildConfigField("String", "BUILDVERSION", "\"${generateGitBuild()}-${generateDate()}\"")
        buildConfigField("String", "REMOTE", "\"${generateGitRemote()}\"")
        buildConfigField("String", "HEAD", "\"${generateGitBuild()}\"")
        buildConfigField("String", "COMMITTED", "\"${allCommitted()}\"")

        // For Dagger injected instrumentation tests in app module
        testInstrumentationRunner = "app.aaps.runners.InjectedTestRunner"
    }

    flavorDimensions.add("standard")
    productFlavors {
        create("full") {
            isDefault = true
            applicationId = "info.nightscout.androidaps"
            dimension = "standard"
            resValue("string", "app_name", "AAPS")
            versionName = Versions.appVersion
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_launcher_round"
        }
        create("pumpcontrol") {
            applicationId = "info.nightscout.aapspumpcontrol"
            dimension = "standard"
            resValue("string", "app_name", "Pumpcontrol")
            versionName = Versions.appVersion + "-pumpcontrol"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_pumpcontrol"
            manifestPlaceholders["appIconRound"] = "@null"
        }
        create("aapsclient") {
            applicationId = "info.nightscout.aapsclient"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_yellowowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_yellowowl"
        }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient2")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_blueowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_blueowl"
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


    useLibrary("org.apache.http.legacy")

    //Deleting it causes a binding error
    buildFeatures {
        dataBinding = true
        buildConfig = true
    }
}

allprojects {
    repositories {
    }
}

dependencies {
    // in order to use internet"s versions you"d need to enable Jetifier again
    // https://github.com/nightscout/graphview.git
    // https://github.com/nightscout/iconify.git
    implementation(project(":shared:impl"))
    implementation(project(":core:data"))
    implementation(project(":core:objects"))
    implementation(project(":core:graph"))
    implementation(project(":core:graphview"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:libraries"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:validators"))
    implementation(project(":ui"))
    implementation(project(":plugins:aps"))
    implementation(project(":plugins:automation"))
    implementation(project(":plugins:configuration"))
    implementation(project(":plugins:constraints"))
    implementation(project(":plugins:insulin"))
    implementation(project(":plugins:main"))
    implementation(project(":plugins:sensitivity"))
    implementation(project(":plugins:smoothing"))
    implementation(project(":plugins:source"))
    implementation(project(":plugins:sync"))
    implementation(project(":implementation"))
    implementation(project(":database:impl"))
    implementation(project(":database:persistence"))
    implementation(project(":pump:combov2"))
    implementation(project(":pump:dana"))
    implementation(project(":pump:danars"))
    implementation(project(":pump:danar"))
    implementation(project(":pump:diaconn"))
    implementation(project(":pump:eopatch"))
    implementation(project(":pump:medtrum"))
    implementation(project(":pump:equil"))
    implementation(project(":pump:insight"))
    implementation(project(":pump:medtronic"))
    implementation(project(":pump:pump-common"))
    implementation(project(":pump:omnipod-common"))
    implementation(project(":pump:omnipod-eros"))
    implementation(project(":pump:omnipod-dash"))
    implementation(project(":pump:rileylink"))
    implementation(project(":pump:virtual"))
    implementation(project(":workflow"))

    testImplementation(project(":shared:tests"))
    androidTestImplementation(project(":shared:tests"))
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.org.skyscreamer.jsonassert)


    kspAndroidTest(libs.com.google.dagger.android.processor)

    /* Dagger2 - We are going to use dagger.android which includes
     * support for Activity and fragment injection so we need to include
     * the following dependencies */
    ksp(libs.com.google.dagger.android.processor)
    ksp(libs.com.google.dagger.compiler)

    // MainApp
    api(libs.com.uber.rxdogtag2.rxdogtag)
    // Remote config
    api(libs.com.google.firebase.config)
}

