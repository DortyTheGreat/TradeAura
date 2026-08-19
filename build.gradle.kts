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
 * Version comes from GIT, not from a counter in a committed file.
 *
 * A hand-maintained counter conflicts on every merge, has to be bumped
 * before Gradle even starts (which is why the old code mutated
 * version.properties at configuration time), and says nothing about what
 * is actually in the build. A tag plus a commit count says both.
 *
 *   on an exact tag, clean tree ->  1.2.3+mc26.1.2
 *   7 commits past v1.2.3        ->  1.2.4-dev.7+abc1234
 *   ...with uncommitted changes  ->  1.2.4-dev.7+abc1234.dirty
 *
 * SemVer orders these correctly: 1.2.4-dev.7 sorts BELOW 1.2.4, which is
 * right - it is on the way there, not there yet. Hence the patch bump:
 * naming it 1.2.3-dev.7 would sort it below the tag it comes after.
 */
fun git(vararg args: String): String? = try {
    val p = ProcessBuilder(listOf("git") + args)
        .directory(rootDir).redirectErrorStream(false).start()
    val out = p.inputStream.bufferedReader().readText().trim()
    if (p.waitFor() == 0 && out.isNotEmpty()) out else null
} catch (e: Exception) {
    null   // no git, or not a repo - fall back below
}

fun bumpPatch(v: String): String {
    val parts = v.split(".").toMutableList()
    if (parts.size < 3) return v
    parts[2] = ((parts[2].takeWhile { it.isDigit() }.toIntOrNull() ?: 0) + 1).toString()
    return parts.joinToString(".")
}

val gitExactTag = git("describe", "--tags", "--exact-match", "HEAD")?.removePrefix("v")
val gitLastTag = git("describe", "--tags", "--abbrev=0")?.removePrefix("v")
val gitCommitsSinceTag = git("rev-list", "--count", "HEAD", "^${gitLastTag?.let { "v$it" } ?: "HEAD"}")
val gitHash = git("rev-parse", "--short", "HEAD")
val gitDirty = !git("status", "--porcelain").isNullOrEmpty()

/**
 * Format: <mod_version>+<minecraft_version>, the Fabric convention - Fabric
 * API itself ships as e.g. 0.100.1+1.21.4.
 *
 *     1.2.3+26.1.2           on an exact tag, clean tree
 *     1.2.4-dev.7+26.1.2     7 commits past v1.2.3
 *     1.2.4-dev.7d+26.1.2    ...with uncommitted changes
 *
 * Mod version FIRST because everything after `+` is SemVer build metadata,
 * which version comparison IGNORES. Putting the Minecraft version first
 * would make 1.2.3 and 9.9.9 compare EQUAL under the same game version, so
 * releasing an update would be invisible to anything that checks versions.
 *
 * `-` is not an alternative to `+` here: `-` means pre-release and sorts
 * BELOW the plain version, so `26.1.2-1.2.3` would declare every build a
 * draft of a Minecraft version that never ships.
 */
val gitVersion: String = when {
    // No git at all - a source zip, say. 0.0.0 rather than a guess.
    gitHash == null -> "0.0.0+${prop("minecraft_version")}"
    gitExactTag != null && !gitDirty ->
        "$gitExactTag+${prop("minecraft_version")}"
    else -> {
        // No tags yet means the first release is still ahead, so count up
        // from 0.0.0 - the bump makes that 0.0.1-dev.N, which is correct:
        // below any tag anyone will ever make.
        val base = bumpPatch(gitLastTag ?: "0.0.0")
        val n = gitCommitsSinceTag ?: "0"
        val dirty = if (gitDirty) "d" else ""
        "$base-dev.$n$dirty+${prop("minecraft_version")}"
    }
}

/**
 * A release is an exact tag on a clean tree. Everything else is a dev build.
 *
 * No version.properties, no -PbuildType: whether this is a release is a fact
 * about the repository, not a flag somebody remembers to pass. Passing a flag
 * could label a dirty working tree a release, which is the one thing a
 * release must never be.
 */
