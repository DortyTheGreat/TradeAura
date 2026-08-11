pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral()
        gradlePluginPortal()
    }

    // Property names have to match the keys in gradle.properties for this delegate to work.
    val loom_version: String by settings

    plugins {
        id("net.fabricmc.fabric-loom") version loom_version
    }
}

// Has to be applied here (a real plugins {} block, not pluginManagement.plugins) to actually
// register the toolchain repositories - that's what was missing and caused the auto-provisioning
// deprecation warning.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val mod_name: String by settings

rootProject.name = mod_name
