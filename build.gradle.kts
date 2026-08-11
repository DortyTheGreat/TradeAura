// `java` is shadowed by Gradle's JavaPluginExtension accessor once the java plugin (via Loom) is
// applied, so java.util.Properties won't resolve below - import the class directly instead.
import java.util.Properties

plugins {
    // 26.1 is unobfuscated: this is the new, non-remapping loom plugin.
    // The version lives in gradle.properties and is applied in settings.gradle.kts.
    id("net.fabricmc.fabric-loom")
}

fun prop(key: String) = properties[key] as String

/** The repo url is derived, so owner and name only exist once. */
val repoUrl = "https://github.com/${prop("github_owner")}/${prop("github_repo")}"

/**
 * version_code/dev_number live in version.properties, not gradle.properties, because
 * test-addon-prism.bat rewrites that file on every build (-dev bumps dev_number, -release prompts
 * to confirm/change version_code and drops the -dev suffix). Pass -PbuildType=release for a release
 * build; anything else (including not passing it at all) builds a -dev version.
 */
val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val versionCode = versionProps.getProperty("version_code", "a")
val devNumber = versionProps.getProperty("dev_number", "0")
val isRelease = (findProperty("buildType") as String?) == "release"

val modVersion = if (isRelease) {
    "${prop("minecraft_version")}-$versionCode"
} else {
    "${prop("minecraft_version")}-$versionCode-dev.$devNumber"
}

/**
 * Meteor addons are generally forward-compatible - one release is expected to keep working on
 * several newer Minecraft versions until it eventually breaks (see CHANGELOG.md). So the depends
 * range in fabric.mod.json stays open-ended (">=minecraft_version") by default. Set the optional
 * minecraft_version_max property in gradle.properties only once you've confirmed/decided where a
 * given release actually stops working, to cap it instead of failing silently at runtime.
 */
val minecraftVersionMax = (properties["minecraft_version_max"] as String?)?.trim().orEmpty()
val minecraftDepends = if (minecraftVersionMax.isNotEmpty()) {
    ">=${prop("minecraft_version")} <=$minecraftVersionMax"
} else {
    ">=${prop("minecraft_version")}"
}

base {
    archivesName = prop("mod_name")
    version = modVersion
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
        "MOD_VERSION" to modVersion,
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
        // Explicit UTF-8 for the token expansion below - Gradle can default this to the platform
        // charset on Windows, which would mangle the § color code in display_name.
        filteringCharset = "UTF-8"

        // Everything fabric.mod.json needs, straight out of gradle.properties.
        val propertyMap = mapOf(
            "mod_id" to prop("mod_id"),
            "mod_name" to prop("mod_name"),
            // Shown by Fabric's own mod list (the "Mods" button, via the ModMenu addon) as
            // "<name> by <author>" - sneaks the version letter in, colored, without touching the
            // plain mod_name used everywhere else (BuildConfig, log lines, etc).
            "display_name" to "${prop("mod_name")} §e$versionCode§r",
            "mod_description" to prop("mod_description"),
            "mod_author" to prop("mod_author"),
            "mod_package" to prop("mod_package"),
            "mod_repo" to repoUrl,
            "mod_color" to prop("mod_color"),
            "version" to modVersion,
            "minecraft_version" to prop("minecraft_version"),
            "minecraft_depends" to minecraftDepends,
            "jdk_version" to prop("jdk_version"),
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE-NOTICE") {
            rename { "LICENSE_${inputs.properties["archivesName"]}" }
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