val isRelease = gitExactTag != null && !gitDirty

val modVersion = gitVersion!!

/**
 * The coloured suffix in Fabric's mod list. Dev builds only - a release should
 * read as a plain name, and a permanent marker would stop meaning anything.
 *
 * It carries the commit HASH rather than the count that the version string
 * uses. The two answer different questions: the count orders builds, the hash
 * identifies one exactly. A bug report quoting "dev.a1b2c3d" can be checked
 * out; one quoting "dev.7" cannot.
 */
val displayName = if (isRelease) {
    prop("mod_name")
} else {
    "${prop("mod_name")} §edev.${gitHash ?: "unknown"}${if (gitDirty) "d" else ""}§r"
}

/**
 * Meteor addons are generally forward-compatible - one release is expected to keep working on
 * several newer Minecraft versions until it eventually breaks. So the depends range in
 * fabric.mod.json stays open-ended (">=minecraft_version") by default. Set the optional
 * minecraft_version_max property in gradle.properties only once you've confirmed where a given
 * release actually stops working, to cap it instead of failing silently at runtime.
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
 * Generates BuildConfig from gradle.properties so the java side does not have to repeat the mod
 * name, the category or the repo. Regenerated whenever one of those values changes.
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
            // Shown by Fabric's own mod list as "<name> by <author>" - sneaks the version letter
            // in, colored, without touching the plain mod_name used everywhere else.
            "display_name" to displayName,
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

        from("LICENSE") {
            rename { "LICENSE_${inputs.properties["archivesName"]}" }
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}


// =====================================================================
//  BUILD / DEPLOY TASKS   (replaces test-addon-prism.bat)
//
//      gradlew build           the jar, and nothing else
//      gradlew deploy          ...plus swap it into the instance and restart
//      gradlew buildArchive    ...plus copy it into releases/
//
//  There used to be a dev/release pair of each of these. Once the version
//  came from git that distinction stopped existing: dev and release are the
//  same build, differing only in whether HEAD happens to sit on a clean tag.
//  Two names for one operation is just a way to pick the wrong one.
//
//  So what is left is the two things that actually differ - where the jar
//  goes afterwards. Tag the commit (git tag -a v1.2.3) and the version
//  follows; buildArchive warns if HEAD is not on a clean tag.
//
//  Machine-specific paths live in deploy.local.properties (gitignored):
//      prism_instance=26.1.2 - meteor
//      mods_dir=C:\\Users\\you\\AppData\\Roaming\\PrismLauncher\\instances\\...\\mods
//      prism_exe=C:\\Users\\you\\AppData\\Local\\Programs\\PrismLauncher\\prismlauncher.exe
// =====================================================================

val deployProps = Properties().apply {
    val f = rootProject.file("deploy.local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/**
 * Reads a deploy property, expanding ${other_key} references.
 *
 * java.util.Properties has no interpolation of its own, so this is done
 * here - otherwise the instance name has to be typed twice, once on its
 * own line and once inside the mods path, and the two drift apart the
 * first time you rename an instance.
 */
fun deployProp(key: String): String {
    var v = deployProps.getProperty(key, "").trim()
    // Bounded loop rather than recursion: a typo like a=${a} would
    // otherwise hang the build instead of failing it.
    repeat(5) {
        val next = Regex("""\$\{([^}]+)}""").replace(v) { m ->
            deployProps.getProperty(m.groupValues[1], "").trim()
        }
        if (next == v) return@repeat
        v = next
    }
    return v
}

/**
 * Where the jar goes. Set mods_dir explicitly for a non-standard layout;
 * otherwise it is derived from prism_root + prism_instance, so the
 * instance name only ever exists in one place.
 */
