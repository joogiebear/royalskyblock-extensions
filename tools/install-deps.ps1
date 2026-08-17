# Install the jars these extensions compile against into the local Maven repository.
#
# Neither eco nor RoyalSkyblock is published anywhere this build can reach, so both come from the
# jars the server actually runs. Compiling against the running jars means an extension meets exactly
# the API it was built for.
#
# Re-run after changing RoyalSkyblock's API, or after updating eco.
#
# Arguments are passed as an array rather than with backtick continuations: those get mangled when
# the script is invoked through `powershell -File`, and Maven then reads a stray fragment as a
# lifecycle phase and fails with LifecyclePhaseNotFoundException.

param(
    [string]$EcoJar = "S:\Claude\mcctl\instances\garden\plugins\eco-2026.33-ecohub.jar",
    [string]$EcoVersion = "2026.33",
    [string]$SkyblockJar = "S:\Claude\minecraft\plugins\RoyalSkyblock\build\libs\RoyalSkyblock.jar",
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

Install-Jar -Jar $EcoJar -Group "com.willfp" -Artifact "eco" -Version $EcoVersion
Install-Jar -Jar $SkyblockJar -Group "com.mystipixel" -Artifact "royalskyblock" -Version $SkyblockVersion

Write-Host "Done. Build with: mvn clean package"
