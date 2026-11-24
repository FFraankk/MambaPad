# Runs only on the disposable GitHub runner; no installer is executed.
$ErrorActionPreference = 'Stop'
$toolRoot = Join-Path $env:RUNNER_TEMP 'mambapad-tools'
if (Test-Path -LiteralPath $toolRoot) { throw 'Packaging tools directory already exists' }
New-Item -ItemType Directory -Path $toolRoot | Out-Null
function Get-VerifiedFile($Url, $Name, $SHA256) {
    $file = Join-Path $toolRoot $Name
    Invoke-WebRequest -Uri $Url -OutFile $file
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $SHA256) { throw "Tool hash differs: $Name" }
    return $file
}
$adb = Get-VerifiedFile 'https://dl.google.com/android/repository/platform-tools_r37.0.3-win.zip' 'adb.zip' '89d108360a3bf2924d3bea837239edaccc22fd6ff5befd3787c4348a9fe4f09d'
$inno = Get-VerifiedFile 'https://github.com/jrsoftware/issrc/releases/download/is-6_7_4/innosetup-6.7.4.exe' 'inno.exe' 'a549929319f9120716677c685535ca4671e4b937b5fdc87e2585f32d8eb30e8c'
$unpacker = Get-VerifiedFile 'https://github.com/jrathlev/InnoUnpacker-Windows-GUI/releases/download/ui_2_2_14/innounp-2.zip' 'unpacker.zip' '078ddb005aa17e669f0b5545980bff92200f7401418174172dca0d75f9e5d8f0'
Expand-Archive -LiteralPath $adb -DestinationPath (Join-Path $toolRoot 'adb')
Expand-Archive -LiteralPath $unpacker -DestinationPath (Join-Path $toolRoot 'unpacker')
$exe = Get-ChildItem -LiteralPath (Join-Path $toolRoot 'unpacker') -Filter innounp.exe -Recurse
if (@($exe).Count -ne 1) { throw 'Expected one Inno unpacker' }
& $exe.FullName -x -y "-d$(Join-Path $toolRoot 'inno')" $inno
if ($LASTEXITCODE) { throw 'Inno compiler extraction failed' }
if (-not (Test-Path -LiteralPath (Join-Path $toolRoot 'inno/{app}/ISCC.exe'))) { throw 'Missing ISCC compiler' }
