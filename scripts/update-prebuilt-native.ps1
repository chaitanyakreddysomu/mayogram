# Copies the native library produced by a full build (with -PbuildNative) into
# TMessagesProj/jniLibs/<abi>/, stripped, so later builds can skip the
# CMake/NDK step entirely. Re-run this only after changing code under
# TMessagesProj/jni/ and rebuilding with -PbuildNative.
#
#   powershell -ExecutionPolicy Bypass -File scripts\update-prebuilt-native.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$lib = 'libtmessages.49.so'

$sdkLine = Get-Content "$root\local.properties" | Where-Object { $_ -match '^sdk.dir=' }
$sdk = ($sdkLine -replace '^sdk.dir=', '') -replace '\\\\', '\' -replace '\\:', ':'
$ndkVersion = ([regex]::Match((Get-Content "$root\TMessagesProj\build.gradle" -Raw), 'ndkVersion "([^"]+)"')).Groups[1].Value
$strip = "$sdk\ndk\$ndkVersion\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe"
if (-not (Test-Path $strip)) { throw "llvm-strip not found at $strip" }

$cxx = "$root\TMessagesProj\build\intermediates\cxx"
$outRoot = "$root\TMessagesProj\jniLibs"

foreach ($abi in 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64') {
    # Newest build of this ABI wins, preferring RelWithDebInfo over Debug on ties.
    $src = Get-ChildItem $cxx -Recurse -Filter $lib -ErrorAction SilentlyContinue |
        Where-Object { $_.Directory.Name -eq $abi } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $src) {
        Write-Host "$abi : no build found, skipped"
        continue
    }
    $outDir = "$outRoot\$abi"
    New-Item -ItemType Directory -Force $outDir | Out-Null
    & $strip --strip-unneeded -o "$outDir\$lib" $src.FullName
    if ($LASTEXITCODE -ne 0) { throw "llvm-strip failed for $abi" }
    $mb = [math]::Round((Get-Item "$outDir\$lib").Length / 1MB, 1)
    Write-Host "$abi : $mb MB  (from $($src.FullName.Replace($root + '\', '')))"
}
