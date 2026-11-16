plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val tagVersion = System.getenv("VERSION_NAME") ?: "1.0.2"
val tagCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1000002
android {
    namespace = "com.auracharge"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.auracharge"
        minSdk = 26
        targetSdk = 34
        versionCode = tagCode
        versionName = tagVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("releaseFromEnvironment") {
            val path = System.getenv("KEYSTORE_PATH")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (!System.getenv("KEYSTORE_PATH").isNullOrBlank()) signingConfigs.getByName("releaseFromEnvironment") else signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true }
    bundle { language { enableSplit = false } }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.withType<Test>().configureEach {
    inputs.file("src/main/assets/library.json")
    inputs.file("../scripts/library.tsv")
    inputs.dir("src/main/res/raw")
}
