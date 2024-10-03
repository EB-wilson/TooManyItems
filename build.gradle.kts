import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.tools.javac.JavacCompilerFactory
import org.codehaus.groovy.tools.javac.JavacJavaCompiler
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

val kotlin_version = "1.9.23"
//the build number that this mod is made for
val mindustryVersion = "v146"

val modOutputDir = properties["modOutputDir"] as? String

val sdkRoot: String? = System.getenv("ANDROID_HOME")

//version of SDK you will be using
val minSdkAPI = 30

val buildDir = layout.buildDirectory.get().asFile.path
val projectName = project.name

plugins {
    kotlin("jvm") version "1.9.24"
    `maven-publish`
}

group = "com.github.EB-wilson"
version = "2.4.1"

run {
    "javac SyncBundles.java".execute()
    "java SyncBundles $version".execute()

    rootDir.listFiles()
        ?.filter { it.extension == "class" && it.name.contains("SyncBundles") }
        ?.forEach { it.delete() }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.EB-wilson"
            artifactId = "TooManyItems"
            version = "${project.version}"

            from(components["java"])
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository") }
    maven { url = uri("https://www.jitpack.io") }
}


dependencies {
    compileOnly("com.github.Anuken.Arc:arc-core:$mindustryVersion")
    compileOnly("com.github.Anuken.Mindustry:core:$mindustryVersion")

    implementation("com.github.EB-wilson.UniverseCore:markdown:2.1.1")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
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
                val fi = File("$sdkRoot/platforms/")
                if (!fi.exists()) throw RuntimeException("android SDK platfroms was not found")

                val platformRoot = fi.listFiles()!!.sorted().reversed().find { File(it, "android.jar").exists() }

                //collect dependencies needed for desugaring
                val dependencies = (
                    configurations.getByName("runtimeClasspath") +
                        configurations.getByName("compileClasspath") +
                        File(platformRoot, "android.jar")
                    ).joinToString(" ") { "--classpath $it" }

                try {
                    println("build android dex...")

                    //dex and desugar files - this requires d8 in your PATH
                    "d8 $dependencies --min-api 14 --output ${project.name}-android.jar ${project.name}-desktop.jar"
                        .execute(File("${buildDir}/libs"))
                }
                catch (e: Throwable) {
                    if (e is Error) throw e

                    val d8 = File("$sdkRoot/build-tools/").listFiles()!!.find {
                        it.listFiles()!!.any { f ->
                            f.name.contains("d8")
                        } && Integer.valueOf(it.name.substring(0, 2)) >= minSdkAPI
                    }?.listFiles()?.find { it.name.contains("d8") }

                    "$d8 $dependencies --min-api 14 --output ${project.name}-android.jar ${project.name}-desktop.jar"
                        .execute(File("${buildDir}/libs"))
                }
            }
            catch (e: Throwable) {
                if (e is Error){
                    println(e.message)
                    return@doLast
                }

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
                    from("${buildDir}/libs/${project.name}.jar")
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
            project.properties["debugGamePath"] as? String?:"",
            "-debug"
        )
    }
}

fun String.execute(path: File? = null, vararg args: Any?): Process{
    val process = Runtime.getRuntime().exec(
        this.split(Regex("\\s+")).toMutableList()
            .apply { addAll(args.map { it?.toString()?:"null" }) }.toTypedArray(),
        null,
        path?:rootDir
    )

    if (process.waitFor() != 0) throw Error(InputStreamReader(process.errorStream).readText())

    return process
}

class Error(str: String): RuntimeException(str)
