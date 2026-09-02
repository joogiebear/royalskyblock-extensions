# Install RoyalSkyblock into the local Maven repository, so extensions can compile against it.
#
# ONLY RoyalSkyblock. eco and libreforge come from repo.auxilor.io and must NOT be installed from the
# jars on the server: those are shaded, with Kotlin relocated to com.willfp.eco.libs.kotlin, which
# leaves their @Metadata unreadable to the Kotlin compiler. A Java module survives that because the
# Java-visible API is intact; a Kotlin one does not - `arguments` and `get` vanish, and open vals like
# Condition.description stop being overridable.
#
# The local repository wins over remote ones, so an installed copy silently shadows the published
# artifact and the failure looks like the published one being wrong. If Kotlin compilation starts
# failing with unresolved libreforge references, check ~/.m2/repository/com/willfp first.
#
# Re-run after changing RoyalSkyblock's API.
#
# Arguments are passed as an array rather than with backtick continuations: those get mangled when
# the script is invoked through `powershell -File`, and Maven then reads a stray fragment as a
# lifecycle phase and fails with LifecyclePhaseNotFoundException.

param(
    [string]$SkyblockJar = "S:\Claude\royal-plugins\RoyalSkyblock\build\libs\RoyalSkyblock.jar",
    [string]$SkyblockVersion = "2026.32.0"
)

$ErrorActionPreference = "Stop"

function Install-Jar {
    param([string]$Jar, [string]$Group, [string]$Artifact, [string]$Version)

    if (-not (Test-Path $Jar)) {
        throw "jar not found: $Jar"
    }
    Write-Host "Installing $Group`:$Artifact`:$Version from $Jar"
    $mvnArgs = @(
        "-q", "install:install-file",
        "-Dfile=$Jar",
        "-DgroupId=$Group",
        "-DartifactId=$Artifact",
        "-Dversion=$Version",
        "-Dpackaging=jar"
    )
    & mvn $mvnArgs
    if ($LASTEXITCODE -ne 0) {
        throw "mvn install-file failed for $Artifact"
    }
}

Install-Jar -Jar $SkyblockJar -Group "com.mystipixel" -Artifact "royalskyblock" -Version $SkyblockVersion

Write-Host "Done. Build with: .\gradlew.bat build"
