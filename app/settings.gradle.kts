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
            url = uri("https://raw.githubusercontent.com/SeineEloquenz/compose-kit/maven/")
        }
    }
}

rootProject.name = "justshop"
include(":app")

val localComposeKitPath = providers.environmentVariable("LOCAL_COMPOSE_KIT").orNull?.takeIf { it.isNotBlank() }
if (localComposeKitPath != null) {
    includeBuild(localComposeKitPath) {
        dependencySubstitution {
            substitute(module("nz.eloque.compose-kit:lib")).using(project(":lib"))
        }
    }
}
 