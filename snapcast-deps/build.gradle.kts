plugins {
    id("maven-publish")
}

// https://discuss.gradle.org/t/maven-publish-with-aar-dependency-could-not-resolve-all-files-for-configuration-debugruntimeclasspath/45467
// https://stackoverflow.com/questions/60878599/error-building-android-library-direct-local-aar-file-dependencies-are-not-supp
configurations.maybeCreate("default")
artifacts.add("default", file("libs/oboe-1.9.0.aar"))
artifacts.add("default", file("libs/boost-1.85.0.aar"))
artifacts.add("default", file("libs/flac-1.4.2.aar"))
artifacts.add("default", file("libs/ogg-1.3.5.aar"))
artifacts.add("default", file("libs/opus-1.1.2.aar"))
artifacts.add("default", file("libs/soxr-0.1.3.aar"))
artifacts.add("default", file("libs/tremor-1.0.1.aar"))
artifacts.add("default", file("libs/vorbis-1.3.7.aar"))
artifacts.add("default", file("libs/openssl-3.2.0.aar"))

fun getSnapcastGitTag(): String =
    ProcessBuilder("git", "describe", "--tags")
        .directory(file("${rootDir}/lib-snapcast-android/src/main/cpp/snapcast"))
        .start()
        .inputStream
        .bufferedReader().readText().trim()
        .removePrefix("v")

publishing {
    publications {
        create<MavenPublication>("aarPublication") {
            groupId = "tech.capullo"
            artifactId = "snapcast-deps"
            version = "0.29.0"

            // Add each .aar file as an artifact
            artifact(file("libs/oboe-1.9.0.aar")) {
                extension = "aar"
                classifier = "oboe"
            }
            artifact(file("libs/boost-1.85.0.aar")) {
                extension = "aar"
                classifier = "boost"
            }
            artifact(file("libs/flac-1.4.2.aar")) {
                extension = "aar"
                classifier = "flac"
            }
            artifact(file("libs/ogg-1.3.5.aar")) {
                extension = "aar"
                classifier = "ogg"
            }
            artifact(file("libs/opus-1.1.2.aar")) {
                extension = "aar"
                classifier = "opus"
            }
            artifact(file("libs/soxr-0.1.3.aar")) {
                extension = "aar"
                classifier = "soxr"
            }
            artifact(file("libs/tremor-1.0.1.aar")) {
                extension = "aar"
                classifier = "tremor"
            }
            artifact(file("libs/vorbis-1.3.7.aar")) {
                extension = "aar"
                classifier = "vorbis"
            }
            artifact(file("libs/openssl-3.2.0.aar")) {
                extension = "aar"
                classifier = "openssl"
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/capullo-tech/lib-snapcast-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}