fun modsDir(): String {
    val explicit = deployProp("mods_dir")
    if (explicit.isNotEmpty()) return explicit

    val root = deployProp("prism_root")
    val instance = deployProp("prism_instance")
    if (root.isEmpty() || instance.isEmpty()) return ""
    return File(File(File(root, "instances"), instance), "minecraft/mods").path
}

/** The freshly built jar, ignoring -sources and -dev classifiers. */
fun builtJar(): File {
    val dir = layout.buildDirectory.dir("libs").get().asFile
    return dir.listFiles { f: File ->
        f.name.endsWith(".jar") && !f.name.contains("sources") && !f.name.contains("-dev.jar")
    }?.maxByOrNull { it.lastModified() }
        ?: error("No jar in ${dir.absolutePath} - did the build actually run?")
}

fun runCommand(vararg args: String) {
    val p = ProcessBuilder(*args).redirectErrorStream(true).start()
    p.inputStream.bufferedReader().forEachLine { logger.lifecycle("    $it") }
    p.waitFor()
}

val buildArchive by tasks.registering {
    group = "addon"
    description = "Build and copy the jar into releases/."
    dependsOn(tasks.jar)
    doLast {
        val jar = builtJar()
        val releases = rootProject.file("releases").apply { mkdirs() }
        jar.copyTo(File(releases, jar.name), overwrite = true)
        logger.lifecycle("Built and archived releases/${jar.name}")

        // Jars are small, but a releases/ folder that quietly grows into
        // the repo is exactly the phase_v2 mistake in a different costume.
        val mb = (releases.listFiles()?.sumOf { it.length() } ?: 0L) / 1048576
        if (mb > 5) {
            logger.warn("WARNING: releases/ is now ~$mb MB - trim old jars before committing.")
        }
        if (!isRelease) {
            logger.warn("NOT a release build: HEAD is not on a clean tag, so this " +
                        "jar is $modVersion. Tag it first (git tag -a v1.2.3) if " +
                        "you meant to release.")
        }
        logger.lifecycle("Remember to add a CHANGELOG.md entry for $modVersion.")
    }
}

/** Kill the running instance, swap the jar in, relaunch. */
fun deployAndLaunch() {
    val modsDir = modsDir()
    if (modsDir.isEmpty()) {
        error("No mods folder. Copy deploy.local.properties.example to " +
              "deploy.local.properties and set either prism_root + " +
              "prism_instance, or mods_dir directly.")
    }

    val instance = deployProp("prism_instance")
    if (instance.isNotEmpty()) {
        // PrismLauncher passes -Djava.library.path with FORWARD slashes even
        // on Windows, so the match below uses / deliberately - matching on \
        // silently finds nothing and you end up testing the old jar.
        logger.lifecycle("Stopping a running instance, if any...")
        runCommand("powershell", "-NoProfile", "-Command",
            "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe' or Name='java.exe'\" | " +
            "Where-Object { \$_.CommandLine -like '*instances/$instance/*' } | " +
            "ForEach-Object { Write-Host ('Stopping PID ' + \$_.ProcessId); " +
            "Stop-Process -Id \$_.ProcessId -Force; Start-Sleep -Seconds 1 }")
    }

    val mods = File(modsDir).apply { mkdirs() }
    val name = project.base.archivesName.get()
    mods.listFiles { f: File -> f.name.startsWith("$name-") && f.name.endsWith(".jar") }
        ?.forEach { it.delete() }

    val jar = builtJar()
    jar.copyTo(File(mods, jar.name), overwrite = true)
    logger.lifecycle("Deployed ${jar.name} to $modsDir")

    val exe = deployProp("prism_exe")
    if (exe.isNotEmpty() && instance.isNotEmpty()) {
        logger.lifecycle("Launching $instance...")
        ProcessBuilder(exe, "-l", instance).start()
    }
}

tasks.register("deploy") {
    group = "addon"
    description = "Build, swap the jar into the Prism instance, and restart it."
    dependsOn(tasks.jar)
    doLast { deployAndLaunch() }
}

