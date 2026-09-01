// RoyalSkyblock extensions.
//
// One repository, one module per extension, one jar each — dropped into
// plugins/RoyalSkyblock/extensions/. They share this parent because they share everything that is
// not their own code: the JDK, the provided host dependencies, and extension.yml filtering.
//
// Built with GRADLE, not Maven, for one load-bearing reason: adapters that register libreforge
// elements (ecominions, and any future one) must compile Kotlin against libreforge, and only the
// libreforge-gradle-plugin — the toolchain RoyalSkyblock itself builds with — resolves libreforge
// properly. The published com.willfp:libreforge Maven artifact is an empty stub, and the shipped
// jar's relocated Kotlin is unreadable to the Kotlin compiler. See the git history of BLOCKED.md
// for the full autopsy.

plugins {
    // Nothing applies to the root itself — it exists to version the plugins and share config.
    kotlin("jvm") version "2.3.0" apply false
    id("com.gradleup.shadow") version "9.3.1" apply false
    id("com.willfp.libreforge-gradle-plugin") version "2.0.0" apply false
}

subprojects {
    apply(plugin = "java")

    group = "com.mystipixel"
    version = rootProject.property("version") as String

    repositories {
        // RoyalSkyblock is not published anywhere: it is built from source and installed into the
        // local Maven repository (tools/install-deps.ps1 locally; the CI workflow does the same).
        // Scoped to our own group so a hand-installed eco/libreforge jar can never silently shadow
        // the published artifacts — the exact trap the old Maven build documented.
        mavenLocal {
            content { includeGroup("com.mystipixel") }
        }
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.auxilor.io/repository/maven-public/")
    }

    dependencies {
        // All provided/compileOnly: an extension is loaded by RoyalSkyblock, inside the server,
        // with eco and Paper already on the classpath. Shading any of them would put a second copy
        // of eco in the same JVM — the fastest way to make an eco plugin stop recognising its types.
        "compileOnly"("com.willfp:eco:${rootProject.property("eco-version")}")
        "compileOnly"("com.mystipixel:royalskyblock:${rootProject.property("royalskyblock-version")}")
        "compileOnly"("io.papermc.paper:paper-api:${rootProject.property("paper-version")}")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
        options.encoding = "UTF-8"
    }

    // extension.yml carries ${project.version}; nothing else is templated.
    tasks.withType<ProcessResources>().configureEach {
        filesMatching("extension.yml") {
            expand("project" to mapOf("version" to project.version))
        }
    }
}
