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
    throw "Version must look like 1.0.15."
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

$propertiesPath = Join-Path $root 'gradle.properties'
$properties = Get-Content $propertiesPath
$currentVersionLine = $properties | Where-Object { $_ -match '^pluginVersion=' } | Select-Object -First 1
if (-not $currentVersionLine) {
    throw "gradle.properties does not contain pluginVersion."
}
$currentVersion = $currentVersionLine.Substring('pluginVersion='.Length)
if ($currentVersion -ne $Version) {
    $properties = $properties | ForEach-Object {
        if ($_ -match '^pluginVersion=') {
            "pluginVersion=$Version"
        } else {
            $_
        }
    }
    Set-Content -Path $propertiesPath -Value $properties -Encoding UTF8
}

$paperHeaders = @{
    'User-Agent' = 'Riqqqque/RuinedCollections (https://github.com/Riqqqque/RuinedCollections)'
}
$paperBuild = (Invoke-RestMethod 'https://fill.papermc.io/v3/projects/paper/versions/26.2/builds' -Headers $paperHeaders)[0]
$paperApiVersion = "26.2.build.$($paperBuild.id)-$($paperBuild.channel.ToLowerInvariant())"
.\gradlew.bat --no-daemon "-PpaperApiVersion=$paperApiVersion" clean compileJava
.\gradlew.bat --no-daemon clean build

$jar = Join-Path $root 'build\libs\RuinedCollections.jar'
if (-not (Test-Path $jar)) {
    throw "Build did not create build\libs\RuinedCollections.jar."
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
    git add gradle.properties
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
