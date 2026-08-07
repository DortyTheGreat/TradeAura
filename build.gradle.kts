plugins {
    // 26.1 is unobfuscated: this is the new, non-remapping loom plugin.
    // The version lives in gradle.properties and is applied in settings.gradle.kts.
    id("net.fabricmc.fabric-loom")
}

fun prop(key: String) = properties[key] as String

/** The repo url is derived, so owner and name only exist once. */
val repoUrl = "https://github.com/${prop("github_owner")}/${prop("github_repo")}"

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

/**
 * Generates com.TradeAura.addon.BuildConfig from gradle.properties so the java side does not have to repeat
 * the mod name, the category or the repo. Regenerated whenever one of those values changes.
 */
val generateBuildConfig by tasks.registering {
    val values = linkedMapOf(
        "MOD_ID" to prop("mod_id"),
        "MOD_NAME" to prop("mod_name"),
        "MOD_VERSION" to prop("mod_version"),
        "MOD_DESCRIPTION" to prop("mod_description"),
        "MOD_AUTHOR" to prop("mod_author"),
        "MOD_PACKAGE" to prop("mod_package"),
        "CATEGORY_NAME" to prop("category_name"),
        "GITHUB_OWNER" to prop("github_owner"),
        "GITHUB_REPO" to prop("github_repo"),
        "GITHUB_URL" to repoUrl,
        "MINECRAFT_VERSION" to prop("minecraft_version")
    )

    val packageName = prop("mod_package")
    val outputDir = layout.buildDirectory.dir("generated/sources/buildconfig")

    inputs.properties(values)
    outputs.dir(outputDir)

    doLast {
        val dir = outputDir.get().asFile.resolve(packageName.replace('.', '/'))
        dir.mkdirs()

        dir.resolve("BuildConfig.java").writeText(buildString {
            appendLine("package $packageName;")
            appendLine()
            appendLine("/** Generated from gradle.properties by the generateBuildConfig task. Do not edit. */")
            appendLine("public final class BuildConfig {")
            appendLine("    private BuildConfig() {")
            appendLine("    }")
            appendLine()
            values.forEach { (key, value) ->
                val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
                appendLine("    public static final String $key = \"$escaped\";")
            }
            appendLine("}")
        })
    }
}

sourceSets.main.get().java.srcDir(generateBuildConfig)

tasks {
    processResources {
        // Everything fabric.mod.json needs, straight out of gradle.properties.
        val propertyMap = mapOf(
            "mod_id" to prop("mod_id"),
            "mod_name" to prop("mod_name"),
            "mod_description" to prop("mod_description"),
            "mod_author" to prop("mod_author"),
            "mod_package" to prop("mod_package"),
            "mod_repo" to repoUrl,
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