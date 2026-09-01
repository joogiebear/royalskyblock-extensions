// MythicMobs adapter: the easy one. MythicMobs publishes a real, compilable API artifact, so unlike
// the eco adapters this binds with actual types — no reflection.
repositories {
    maven("https://mvn.lumine.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.lumine:Mythic-Dist:5.13.0") { isTransitive = false }
}

tasks.jar {
    archiveFileName.set("MythicMobs.jar")
}
