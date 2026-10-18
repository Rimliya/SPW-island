param(
    [string]$Path
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

if (-not $Path) {
    $packages = @(Get-ChildItem (Join-Path $root "build/distributions") -Filter "*.spmod" -File)
    if ($packages.Count -ne 1) { throw "Expected exactly one .spmod, found $($packages.Count)" }
    $Path = $packages[0].FullName
}
if (-not (Test-Path -LiteralPath $Path)) { throw "Package not found: $Path" }

$versionLine = Get-Content (Join-Path $root "gradle.properties") |
    Where-Object { $_ -match '^version=(.+)$' } | Select-Object -First 1
if (-not $versionLine) { throw "version is missing from gradle.properties" }
$version = [regex]::Match($versionLine, '^version=(.+)$').Groups[1].Value.Trim()

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
try {
    $entries = @{}
    foreach ($entry in $archive.Entries) { $entries[$entry.FullName] = $entry }

    function Read-Entry([string]$name) {
        $entry = $entries[$name]
        if (-not $entry) { throw "Missing package entry: $name" }
        $stream = $entry.Open()
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $true)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose(); $stream.Dispose() }
    }

    $preferences = Read-Entry "classes/preference_config.json" | ConvertFrom-Json
    $configNames = @($preferences.configs | ForEach-Object { $_.config })
    if ($configNames -notcontains "island.json") { throw "Merged preferences are missing island.json" }

    $extensionClass = "io.github.gaboron.spwisland.host.IslandPlaybackExtension"
    $extensionIndex = (Read-Entry "classes/META-INF/extensions.idx").Trim()
    if ($extensionIndex -ne $extensionClass) {
        throw "Unexpected extensions.idx: $extensionIndex"
    }

    $manifest = Read-Entry "classes/META-INF/MANIFEST.MF"
    $expectedManifest = [ordered]@{
        "Plugin-Class" = "io.github.gaboron.spwisland.host.IslandPlugin"
        "Plugin-Id" = "io.github.gaboron.spwisland"
        "Plugin-Version" = $version
        "Plugin-Provider" = "GaBoron / Solitaire"
        "Plugin-Open-Source-Url" = "https://github.com/Rimliya/SPW-island"
    }
    foreach ($key in $expectedManifest.Keys) {
        $pattern = "(?m)^$([regex]::Escape($key)): $([regex]::Escape($expectedManifest[$key]))\r?$"
        if ($manifest -notmatch $pattern) { throw "Manifest does not contain $key=$($expectedManifest[$key])" }
    }

    $sourceProperties = Read-Entry "classes/project.properties"
    if ($sourceProperties -notmatch "source\.url=https://github\.com/Rimliya/SPW-island") {
        throw "project.properties does not point at the fork repository"
    }

    $requiredEntries = @(
        "classes/fonts/PingFang.ttc",
        "classes/fonts/MiSansVF.ttf",
        "classes/native/spw-spectrum.exe",
        "classes/native/spw-island-tray.exe",
        "classes/native/spw-island-glass.dll",
        "classes/native/spw-spout.dll",
        "classes/native/compose/skiko-windows-x64.dll",
        "classes/native/island-linux.py",
        "classes/native/island-pointer.py",
        "classes/native/island-spectrum.py",
        "classes/native/install-gnome-pointer.py",
        "classes/native/spw-island-pointer@gaboron.github.io.shell-extension.zip"
    )
    foreach ($required in $requiredEntries) {
        if (-not $entries.ContainsKey($required)) { throw "Missing package entry: $required" }
    }

    $linuxSkiko = @($entries.Keys | Where-Object { $_ -match "^lib/skiko-awt-runtime-linux-x64-.+\.jar$" })
    if ($linuxSkiko.Count -eq 0) { throw "Package must bundle the Linux x64 Skiko runtime" }

    "PASS: $Path"
}
finally {
    $archive.Dispose()
}