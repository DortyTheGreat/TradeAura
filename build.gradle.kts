// `java` is shadowed by Gradle's JavaPluginExtension accessor once the java plugin (via Loom) is
// applied, so java.util.Properties won't resolve below - import the class directly instead.
import java.util.Properties

plugins {
    // 26.1 is unobfuscated: this is the new, non-remapping loom plugin.
    // The version lives in gradle.properties and is applied in settings.gradle.kts.
    id("net.fabricmc.fabric-loom")
}

fun prop(key: String) = properties[key] as String

/** An optional comma-separated property as a list, blanks dropped. Missing or empty -> empty list. */
fun listProp(key: String): List<String> =
    (properties[key] as String?).orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** The repo url is derived, so owner and name only exist once. */
val repoUrl = "https://github.com/${prop("github_owner")}/${prop("github_repo")}"


/**
 * Version comes from GIT, not from a counter in a committed file.
 *
 * A hand-maintained counter conflicts on every merge and says nothing about
 * what is actually in the build. A tag plus a commit count says both.
 *
 * Only tags shaped vX.Y.Z count. Anything else - old release names like
 * 26.1.2.f, "latest", "snapshot" - is ignored, because `git describe` would
 * otherwise happily base the version on it and produce 1.21.5.21.11b-dev.0.
 *
 * SemVer orders the results correctly: 1.2.4-dev.7 sorts BELOW 1.2.4, which
 * is right - it is on the way there, not there yet. Hence the patch bump:
 * naming it 1.2.3-dev.7 would sort it below the tag it comes after.
 */
val releaseTagPattern = "v[0-9]*"

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

val gitExactTag = git("describe", "--tags", "--exact-match", "--match", releaseTagPattern, "HEAD")?.removePrefix("v")
val gitLastTag = git("describe", "--tags", "--abbrev=0", "--match", releaseTagPattern)?.removePrefix("v")
val gitCommitsSinceTag = if (gitLastTag != null) {
    git("rev-list", "--count", "v$gitLastTag..HEAD")
} else {
    git("rev-list", "--count", "HEAD")
}
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
        // No release tag yet means the first release is still ahead, so count up
        // from 0.0.0 - the bump makes that 0.0.1-dev.N, which is correct:
        // below any tag anyone will ever make.
        val base = bumpPatch(gitLastTag ?: "0.0.0")
        val n = gitCommitsSinceTag ?: "0"
        val dirty = if (gitDirty) "d" else ""
        "$base-dev.$n$dirty+${prop("minecraft_version")}"
    }
}

/**
 * A release is an exact vX.Y.Z tag on a clean tree. Everything else is a dev build.
 *
 * No version.properties, no -PbuildType: whether this is a release is a fact
 * about the repository, not a flag somebody remembers to pass. Passing a flag
 * could label a dirty working tree a release, which is the one thing a
 * release must never be.
 */
val isRelease = gitExactTag != null && !gitDirty

val modVersion = gitVersion

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


// =====================================================================
//  MOD METADATA
//
//  gradle.properties is the only place these values are edited. Two
//  committed files are generated from it:
//
//      src/main/resources/fabric.mod.json
//      meteor-addon-list.json              (read by meteoraddons.com)
//
//  They are committed, and hold real values rather than ${...}
//  placeholders, because tools read them straight from the repository
//  without running Gradle - that is how meteoraddons.com ended up listing
//  "${mod_name}" by "${mod_author}" with zero modules. Only "version"
//  stays a placeholder: it comes from git and processResources fills it in.
//
//  syncModMetadata rewrites both files before every build, so locally
//  they are never stale - commit them together with gradle.properties.
//  On CI (CI=true) it only compares, and fails the build if they drifted.
// =====================================================================

/** Minimal deterministic JSON writer: 2-space indent, keys in insertion order. */
fun toJson(value: Any?, indent: String = ""): String = when (value) {
    null -> "null"
    is String -> "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t") + "\""
    is Number, is Boolean -> value.toString()
    is Map<*, *> ->
        if (value.isEmpty()) "{}"
        else value.entries.joinToString(",\n", "{\n", "\n$indent}") {
            "$indent  " + toJson(it.key.toString()) + ": " + toJson(it.value, "$indent  ")
        }
    is List<*> ->
        if (value.isEmpty()) "[]"
        else value.joinToString(",\n", "[\n", "\n$indent]") { "$indent  " + toJson(it, "$indent  ") }
    else -> error("toJson: cannot write $value")
}

val modIcon = "assets/${prop("mod_id")}/icon.png"

