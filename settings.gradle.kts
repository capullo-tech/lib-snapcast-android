pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GithubPackages"
            url = uri("https://maven.pkg.github.com/badaix/snapcast-deps")
            credentials {
                username = providers.gradleProperty("GITHUB_USERNAME").getOrElse(System.getenv("GITHUB_USERNAME") ?: "")
                password = providers.gradleProperty("GITHUB_TOKEN").getOrElse(System.getenv("GITHUB_TOKEN") ?: "")
            }
        }
    }
}

rootProject.name = "Snapcast Android Example"
include(":app")
include(":lib-snapcast-android")
