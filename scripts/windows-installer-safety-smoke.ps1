param(
    [string]$InputDirectory = "build\install\PinDB\lib",
    [string]$AssociationsFile = "packaging\pindb-file-associations.properties"
)

$ErrorActionPreference = "Stop"

$upgradeUuid = "333e60e6-ca34-4411-9fce-6b1d6c8ff2c9"
$installDirectory = "Programs\PinDB"
$appDirectory = Join-Path $env:LOCALAPPDATA $installDirectory
$inputPath = (Resolve-Path $InputDirectory).Path
$associationsPath = (Resolve-Path $AssociationsFile).Path
$mainJars = @(Get-ChildItem $inputPath -Filter "pindb-*.jar" -File)

if ($mainJars.Count -ne 1) {
    throw "Expected exactly one PinDB main JAR in $inputPath; found $($mainJars.Count)."
}

$root = Join-Path $env:RUNNER_TEMP ("pindb-installer-safety-" + [Guid]::NewGuid().ToString("N"))
$baselineDirectory = Join-Path $root "baseline"
$upgradeDirectory = Join-Path $root "upgrade"
New-Item -ItemType Directory -Force -Path $baselineDirectory, $upgradeDirectory | Out-Null

$documentsDirectory = [Environment]::GetFolderPath("MyDocuments")
$documentsSentinel = Join-Path $documentsDirectory ("pindb-installer-safety-" + [Guid]::NewGuid().ToString("N") + ".txt")
$localAppDataSentinel = Join-Path $env:LOCALAPPDATA ("pindb-installer-safety-" + [Guid]::NewGuid().ToString("N") + ".txt")
Set-Content -Path $documentsSentinel -Value "PinDB installer must never remove this user document." -Encoding UTF8
Set-Content -Path $localAppDataSentinel -Value "PinDB installer must never remove this parent-directory file." -Encoding UTF8

function Assert-Sentinels {
    if (-not (Test-Path $documentsSentinel -PathType Leaf)) {
        throw "Windows installer safety failure: Documents sentinel was removed."
    }
    if (-not (Test-Path $localAppDataSentinel -PathType Leaf)) {
        throw "Windows installer safety failure: LocalAppData parent sentinel was removed."
    }
}

function Build-TestMsi {
    param(
        [Parameter(Mandatory = $true)][string]$Version,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    $jpackageArgs = @(
        "--type", "msi",
        "--dest", $Destination,
        "--input", $inputPath,
        "--name", "PinDB",
        "--app-version", $Version,
        "--vendor", "PinDB Project",
        "--description", "A flexible personal database application.",
        "--main-jar", $mainJars[0].Name,
        "--main-class", "org.pindb.PinDBLauncher",
        "--win-per-user-install",
        "--install-dir", $installDirectory,
        "--win-upgrade-uuid", $upgradeUuid,
        "--win-menu",
        "--win-menu-group", "PinDB",
        "--file-associations", $associationsPath,
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Dprism.order=es2,sw"
    )

    & jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed while building Windows installer safety MSI version $Version."
    }

    $packages = @(Get-ChildItem $Destination -Filter "*.msi" -File)
    if ($packages.Count -ne 1) {
        throw "Expected exactly one MSI for version $Version; found $($packages.Count)."
    }
    return $packages[0].FullName
}

function Invoke-Msi {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$ExpectFailure
    )

    $process = Start-Process -FilePath "msiexec.exe" -ArgumentList $Arguments -Wait -PassThru
    $success = $process.ExitCode -eq 0 -or $process.ExitCode -eq 3010

    if ($ExpectFailure) {
        if ($success) {
            throw "Expected Windows Installer to reject the deliberately invalid upgrade package."
        }
        return
    }

    if (-not $success) {
        throw "Windows Installer exited with code $($process.ExitCode): $($Arguments -join ' ')"
    }
}

$baselineMsi = $null
$upgradeMsi = $null

try {
    if (Test-Path $appDirectory) {
        throw "Safety smoke test requires a clean application directory, but $appDirectory already exists."
    }

    $baselineMsi = Build-TestMsi -Version "0.3.200" -Destination $baselineDirectory
    $upgradeMsi = Build-TestMsi -Version "0.3.201" -Destination $upgradeDirectory

    Invoke-Msi -Arguments @("/i", $baselineMsi, "/qn", "/norestart")
    Assert-Sentinels

    $launcher = Join-Path $appDirectory "PinDB.exe"
    if (-not (Test-Path $launcher -PathType Leaf)) {
        throw "PinDB was not installed into the isolated application directory: $launcher"
    }

    $invalidUpgrade = Join-Path $root "invalid-upgrade.msi"
    Set-Content -Path $invalidUpgrade -Value "This is intentionally not a valid MSI." -Encoding ASCII
    Invoke-Msi -Arguments @("/i", $invalidUpgrade, "/qn", "/norestart") -ExpectFailure
    Assert-Sentinels

    if (-not (Test-Path $launcher -PathType Leaf)) {
        throw "A failed upgrade attempt removed the existing PinDB installation."
    }

    Invoke-Msi -Arguments @("/i", $upgradeMsi, "/qn", "/norestart")
    Assert-Sentinels

    if (-not (Test-Path $launcher -PathType Leaf)) {
        throw "PinDB was missing after the upgrade."
    }

    Invoke-Msi -Arguments @("/x", $upgradeMsi, "/qn", "/norestart")
    Assert-Sentinels

    if (Test-Path $launcher -PathType Leaf) {
        throw "PinDB launcher remained after uninstall."
    }
}
finally {
    if ($upgradeMsi -and (Test-Path $upgradeMsi)) {
        try { Invoke-Msi -Arguments @("/x", $upgradeMsi, "/qn", "/norestart") } catch { }
    }
    if ($baselineMsi -and (Test-Path $baselineMsi)) {
        try { Invoke-Msi -Arguments @("/x", $baselineMsi, "/qn", "/norestart") } catch { }
    }
    Remove-Item $documentsSentinel -Force -ErrorAction SilentlyContinue
    Remove-Item $localAppDataSentinel -Force -ErrorAction SilentlyContinue
    Remove-Item $root -Recurse -Force -ErrorAction SilentlyContinue
}
