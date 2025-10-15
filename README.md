# About

Android library module designed to integrate [Snapcast](https://github.com/badaix/snapcast) functionality into your Android applications. 

This project includes the original Snapcast project [as a git submodule](./.gitmodules), compiles it using the Android NDK, and packages it.

## Installation

### Add the Dependency

You can add the library to your project using either GitHub Packages or JitPack. 

#### Option 1: GitHub Packages repository

Add the following to your project's root `build.gradle` file:

```groovy
repositories {
    ...
    maven {
        url = uri("https://maven.pkg.github.com/capullo-tech/lib-snapcast-android")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
   }
}
```

Please refer to the Github documentation for more information on [authenticating to GitHub Packages](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry#using-a-published-package).

Add the package dependencies to your module's `build.gradle` file:

```groovy
dependencies {
    implementation 'tech.capullo:lib-snapcast-android:0.29.0'
}
```

#### Option 2: JitPack repository

Add the following to your project's root `build.gradle` file:

```groovy
repositories {
    ...
    maven {
        url = uri("https://jitpack.io")
   }
}
```

Add the package dependencies to your module's `build.gradle` file:

```groovy
dependencies {
    implementation 'com.github.capullo-tech.lib-snapcast-android:lib-snapcast-android:main-SNAPSHOT'
}
```

Build number references: https://jitpack.io/#capullo-tech/lib-snapcast-android

## Usage

Both the `snapcast` and `snapserver` binaries are included in the library as [shared objects](https://developer.android.com/ndk/guides/abis#native-code-in-app-packages).

```kotlin
val nativeLibraryDir = applicationContext.applicationInfo.nativeLibraryDir
```

You can then start either the `snapcast` or `snapserver` binary using the `ProcessBuilder` class:

```kotlin
// snapclient
// man pages: https://github.com/badaix/snapcast/blob/master/client/snapclient.1

val androidPlayer = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) "opensl" else "oboe"

val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val rate: String? = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
val fpb: String? = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)

val sampleformat = "$rate:16:*"

val pb = ProcessBuilder()
    .command(
        "$nativeLibraryDir/libsnapclient.so",
        "--player", androidPlayer,
        "--sampleformat", sampleformat,
        "<tcp|ws|wss>://<snapserver host or IP or mDNS service name>[:port]" // For example: 'tcp://192.168.1.1:1704', or 'ws://homeserver.local' or 'wss://_snapcast-https._tcp'
    )
    .redirectErrorStream(true)

val env = pb.environment()
if (rate != null) env["SAMPLE_RATE"] = rate
if (fpb != null) env["FRAMES_PER_BUFFER"] = fpb

val snapclientProcess = pb.start()

// manage the running process
...
```

```kotlin
// snapserver
// man pages: https://github.com/badaix/snapcast/blob/master/server/snapserver.1

val cacheDir = applicationContext.cacheDir
val confFile = File(cacheDir, "snapserver.conf")

val pb = ProcessBuilder()
    .command(
        "$nativeLibraryDir/libsnapserver.so",
        "--config", confFile.absolutePath,
        "--server.datadir=$cacheDir",
        "--stream.source", "tcp://... OR pipe://... OR) // possible stream source configuration: https://github.com/badaix/snapcast#server
    )
    .redirectErrorStream(true)

val snapserverProcess = pb.start()

// manage the running process
...
```

## Building

Clone this repo with git clone --recurse-submodules or do git submodule update --init --recursive immediately after cloning to initialize the [Snapcast submodule](./.gitmodules)

Run:

```shell
$ ./gradlew build
```

For local development:

```shell
$ ./gradlew publishToMavenLocal
```

### Acknowledgements
- [Snapcast](https://github.com/badaix/snapcast)
- [Snapdroid](https://github.com/badaix/snapdroid)