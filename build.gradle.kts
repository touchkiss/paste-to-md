import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

fun properties(key: String) = project.findProperty(key)?.toString() ?: ""

plugins {
    id("java")
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.17.0"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

val localIde = file(properties("localIdePath"))

repositories {
    mavenCentral()
    intellijPlatform.defaultRepositories()
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        local(localIde)
        bundledPlugins(properties("platformBundledPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
}

// The local 2026.2 platform requires Java 25. Keep Java and Kotlin targets aligned.
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(properties("javaVersion")))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(properties("javaVersion")))
        jvmDefault.set(JvmDefaultMode.ENABLE)
    }
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    patchPluginXml {
        pluginDescription.set(
            projectDir.resolve("README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) }
        )

        changeNotes.set(
            """
            <p>Initial preview release.</p>
            """.trimIndent()
        )
    }

    test {
        useJUnitPlatform()
    }

    buildSearchableOptions {
        isEnabled = false
    }

    named("prepareJarSearchableOptions") {
        enabled = false
    }

    runIde {
        systemProperty("idea.ProcessCanceledException", "disabled")
        systemProperty("idea.debug.mode", "true")
    }
}
