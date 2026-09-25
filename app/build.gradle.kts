plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.navigation.safe.args)
}

android {
    namespace = "com.jdcosmetics.stockcollect"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jdcosmetics.stockcollect"
        minSdk = 26          // Android 8.0 Oreo
        targetSdk = 34
        versionCode = 2         // > V1 (branche v1-original) : Android refuse de reinstaller la V1 par-dessus
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // MigrationTestHelper lit les schémas exportés depuis les assets du test instrumenté.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)

    // Lifecycle + ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Room (SQLite)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Hilt (Injection de dépendances)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // ML Kit Barcode Scanning (offline)
    implementation(libs.mlkit.barcode)

    // CameraX (flux caméra pour le scan)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Pas d'OpenCSV : le parsing est fait à la main dans domain/service/CsvParser.kt, qui doit
    // gérer un fichier source dont les virgules ne sont pas échappées — aucun parseur conforme
    // ne sait le faire. La dépendance était déclarée et importée nulle part.

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // MigrationTestHelper : rejoue une migration sur une vraie base et la confronte au schéma
    // exporté. Seul moyen de prouver qu'une migration ne casse pas les tablettes déjà déployées.
    androidTestImplementation(libs.androidx.room.testing)
}

// Supprime les warnings kapt inutiles
kapt {
    correctErrorTypes = true

    arguments {
        // Room exporte le schéma JSON dans app/schemas/ — à committer.
        // Sans cet argument, exportSchema = true ne produit rien (juste un warning au build) et
        // il n'existe aucune référence pour écrire ni valider une migration.
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
