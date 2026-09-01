// EcoMobs adapter: Java only. No EcoMobs dependency — every published com.willfp:EcoMobs artifact
// is an empty two-entry stub, so the shipped plugin's classes are resolved reflectively at runtime.
tasks.jar {
    archiveFileName.set("EcoMobs.jar")
}
