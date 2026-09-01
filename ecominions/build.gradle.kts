// EcoMinions adapter — the module the Gradle conversion exists for.
//
// It registers libreforge elements (four triggers + a condition), so it must compile Kotlin against
// libreforge itself: a Condition is an abstract Kotlin class whose `arguments` come from a DSL, and
// none of that is Java-visible. Only the libreforge-gradle-plugin — the same toolchain RoyalSkyblock
// builds with — resolves libreforge in a form the Kotlin compiler can read.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
    id("com.willfp.libreforge-gradle-plugin")
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.shadowJar {
    archiveFileName.set("EcoMinions.jar")
    // Match eco's own relocation so every eco plugin (and extension) shares one Kotlin runtime.
    // kotlin-stdlib is compileOnly, so nothing is bundled; only our own references are rewritten.
    relocate("kotlin", "com.willfp.eco.libs.kotlin")
}

// The relocated shadow jar IS the artifact; a plain jar with unrelocated kotlin refs would NPE
// at runtime the moment Intrinsics is touched.
tasks.jar {
    enabled = false
}
tasks.build {
    dependsOn(tasks.shadowJar)
}
