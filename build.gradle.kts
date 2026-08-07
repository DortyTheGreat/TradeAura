plugins {
    // 26.1 is unobfuscated: this is the new, non-remapping loom plugin.
    // The version lives in gradle.properties and is applied in settings.gradle.kts.
    id("net.fabricmc.fabric-loom")
}

fun prop(key: String) = properties[key] as String

base {
    archivesName = prop("mod_name")
    version = prop("mod_version")
    group = prop("maven_group")
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    // Fabric
    // No mappings dependency anymore, and no modImplementation / remapJar either: nothing gets remapped.
    minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${prop("loader_version")}")

    // Meteor
    implementation("meteordevelopment:meteor-client:${prop("meteor_version")}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(prop("jdk_version").toInt()))
    }
}

tasks {
    processResources {
        // Everything fabric.mod.json needs, straight out of gradle.properties.
        val propertyMap = mapOf(
            "mod_id" to prop("mod_id"),
            "mod_name" to prop("mod_name"),
            "mod_description" to prop("mod_description"),
            "mod_author" to prop("mod_author"),
            "mod_repo" to prop("mod_repo"),
            "mod_color" to prop("mod_color"),
            "version" to prop("mod_version"),
            "minecraft_version" to prop("minecraft_version"),
            "jdk_version" to prop("jdk_version"),
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
