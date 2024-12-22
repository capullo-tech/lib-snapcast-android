# lib_snapcast_android

`lib_snapcast_android` is an Android library module designed to integrate [Snapcast](https://github.com/badaix/snapcast) functionality into your Android applications. This library provides a comprehensive interface for Snapcast, allowing you to easily include audio streaming capabilities in your app.

## Features

- Integration with Snapcast for audio streaming.
- Supports multiple architecture builds (armeabi-v7a, arm64-v8a, x86, x86_64).
- Prebuilt dependencies for various audio codecs.

## Installation

To include the `lib_snapcast_android` library in your Android project, you need to add the GitHub Packages repository and the dependency to your project’s `build.gradle` files. Please refer to the Github documentation for more information on [authenticating to GitHub Packages](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry#using-a-published-package).

### Step 1: Configure the GitHub Packages Repository

Add the following to your project's root `build.gradle` file (Gradle Groovy) or `build.gradle.kts` file (Kotlin DSL) file.to include the GitHub Packages repository:

Example using Gradle Groovy:
```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/capullo-tech/lib-snapcast-android")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
   }
}
```

Example using Kotlin DSL:
```kotlin
repositories {
    maven {
        setUrl("https://maven.pkg.github.com/capullo-tech/lib-snapcast-android")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

### Step 2: Add the Dependency

Add the package dependencies to your module's `build.gradle` file (Gradle Groovy) or `build.gradle.kts` file (Kotlin DSL) file.

Example using Gradle Groovy:
```groovy
dependencies {
    implementation 'tech.capullo:lib_snapcast_android:0.2.0'
}
```

Example using Kotlin DSL:
```kotlin
dependencies {
    implementation("tech.capullo:lib_snapcast_android:0.2.0")
}
```

### Step 3: Configure Authentication

You need to provide authentication for GitHub Packages. This can be done either by setting environment variables or by using a gradle.properties file.

#### Using Environment Variables

Set the following environment variables:
```sh
export GITHUB_USERNAME=<your-github-username>
export GITHUB_TOKEN=<your-github-token>
```

#### Using Gradle Properties

Add the following to your gradle.properties file:
```properties
gpr.user=<your-github-username>
gpr.token=<your-github-token>
```

### Acknowledgements
- [Snapcast](https://github.com/badaix/snapcast)