val fabricModJson = linkedMapOf<String, Any?>(
    "schemaVersion" to 1,
    "id" to prop("mod_id"),
    // The one value only known at build time (git) - filled in by processResources.
    "version" to "\${version}",
    "name" to prop("mod_name"),
    "description" to prop("mod_description"),
    "authors" to listOf(prop("mod_author")),
    "contact" to linkedMapOf("sources" to repoUrl, "issues" to "$repoUrl/issues"),
    "license" to "GPL-3.0-or-later",
    "icon" to modIcon,
    "environment" to "client",
    "entrypoints" to linkedMapOf("meteor" to listOf("${prop("mod_package")}.Addon")),
    "mixins" to listProp("mod_mixins"),
    "custom" to linkedMapOf(
        "meteor-client:color" to prop("mod_color"),
        "modmenu" to linkedMapOf("parent" to linkedMapOf("id" to "meteor-client")),
    ),
    "depends" to linkedMapOf(
        "java" to ">=${prop("jdk_version")}",
        "minecraft" to minecraftDepends,
        "meteor-client" to "*",
    ),
)

/** What meteoraddons.com understands. Anything else it drops silently - so it fails the build here. */
val addonListTags = setOf(
    "PvP", "Utility", "Theme", "Render", "Movement", "Building",
    "World", "Misc", "QoL", "Exploit", "Fun", "Automation",
)
val addonListVersionRegex = Regex("""^(1\.\d+(\.\d+)?|\d{2}\.\d+(\.\d+)?)$""")

val supportedVersions = listProp("supported_versions").ifEmpty { listOf(prop("minecraft_version")) }
val addonTags = listProp("addon_tags")

supportedVersions.filterNot { addonListVersionRegex.matches(it) }.let { bad ->
    require(bad.isEmpty()) {
        "supported_versions: $bad - list exact versions such as 1.21.4, one by one; ranges are not understood."
    }
}
addonTags.filterNot { it in addonListTags }.let { bad ->
    require(bad.isEmpty()) { "addon_tags: $bad - allowed: ${addonListTags.joinToString()}" }
}
if (supportedVersions.size >= 15) {
    logger.warn("supported_versions has ${supportedVersions.size} entries - meteoraddons.com flags 15+ as suspicious.")
}

val addonListJson = linkedMapOf<String, Any?>(
    "description" to prop("mod_description"),
    "tags" to addonTags,
    "supported_versions" to supportedVersions,
)

val metadataFiles = mapOf(
    file("src/main/resources/fabric.mod.json") to toJson(fabricModJson) + "\n",
    file("meteor-addon-list.json") to toJson(addonListJson) + "\n",
)

val syncModMetadata by tasks.registering {
    group = "addon"
    description = "Regenerate fabric.mod.json and meteor-addon-list.json from gradle.properties (on CI: only verify)."

    doLast {
        val stale = metadataFiles.filter { (target, text) ->
            !target.exists() || target.readText().replace("\r\n", "\n") != text
        }
        if (stale.isEmpty()) return@doLast

        val names = stale.keys.joinToString { it.relativeTo(projectDir).invariantSeparatorsPath }
        if (System.getenv("CI") != null) {
            throw GradleException(
                "$names out of date with gradle.properties - run ./gradlew syncModMetadata and commit the result.")
        }

        stale.forEach { (target, text) -> target.writeText(text) }
        logger.lifecycle("Regenerated $names from gradle.properties - commit it together with gradle.properties.")
    }
}

tasks {
    processResources {
        // Explicit UTF-8 - Gradle can default to the platform charset on Windows, which would
        // mangle the § color codes of the dev name below.
        filteringCharset = "UTF-8"
        dependsOn(syncModMetadata)

        val modName = prop("mod_name")
        val expandProps = mapOf("version" to modVersion)
        inputs.properties(expandProps)
        inputs.property("displayName", displayName)

        filesMatching("fabric.mod.json") {
            expand(expandProps)
            // Dev builds are listed as "<name> dev.<hash>" in Fabric's mod list. The committed file
            // keeps the plain name, so the repository always shows the real one.
            filter { line -> line.replace("\"name\": \"$modName\"", "\"name\": \"$displayName\"") }
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
//  BUILD / DEPLOY TASKS
//
//      gradlew build           the jar (build/libs), and nothing else
//      gradlew deploy          ...plus swap it into the instance and restart
//
//  Dev and release are the same build, differing only in whether HEAD sits
//  on a clean vX.Y.Z tag. Tag the commit (git tag -a v1.2.3) and the version
//  follows. Published jars are built by GitHub Actions from the tag, see
//  .github/workflows/build.yml.
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
