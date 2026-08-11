import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.jvm.tasks.Jar
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = providers.systemProperty("mainClass")
            .getOrElse("kr.ac.sunmoon.hunminjeongeum_server.MainKt")

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb
            )

            packageName = "kr.ac.sunmoon.hunminjeongeum_server"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    configureUtf8JvmOutput()
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes("Premain-Class" to "kr.ac.sunmoon.hunminjeongeum_server.Utf8ConsoleAgent")
    }
}

fun JavaExec.configureUtf8JvmOutput() {
    val appJar = tasks.named<Jar>("jar")
    dependsOn(appJar)

    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8"
    )

    val agentProvider = objects.newInstance(Utf8ConsoleAgentArgumentProvider::class.java)
    agentProvider.agentJar.set(appJar.flatMap { it.archiveFile })
    jvmArgumentProviders.add(agentProvider)
}

abstract class Utf8ConsoleAgentArgumentProvider : CommandLineArgumentProvider {
    @get:Classpath
    abstract val agentJar: RegularFileProperty

    override fun asArguments(): Iterable<String> {
        return listOf("-javaagent:${agentJar.get().asFile.absolutePath}")
    }
}
