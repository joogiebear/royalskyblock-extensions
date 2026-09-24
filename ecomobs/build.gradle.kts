// EcoMobs adapter: Java only. No EcoMobs dependency — every published com.willfp:EcoMobs artifact
// is an empty two-entry stub, so the shipped plugin's classes are resolved reflectively at runtime.
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("EcoMobs.jar")
}
