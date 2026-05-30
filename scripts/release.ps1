[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Version,

    [switch]$NoPush
)

$ErrorActionPreference = 'Stop'

if ($Version.StartsWith('v')) {
    $Version = $Version.Substring(1)
}

if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9.-]+)?$') {
    throw "Version must look like 1.0.13."
}

$tag = "v$Version"
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $root

if (git status --porcelain) {
    throw "Working tree is not clean. Commit or stash changes before releasing."
}

if (git tag --list $tag) {
    throw "Tag $tag already exists."
}

$pomPath = Join-Path $root 'pom.xml'
[xml]$pom = Get-Content $pomPath
$currentVersion = $pom.project.version
if ($currentVersion -ne $Version) {
    mvn -B versions:set "-DnewVersion=$Version" "-DgenerateBackupPoms=false"
}

mvn -B "-Dpaper.api.version=26.1.2.build.66-stable" -DskipTests compile
mvn -B clean package

$jar = Join-Path $root 'target\RuinedCollections.jar'
if (-not (Test-Path $jar)) {
    throw "Build did not create target\RuinedCollections.jar."
}

$jarSize = (Get-Item $jar).Length
if ($jarSize -ge 15000000) {
    throw "Jar is $jarSize bytes, which is too large for Hangar direct upload."
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    $entry = $zip.GetEntry('plugin.yml')
    if ($null -eq $entry) {
        throw "plugin.yml is missing from the jar."
    }
    $reader = [IO.StreamReader]::new($entry.Open())
    try {
        $pluginYml = $reader.ReadToEnd()
    } finally {
        $reader.Dispose()
    }
} finally {
    $zip.Dispose()
}

if ($pluginYml -notmatch "version:\s+$([Regex]::Escape($Version))") {
    throw "plugin.yml inside the jar does not show version $Version."
}

if (git status --porcelain) {
    git add pom.xml
    git commit -m "Release $Version"
}

git tag -a $tag -m "Release $Version"

if (-not $NoPush) {
    git push origin main
    git push origin $tag
}

Write-Host "Release $tag is ready."
Write-Host "Jar size: $jarSize bytes."
if ($NoPush) {
    Write-Host "Push skipped. Run: git push origin main; git push origin $tag"
} else {
    Write-Host "GitHub Actions will publish GitHub, Hangar, and Modrinth."
}
