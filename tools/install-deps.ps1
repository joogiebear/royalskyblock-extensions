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

# The version is DERIVED from the host source tree, not typed here. It used to be a default on
# this parameter, which meant the same number was authored in two repositories and had to be kept
# in step by hand; it went stale the first time RoyalSkyblock released, and the symptom was an
# unresolved com.mystipixel:royalskyblock that named the old version and explained nothing.
#
# Pass -SkyblockVersion only to install under something other than what the host tree says.
param(
    [string]$SkyblockJar = "S:\Claude\royal-plugins\RoyalSkyblock\build\libs\RoyalSkyblock.jar",
    [string]$SkyblockVersion
)

$ErrorActionPreference = "Stop"

# The version comes from the host's gradle.properties - the same file the CI workflows read, and
# the same value the host's own release job stamps into the build.
#
# Not from the jar's plugin.yml, which would be the obvious choice: RoyalSkyblock's
# processResources does not declare project.version as a task input, so an incremental local build
# after a version change leaves plugin.yml holding the OLD version while the filename and
# gradle.properties carry the new one. A clean CI build is unaffected, which is why released jars
# are correct and only local ones lie. Reading it here would install current classes under a stale
# version number.
function Get-HostRoot {
    param([string]$Jar)
    # <root>/build/libs/RoyalSkyblock.jar
    return Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $Jar))
}

function Get-SourceVersion {
    param([string]$Props)

    $line = Select-String -Path $Props -Pattern '^version=' | Select-Object -First 1
    if (-not $line) {
        throw "no 'version=' line in $Props. Pass -SkyblockVersion to override."
    }
    return $line.Line -replace '^version=', ''
}

if (-not $SkyblockVersion) {
    if (-not (Test-Path $SkyblockJar)) {
        throw "jar not found: $SkyblockJar. Build RoyalSkyblock first."
    }
    $props = Join-Path (Get-HostRoot -Jar $SkyblockJar) "gradle.properties"
    if (-not (Test-Path $props)) {
        throw "cannot derive the host version: $props not found. Pass -SkyblockVersion to override."
    }

    $SkyblockVersion = Get-SourceVersion -Props $props
    Write-Host "Host version, derived from the host source tree: $SkyblockVersion"

    # A jar older than the version that names it is the case worth stopping for: it means the host
    # moved and was not rebuilt, and installing it would put yesterday's classes under today's
    # version number - an artifact that resolves cleanly and misbehaves at runtime.
    $jarTime = (Get-Item $SkyblockJar).LastWriteTime
    $propsTime = (Get-Item $props).LastWriteTime
    if ($jarTime -lt $propsTime) {
        throw @"
Stale RoyalSkyblock build.
  jar    : $jarTime  ($SkyblockJar)
  source : $propsTime  (version=$SkyblockVersion)

Rebuild the host with a CLEAN build - a plain 'build' is not enough. Gradle holds
processResources up-to-date across a version change, so an incremental rebuild leaves this
jar untouched and you land back here:

    cd $(Get-HostRoot -Jar $SkyblockJar)
    .\gradlew.bat clean build

Pass -SkyblockVersion to install the jar as it is anyway.
"@
    }
}

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

# Point the build at what was actually installed. A local `gradlew build` reads gradle.properties
# rather than the -P the CI workflows pass, so without this the pin and the installed artifact
# drift apart again the moment the host moves — which is the whole failure this script now avoids.
$propsPath = Join-Path (Split-Path -Parent $PSScriptRoot) "gradle.properties"
$current = Select-String -Path $propsPath -Pattern '^royalskyblock-version=' | Select-Object -First 1
$currentVersion = if ($current) { $current.Line -replace '^royalskyblock-version=', '' } else { "" }

if ($currentVersion -ne $SkyblockVersion) {
    Write-Host "Updating royalskyblock-version: $currentVersion -> $SkyblockVersion"
    (Get-Content $propsPath) `
        -replace '^royalskyblock-version=.*', "royalskyblock-version=$SkyblockVersion" |
        Set-Content $propsPath
    Write-Host "gradle.properties changed - commit it so the checked-in pin matches the host."
}

Write-Host "Done. Build with: .\gradlew.bat build"
