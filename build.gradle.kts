
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

val mindustryVersion = "v154"
val arcVersion = "v154"

val modOutputDir = properties["modOutputDir"] as? String
val debugJarDir = properties["debugGamePath"] as? String

val sdkRoot: String? = System.getenv("ANDROID_HOME")

val buildDir = layout.buildDirectory.get()
val projectName = project.name

plugins {
  kotlin("jvm") version "2.3.10"
  `maven-publish`
  id("org.jetbrains.dokka") version "2.1.0"
}

group = "com.github.EB-wilson"
version = "3.1a"

run { "java SyncBundles.java $version".execute() }

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
  jvmToolchain(21)

  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
  }
}

dokka {
  moduleName.set("Too Many Items")
  dokkaPublications.html {
    outputDirectory.set(layout.projectDirectory.dir("../TooManyItems-doc"))
    suppressInheritedMembers.set(true)
    failOnWarning.set(true)
  }
  dokkaSourceSets.main {
    includes.from("Module.md")
    documentedVisibilities.set(setOf(
      VisibilityModifier.Public,
      VisibilityModifier.Internal
    ))
    reportUndocumented.set(false)
    skipEmptyPackages.set(true)
    skipDeprecated.set(true)
    suppressGeneratedFiles.set(true)
    jdkVersion.set(8)
    sourceLink {
      localDirectory.set(file("src/main/kotlin"))
      remoteUrl(
        "https://github.com/EB-wilson/TooManyItems"
      )
      remoteLineSuffix.set("#L")
    }
  }
  pluginsConfiguration.html {
    customAssets.from("icon.png")
    footerMessage.set("EBwilson")
  }
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])

      groupId = "com.github.EB-wilson"
      artifactId = "TooManyItems"
      version = "${project.version}"
    }
  }
}

repositories {
  mavenLocal()
  mavenCentral()
  maven ("https://maven.xpdustry.com/mindustry")
  maven { url = uri("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository") }
  maven { url = uri("https://www.jitpack.io") }
}

dependencies {
  compileOnly("com.github.Anuken.Arc:arc-core:$arcVersion")
  compileOnly("com.github.Anuken.Mindustry:core:$mindustryVersion")

  implementation("com.github.EB-wilson.UniverseCore:markdown:2.1.1")

  implementation(kotlin("stdlib-jdk8"))
  implementation(kotlin("reflect"))
}

tasks {
  jar{
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName = "${project.name}-desktop.jar"

    from(rootDir) {
      include("mod.hjson")
      include("icon.png")
      include("contributors.hjson")
    }

    from("assets/") {
      include("**")
      exclude("git")
    }

    from(configurations.getByName("runtimeClasspath").map { if (it.isDirectory) it else zipTree(it) })
  }

  register("jarAndroid") {
    dependsOn("jar")

    doLast {
      try {
        if (sdkRoot == null) throw GradleException("No valid Android SDK found. Ensure that ANDROID_HOME is set to your Android SDK directory.");

        val platformRoot = File("$sdkRoot/platforms/").listFiles()
          ?.sorted()
          ?.reversed()
          ?.find { f -> File (f, "android.jar").exists() }
          ?.let { f -> File (f, "android.jar") }

        val d8 = File("$sdkRoot/build-tools/").listFiles()
          ?.sorted()
          ?.reversed()
          ?.find { f -> f.listFiles()?.any{ s -> s.name.contains("d8") }?:false }
          ?.let { f -> f.listFiles()?.find{ s -> s.name.contains("d8") } }

        if (platformRoot == null)
          throw GradleException("No android.jar found. Ensure that you have an Android platform installed.")
        if (d8 == null)
          throw GradleException("No d8 found. Ensure that you have an Android build-tool installed.")

        //collect dependencies needed for desugaring
        val dependencies = (
            configurations.compileClasspath.get().files +
            configurations.runtimeClasspath.get().files +
            setOf(platformRoot)
        ).joinToString(" ") { "--classpath $it" }

        //dex and desugar files - this requires d8 in your PATH
        "${d8.absolutePath} $dependencies --min-api 14 --output ${project.name}-android.jar ${project.name}-desktop.jar"
          .execute(File("$buildDir/libs"))
      }
      catch (e: Throwable) {
        if (e is Error){
          println(e.message)
          return@doLast
        }

        println(e.message)
        println("[WARNING] d8 tool or platform tools was not found, if you was installed android SDK, please check your environment variable")

        delete(
          files("${buildDir}/libs/${project.name}-android.jar")
        )

        val out = JarOutputStream(FileOutputStream("${buildDir}/libs/${project.name}-android.jar"))
        out.putNextEntry(JarEntry("non-androidMod.txt"))
        val reader = StringReader(
          "this mod is don't have classes.dex for android, please consider recompile with a SDK or run this mod on desktop only"
        )

        var r = reader.read()
        while (r != -1) {
          out.write(r)
          out.flush()
          r = reader.read()
        }
        out.close()
      }
    }
  }

  register("deploy", Jar::class) {
    dependsOn("jarAndroid")

    from (
      zipTree("${buildDir}/libs/${project.name}-desktop.jar"),
      zipTree("${buildDir}/libs/${project.name}-android.jar")
    )

    doLast {
      if (!modOutputDir.isNullOrEmpty()) {
        copy {
          into("$modOutputDir/")
          from("${buildDir}/libs/${project.name}-${version}.jar")
        }
      }
    }
  }

  register("deployDesktop", Jar::class) {
    dependsOn("jar")
    archiveFileName = "${project.name}.jar"

    from (zipTree("${buildDir}/libs/${project.name}-desktop.jar"))

    doLast {
      if (!modOutputDir.isNullOrEmpty()) {
        copy {
          into("$modOutputDir/")
          from("${buildDir}/libs/${project.name}.jar")
        }
      }
    }
  }

  register("debugMod", JavaExec::class) {
    dependsOn("classes")
    dependsOn("deployDesktop")

    mainClass = "-jar"
    args = listOf(
      debugJarDir?:"",
      "-debug"
    )
  }
}

fun String.execute(path: File? = null, vararg args: Any?): Process{
  val cmd = split(Regex("\\s+"))
    .toMutableList()
    .apply { addAll(args.map { it?.toString()?:"null" }) }
    .toTypedArray()
  val process = ProcessBuilder(*cmd)
    .directory(path?:rootDir)
    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
    .redirectError(ProcessBuilder.Redirect.INHERIT)
    .start()

  if (process.waitFor() != 0) throw Error(InputStreamReader(process.errorStream).readText())

  return process
}

class Error(str: String): RuntimeException(str)
