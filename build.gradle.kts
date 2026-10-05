// Build layout adapted from Moriafly/spw-workshop-api (Apache-2.0).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
    id("org.jetbrains.compose") version "1.12.0"
    `java-library`
}
group = "io.github.gaboron"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
val workshop = "com.github.Moriafly:spw-workshop-api:0.1.0-dev20"
val projectUrl = providers.gradleProperty("projectUrl")
val currentOs = OperatingSystem.current()
val targetPlatform = providers.gradleProperty("targetPlatform").orNull
require(targetPlatform == null || targetPlatform == "windows") {
    "targetPlatform must be windows; this fork only builds the Windows plugin"
}
val isWindows = targetPlatform?.let { it == "windows" } ?: currentOs.isWindows
val metadataSources by configurations.creating { isTransitive = false }
dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(workshop) { isTransitive = false }
    compileOnly("org.pf4j:pf4j:3.12.0")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(compose.desktop.currentOs)
    metadataSources("net.jthink:jaudiotagger:3.0.1:sources")
    testImplementation(kotlin("stdlib"))
}

// Spout2 verification entry points (Windows; spoutChecks also runs headless elsewhere).
tasks.register<JavaExec>("spoutChecks") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gaboron.spwisland.ui.SpoutChecksKt")
}
tasks.register<JavaExec>("spoutSoak") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gaboron.spwisland.ui.SpoutSoak")
    args(providers.gradleProperty("soakSeconds").orElse("660").get())
}
tasks.register<JavaExec>("spoutLifecycle") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gaboron.spwisland.ui.SpoutLifecycle")
}
tasks.processResources {
    from("native/shared-fonts/MiSansVF.ttf") { into("fonts") }
    if (isWindows) {
        dependsOn("buildSpectrum")
        dependsOn("buildWindowsTray")
        dependsOn("buildSpout")
        from(layout.buildDirectory.file("native/spw-spectrum.exe")) { into("native") }
        from(layout.buildDirectory.file("native/spw-island-tray.exe")) { into("native") }
        from(layout.buildDirectory.file("native/spw-spout.dll")) { into("native") }
        from({ zipTree(configurations.runtimeClasspath.get().single {
            it.name.startsWith("skiko-awt-runtime-windows-x64-")
        }) }) {
            into("native/compose")
            include("skiko-windows-x64.dll", "icudtl.dat")
        }
    }
    inputs.property("projectUrl", projectUrl)
    filesMatching("project.properties") { expand("projectUrl" to projectUrl.get()) }
}
tasks.jar {
    manifest.attributes(
        "Plugin-Class" to "io.github.gaboron.spwisland.host.IslandPlugin",
        "Plugin-Id" to "io.github.gaboron.spwisland",
        "Plugin-Name" to "Dynamic Lyrics Island for SPW",
        "Plugin-Version" to project.version.toString(),
        "Plugin-Provider" to "GaBoron / Solitaire",
        "Plugin-Description" to "SPW lyrics island; concept by Lyricify / WXRIW (CC BY-SA 4.0); OBS Spout2 capture fork",
        "Plugin-Has-Config" to "true",
        "Plugin-Open-Source-Url" to projectUrl.get()
    )
}
tasks.register<Zip>("sourceArchive") {
    archiveFileName.set("spw-island-${project.version}-source.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from("src") { into("src"); exclude("**/__pycache__/**", "**/*.pyc") }
    from("native") {
        into("native")
        exclude("**/bin/**", "**/obj/**", "spout/build/**")
    }
    from("gradle") { into("gradle") }
    from("licenses") { into("licenses") }
    from("docs") { into("docs") }
    from("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
        "README.md", "LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", ".gitignore")
}

tasks.register<Exec>("buildSpectrum") {
    val output = layout.buildDirectory.file("native/spw-spectrum.exe")
    inputs.files(fileTree("native") { include("*.cs") })
    outputs.file(output)
    onlyIf {
        if (!isWindows) logger.lifecycle("Skipping the Windows spectrum helper on ${currentOs.name}")
        isWindows
    }
    doFirst { output.get().asFile.parentFile.mkdirs() }
    executable = "${System.getenv("WINDIR") ?: "C:/Windows"}/Microsoft.NET/Framework64/v4.0.30319/csc.exe"
    args("/nologo", "/target:winexe", "/platform:x64", "/optimize+", "/out:${output.get().asFile.absolutePath}",
        file("native/AudioInterop.cs").absolutePath, file("native/Spectrum.cs").absolutePath,
        file("native/ProcessLoopback.cs").absolutePath, file("native/SpectrumLevels.cs").absolutePath)
}

tasks.register<Exec>("buildWindowsTray") {
    val output = layout.buildDirectory.file("native/spw-island-tray.exe")
    inputs.file("native/Tray.cs")
    outputs.file(output)
    onlyIf { isWindows }
    doFirst { output.get().asFile.parentFile.mkdirs() }
    executable = "${System.getenv("WINDIR") ?: "C:/Windows"}/Microsoft.NET/Framework64/v4.0.30319/csc.exe"
    args("/nologo", "/target:winexe", "/platform:x64", "/optimize+",
        "/reference:System.Windows.Forms.dll", "/reference:System.Drawing.dll",
        "/out:${output.get().asFile.absolutePath}", file("native/Tray.cs").absolutePath)
}

tasks.register<Exec>("configureSpout") {
    onlyIf { isWindows }
    inputs.files(fileTree("native/spout"))
    outputs.file(layout.buildDirectory.file("spout/CMakeCache.txt"))
    commandLine("cmake", "-S", "native/spout", "-B", "build/spout", "-A", "x64")
}
tasks.register<Exec>("buildSpout") {
    dependsOn("configureSpout")
    onlyIf { isWindows }
    inputs.files(fileTree("native/spout"))
    outputs.file(layout.buildDirectory.file("native/spw-spout.dll"))
    commandLine("cmake", "--build", "build/spout", "--config", "Release", "--parallel", "4")
    doLast {
        copy { from("build/spout/Release/spw-spout.dll"); into("build/native") }
    }
}

fun registerPluginArchive(taskName: String, platform: String, enabled: Boolean) = tasks.register<Zip>(taskName) {
    val libraryNames by lazy {
        val artifacts = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
        val duplicates = artifacts.groupBy { it.file.name }.filterValues { it.size > 1 }.keys
        artifacts.associate { artifact ->
            artifact.file.canonicalPath to if (artifact.file.name in duplicates) {
                "${artifact.moduleVersion.id.group}-${artifact.file.name}"
            } else artifact.file.name
        }
    }
    dependsOn(tasks.jar, "sourceArchive")
    onlyIf {
        if (!enabled) logger.lifecycle("$taskName must run on a $platform host")
        enabled
    }
    archiveFileName.set("spw-island-${project.version}-$platform-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("classes") { from(tasks.jar.map { zipTree(it.archiveFile) }) }
    into("lib") {
        from(configurations.runtimeClasspath) {
            eachFile {
                name = libraryNames[file.canonicalPath] ?: name
            }
        }
    }
    into("licenses") { from("licenses") }
    into("source") { from(tasks.named("sourceArchive")); from(metadataSources) }
    from("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", "README.md")
}

val pluginWindows = registerPluginArchive("pluginWindows", "windows", isWindows)

tasks.register("plugin") {
    group = "build"
    description = "Builds the Windows plugin archive. This fork only supports Windows."
    dependsOn(pluginWindows)
}
