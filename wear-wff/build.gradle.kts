import java.util.Properties

// Resource-only Watch Face Format (WFF) watch face. It contains no code: all AAPS data
// comes from the complication providers of the :wear app (same signing key).
plugins {
    id("com.android.application")
}

android {
    namespace = "info.nightscout.androidaps.watchface"
    compileSdk = Versions.compileSdk

    defaultConfig {
        applicationId = "info.nightscout.androidaps.watchface"
        // WFF requires API 33+
        minSdk = 33
        targetSdk = 34
        versionCode = Versions.versionCode
        versionName = Versions.appVersion
    }

    signingConfigs {
        create("fullRelease") {
            // Same key as :app and :wear. Do not fail when credentials are missing,
            // this module is configured by every build (CI injects signing itself).
            val propertiesFile = File(System.getProperty("user.home") + File.separator + ".gradle" + File.separator + "gradle.properties")
            if (propertiesFile.exists()) {
                val properties = propertiesFile.inputStream().use { Properties().apply { load(it) } }
                val alias = properties.getProperty("AAPS_KEY_ALIAS")
                val storePass = properties.getProperty("AAPS_STORE_PASS")
                val keyPass = properties.getProperty("AAPS_KEY_PASS")
                val store = file("${projectDir.parentFile}${File.separator}keys${File.separator}peter_keys.jks")
                if (alias != null && storePass != null && keyPass != null && store.exists()) {
                    storeFile = store
                    storePassword = storePass
                    keyAlias = alias
                    keyPassword = keyPass
                }
            }
        }
    }

    buildTypes {
        debug {
            // Strips the generated R class so the APK contains no classes.dex
            isMinifyEnabled = true
        }
        release {
            isMinifyEnabled = true
            signingConfigs.getByName("fullRelease").takeIf { it.storeFile != null }?.let { signingConfig = it }
        }
    }

    buildFeatures {
        buildConfig = false
    }
}
