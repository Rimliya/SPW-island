// Build layout adapted from Moriafly/spw-workshop-api (Apache-2.0).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.internal.os.OperatingSystem
import com.xuncorp.spw.workshop.gradle.PluginPermission

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
    id("com.xuncorp.spw.workshop") version "0.1.0-dev23"
    `java-library`
}
group = "io.github.solitaire"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
kotlin { compilerOptions {
    jvmTarget.set(JvmTarget.JVM_25)
    freeCompilerArgs.add("-jvm-default=no-compatibility")
} }
val workshop = "com.github.Moriafly.spw-workshop-api:spw-workshop-api:0.1.0-dev23"
val projectUrl = providers.gradleProperty("projectUrl")
val currentOs = OperatingSystem.current()
val isWindows = currentOs.isWindows
val metadataSources = configurations.create("metadataSources") { isTransitive = false }
val skikoVersion = "0.150.1"
dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(workshop) { isTransitive = false }
    compileOnly("org.pf4j:pf4j:3.12.0")
    compileOnly("net.java.dev.jna:jna:5.17.0")
    compileOnly("net.java.dev.jna:jna-platform:5.17.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.compose.desktop:desktop-jvm:1.12.1")
    implementation("org.jetbrains.skiko:skiko-awt:$skikoVersion")
    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$skikoVersion")
    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:$skikoVersion")
    metadataSources("net.jthink:jaudiotagger:3.0.1:sources")
    testImplementation(kotlin("stdlib"))
    testImplementation("net.java.dev.jna:jna:5.17.0")
}

// Spout2 verification entry points (Windows; spoutChecks runs headless on CI).
tasks.register<JavaExec>("spoutChecks") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.solitaire.spwisland.ui.SpoutChecksKt")
    jvmArgs("-Djava.awt.headless=true")
}
tasks.register<JavaExec>("spoutSoak") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.solitaire.spwisland.ui.SpoutSoak")
    args(providers.gradleProperty("soakSeconds").orElse("660").get())
}
tasks.register<JavaExec>("spoutLifecycle") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.solitaire.spwisland.ui.SpoutLifecycle")
}
configurations.named("runtimeClasspath") {
    // SPW owns JNA and its native dispatcher; do not leave duplicate JARs in the update directory.
    exclude(group = "net.java.dev.jna")
}
val gnomePointer = tasks.register<Zip>("gnomePointer") {
    archiveFileName.set("spw-island-pointer@solitaire.github.io.shell-extension.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from("src/linux/resources/native/gnome-pointer")
    from("LICENSE")
}
tasks.processResources {
    doFirst { check(isWindows) { "Build the universal plugin on Windows with the native toolchains installed" } }
    from("native/shared-fonts/MiSansVF.ttf") { into("fonts") }
    from("native/shared-fonts/PingFang.ttc") { into("fonts") }
    dependsOn("buildSpectrum", "buildWindowsTray", "buildWindowsGlass", "buildSpout", gnomePointer)
    from(layout.buildDirectory.file("native/spw-spectrum.exe")) { into("native") }
    from(layout.buildDirectory.file("native/spw-island-tray.exe")) { into("native") }
    from(layout.buildDirectory.file("native/spw-spout.dll")) { into("native") }
    from(layout.buildDirectory.file("native/spw-island-glass.dll")) { into("native") }
    from({ zipTree(configurations.runtimeClasspath.get().single {
        it.name.startsWith("skiko-awt-runtime-windows-x64-")
    }) }) {
        into("native/compose")
        include("skiko-windows-x64.dll", "icudtl.dat")
    }
    from("src/linux/resources") { exclude("**/__pycache__/**", "**/*.pyc", "native/gnome-pointer/**") }
    from(gnomePointer) { into("native") }
    inputs.property("projectUrl", projectUrl)
    filesMatching("project.properties") { expand("projectUrl" to projectUrl.get()) }
}
spmod {
    PluginClass = "io.github.solitaire.spwisland.host.IslandPlugin"
    PluginId = "io.github.solitaire.spwisland"
    PluginName = "灵动词岛 for SPW"
    PluginVersion = project.version.toString()
    PluginProvider = "solitaire + 大肥鱼（感谢原作者 GaBoron）"
    PluginDescription = "SPW lyrics island; concept by Lyricify / WXRIW (CC BY-SA 4.0); OBS Spout2 capture fork"
    PluginHasConfig = true
    PluginOpenSourceUrl = projectUrl.get()
    PluginPermissions = listOf(PluginPermission.LIBRARY_READ, PluginPermission.KEY_BINDINGS)
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
    from("tools/build-glass.ps1") { into("tools") }
    from("scripts/verify-spmod.ps1") { into("scripts") }
    from("licenses") { into("licenses") }
    from("docs") { into("docs") }
    from("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
        "README.md", "LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", ".gitignore")
}

tasks.register<Exec>("buildSpectrum") {
    val output = layout.buildDirectory.file("native/spw-spectrum.exe")
    inputs.files(fileTree("native") { include("*.cs") })
    outputs.file(output)
    onlyIf { isWindows }
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
tasks.register<Exec>("buildWindowsGlass") {
    val output = layout.buildDirectory.file("native/spw-island-glass.dll")
    inputs.file("native/glass/DesktopCapture.cpp")
    inputs.file("native/glass/DesktopReadback.h")
    inputs.file("native/glass/DesktopViewport.h")
    inputs.file("native/glass/CaptureDiagnostics.h")
    inputs.file("native/glass/DesktopSharedTexture.h")
    inputs.file("tools/build-glass.ps1")
    outputs.file(output)
    onlyIf { isWindows }
    executable = "powershell.exe"
    args("-NoProfile", "-File", file("tools/build-glass.ps1").absolutePath,
        "-Output", output.get().asFile.absolutePath)
}

tasks.named<Zip>("plugin") {
    val libraryNames by lazy {
        val artifacts = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
        val duplicates = artifacts.groupBy { it.file.name }.filterValues { it.size > 1 }.keys
        artifacts.associate { artifact ->
            artifact.file.canonicalPath to if (artifact.file.name in duplicates) {
                "${artifact.moduleVersion.id.group}-${artifact.file.name}"
            } else artifact.file.name
        }
    }
    dependsOn(tasks.jar, "sourceArchive", "spoutChecks")
    archiveFileName.set("spw-island-${project.version}.spmod")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    eachFile {
        if (path.startsWith("lib/")) {
            name = libraryNames[file.canonicalPath] ?: name
        }
    }
    into("licenses") { from("licenses") }
    into("source") { from(tasks.named("sourceArchive")); from(metadataSources) }
    from("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", "README.md")
}
