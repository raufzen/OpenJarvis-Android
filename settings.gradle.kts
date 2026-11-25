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
        maven { url = java.net.URI.create("https://maven.aliyun.com/repository/public/") }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI.create("https://repo1.maven.org/maven2/") }
        maven { url = java.net.URI.create("https://maven.aliyun.com/repository/public/") }
    }
}

rootProject.name = "OpenJarvis"
include(":app")
