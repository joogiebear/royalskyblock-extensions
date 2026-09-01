pluginManagement {
    repositories {
        gradlePluginPortal()
        // The libreforge-gradle-plugin is published on Auxilor's repo, not the plugin portal.
        maven("https://repo.auxilor.io/repository/maven-public/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "royalskyblock-extensions"

include("ecoskills")
include("ecomobs")
include("ecominions")
include("mythicmobs")
