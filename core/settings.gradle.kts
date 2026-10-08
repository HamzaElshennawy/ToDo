// The core module is its own Gradle build so it can be built and tested
// without the Android SDK: `./gradlew -p core test`.
// The app build pulls it in with includeBuild("core").
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "core"