// =====================================================================
//  runClient against YOUR instance instead of an empty run/ folder.
//
//  `gradlew runClient` is the right way to iterate - it puts the mod on
//  the classpath directly, so there is no jar, no copying and no launcher
//  restart, and from a debugger you get hot-swap on method bodies. Out of
//  the box, though, it runs in ./run: no worlds, no options, no other mods.
//
//  Pointing runDir at the Prism instance's minecraft folder fixes all of
//  that at once - same saves, same options, same mods, including Meteor.
//
//  THE CATCH, and it will bite exactly once: that folder's mods/ may still
//  contain a packaged copy of THIS addon from a previous deploy. Fabric
//  would then load the mod twice under one id and refuse to start. The
//  doFirst below deletes it first, which also means deploy and
//  runClient can be used interchangeably without thinking about it.
// =====================================================================
if (modsDir().isEmpty()) {
    logger.lifecycle(
        "[runInstance] Not available: create deploy.local.properties (copy the " +
        ".example) and set prism_root + prism_instance. `gradlew runClient` " +
        "works regardless.")
} else {
    val gameDir = File(modsDir()).parentFile   // ...\minecraft
    if (!gameDir.isDirectory) {
        logger.warn("[runInstance] ${gameDir.absolutePath} does not exist - " +
                    "check prism_root and prism_instance.")
    } else {
        logger.lifecycle("[runInstance] Game directory: ${gameDir.absolutePath}")
    }


    // runDir is ALWAYS resolved against the project directory - loom
    // concatenates rather than checking for an absolute path, which on
    // Windows produces "C:\project\C:\Users\..." and fails with
    // "Illegal char <:>". So hand it a relative path instead.
    val relativeGameDir = try {
        gameDir.relativeTo(projectDir).invariantSeparatorsPath
    } catch (e: IllegalArgumentException) {
        // Different drives - no relative path exists between them.
        null
    }

    if (relativeGameDir == null) {
        logger.warn("[runInstance] ${gameDir.absolutePath} is on a different drive " +
                    "than the project, and runDir only accepts a relative path. " +
                    "Move the project onto the same drive, or junction ./run at " +
                    "the instance folder by hand and use runClient.")
    } else {
        // A SEPARATE run config, not an override of runClient. Plain
        // `gradlew runClient` keeps doing the standard thing - empty ./run,
        // no other mods - which is what a fresh clone of this repo should
        // do, and what you want when checking the addon works on its own.
        loom {
            runs {
                register("instance") {
                    client()
                    configName = "Minecraft Client (Prism instance)"
                    runDir = relativeGameDir
                }
            }
        }
    }

    tasks.named("runInstance") {
        doFirst {
            val name = project.base.archivesName.get()
            File(modsDir())
                .listFiles { f: File -> f.name.startsWith("$name-") && f.name.endsWith(".jar") }
                ?.forEach {
                    logger.lifecycle("Removing packaged copy ${it.name} - the dev " +
                                     "build is on the classpath already")
                    it.delete()
                }
        }
    }
}

// =====================================================================
//  DEBUGGING
//
//  Pass -Pdebug to make the GAME's JVM open a debugger port and wait for
//  something to attach:
//
//      gradlew runInstance -Pdebug
//
//  Not --debug-jvm: that debugs the Gradle daemon, not Minecraft, and
//  produces a debugger session where none of your breakpoints ever fire.
//
//  suspend=y is deliberate. The interesting code is often in onInitialize,
//  which has already run by the time you could attach otherwise.
// =====================================================================
if (hasProperty("debug")) {
    val port = (findProperty("debugPort") as String?) ?: "5005"
    loom {
        runs {
            configureEach {
                vmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:$port")
            }
        }
    }
    logger.lifecycle("[debug] The game will wait for a debugger on port $port.")
}

