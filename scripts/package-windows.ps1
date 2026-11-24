param(
    [string]$GoExe = 'go',
    [Parameter(Mandatory = $true)][string]$AdbDirectory,
    [Parameter(Mandatory = $true)][string]$GoLicense,
    [Parameter(Mandatory = $true)][string]$ISCC
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$gradle = Get-Content -LiteralPath (Join-Path $projectRoot 'app/build.gradle') -Raw
$version = [regex]::Match($gradle, 'versionName "([^\"]+)"').Groups[1].Value
if (-not $version -or $version -notmatch '^[A-Za-z0-9.-]+$') { throw 'Invalid versionName' }
$outputRoot = Join-Path $projectRoot "dist/windows-$version"
if (Test-Path -LiteralPath $outputRoot) { throw "Output already exists: $outputRoot. Use a fresh version or move the previous output aside." }
foreach ($file in @('adb.exe','AdbWinApi.dll','AdbWinUsbApi.dll','NOTICE.txt','source.properties')) {
    if (-not (Test-Path -LiteralPath (Join-Path $AdbDirectory $file))) { throw "Missing ADB file: $file" }
}
foreach ($file in @($GoLicense,$ISCC)) { if (-not (Test-Path -LiteralPath $file)) { throw "Missing tool/license: $file" } }
$payload = Join-Path $outputRoot 'MambaPad'
$adbRevision = (Get-FileHash -LiteralPath (Join-Path $AdbDirectory 'adb.exe') -Algorithm SHA256).Hash.Substring(0,12).ToLowerInvariant()
$adbPayload = Join-Path $payload "tools/adb/$adbRevision"
New-Item -ItemType Directory -Path $adbPayload -Force | Out-Null
Push-Location (Join-Path $projectRoot 'windows-receiver')
try {
    & $GoExe test ./...
    if ($LASTEXITCODE) { throw 'Go tests failed' }
    & $GoExe vet ./...
    if ($LASTEXITCODE) { throw 'Go vet failed' }
    & $GoExe build -trimpath '-ldflags=-H=windowsgui -s -w' -o (Join-Path $payload 'MambaPad.exe') .
    if ($LASTEXITCODE) { throw 'Go build failed' }
} finally { Pop-Location }
foreach ($file in @('adb.exe','AdbWinApi.dll','AdbWinUsbApi.dll','NOTICE.txt','source.properties')) {
    Copy-Item -LiteralPath (Join-Path $AdbDirectory $file) -Destination $adbPayload
}
foreach ($file in @('LICENSE','NOTICE.txt')) { Copy-Item -LiteralPath (Join-Path $projectRoot $file) -Destination $payload }
Copy-Item -LiteralPath $GoLicense -Destination (Join-Path $payload 'GO-LICENSE.txt')
Copy-Item -LiteralPath (Join-Path $projectRoot 'packaging/windows/README.txt') -Destination $payload
Set-Content -LiteralPath (Join-Path $payload 'adb-path.txt') -Value "tools/adb/$adbRevision/adb.exe" -Encoding ascii
Set-Content -LiteralPath (Join-Path $payload 'portable.flag') -Value '' -Encoding ascii
$zip = Join-Path $outputRoot "MambaPad-Windows-x64-$version-Portable.zip"
Compress-Archive -LiteralPath $payload -DestinationPath $zip
& $ISCC "/DAppVersion=$version" "/DPayloadDir=$payload" "/DOutputDir=$outputRoot" (Join-Path $projectRoot 'packaging/windows/MambaPad.iss')
if ($LASTEXITCODE) { throw 'Installer compilation failed' }
$installer = Join-Path $outputRoot "MambaPad-Windows-x64-$version-Setup.exe"
$hashes = foreach ($file in @($zip,$installer)) {
    $hash = Get-FileHash -LiteralPath $file -Algorithm SHA256
    "$(($hash.Hash).ToLowerInvariant())  $(Split-Path $file -Leaf)"
}
$hashes | Set-Content -LiteralPath (Join-Path $outputRoot 'SHA256SUMS.txt') -Encoding ascii
Write-Output "Packages ready: $outputRoot"
