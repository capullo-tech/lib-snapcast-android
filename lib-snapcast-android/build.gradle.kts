import com.android.build.gradle.internal.registerDependencyCheck

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "tech.capullo.lib_snapcast_android"
    compileSdk = 35

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static", "-DBUILD_SERVER=ON", "-DBUILD_TESTS=OFF")
                cppFlags += listOf("-std=c++14")
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/snapcast/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        prefab = true
    }
    ndkVersion = "25.1.8937393"
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("com.github.badaix:oboe:1.9.0@aar")
    implementation("com.github.badaix:boost:1.85.0@aar")
    implementation("com.github.badaix:flac:1.4.2@aar")
    implementation("com.github.badaix:ogg:1.3.5@aar")
    implementation("com.github.badaix:opus:1.1.2@aar")
    implementation("com.github.badaix:soxr:0.1.3@aar")
    implementation("com.github.badaix:tremor:1.0.1@aar")
    implementation("com.github.badaix:vorbis:1.3.7@aar")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "tech.capullo"
            artifactId = "lib-snapcast-android"
            version = "0.1.0"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}