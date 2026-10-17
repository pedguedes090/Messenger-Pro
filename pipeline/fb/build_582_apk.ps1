param(
    [string]$ApkPath = (Join-Path $PSScriptRoot '..\..\apkdebug\com.facebook.katana_582.0.0.50.54-475417168_minAPI30(arm64-v8a)(560,640dpi)_apkmirror.com.apk'),
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\..\app\build\outputs\fb\Facebook-582-patched.apk'),
    [string]$DeviceSerial,
    [switch]$Install
)

$ErrorActionPreference = 'Stop'
$dexTool = Join-Path $PSScriptRoot 'patch_dex/patch_dex.sh'
$zipTool = Join-Path $PSScriptRoot 'patch_582_apk.py'
$gitBash = 'C:\Program Files\Git\bin\bash.exe'
$pythonCommand = Get-Command python.exe -ErrorAction SilentlyContinue
$pythonExe = if ($pythonCommand) { $pythonCommand.Source } else { 'C:\Users\dun\AppData\Local\Programs\Python\Python311\python.exe' }
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Users\dun\AppData\Local\Android\Sdk' }
$buildTools = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Directory |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$zipalign = Join-Path $buildTools.FullName 'zipalign.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$keystore = Join-Path $env:USERPROFILE '.android\debug.keystore'

foreach ($required in @($ApkPath, $dexTool, $zipTool, $gitBash, $pythonExe, $zipalign, $apksigner, $keystore)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required input/tool missing: $required" }
}

$work = Join-Path $env:TEMP ("mpro-facebook-582-build-" + [Guid]::NewGuid())
$rawDex = Join-Path $work 'dex'
$patchedDex = Join-Path $work 'patched'
$unsigned = Join-Path $work 'Facebook-582-unsigned.apk'
$aligned = Join-Path $work 'Facebook-582-aligned.apk'
$signed = [System.IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Path $rawDex, $patchedDex, (Split-Path -Parent $signed) -Force | Out-Null

& $pythonExe $zipTool extract --apk (Resolve-Path -LiteralPath $ApkPath).Path --outdir $rawDex
if ($LASTEXITCODE -ne 0) { throw "Failed to extract target DEX files ($LASTEXITCODE)" }

function Convert-ToMsysPath([string]$path) {
    $p = [System.IO.Path]::GetFullPath($path).Replace('\', '/')
    if ($p -match '^([A-Za-z]):/(.*)$') { return "/$($matches[1].ToLower())/$($matches[2])" }
    return $p
}

function Invoke-Facebook582Patch([string]$name, [string]$expected) {
    $input = Join-Path $rawDex $name
    $output = Join-Path $patchedDex $name
    $cmd = "'$(Convert-ToMsysPath $dexTool)' ads '$(Convert-ToMsysPath $input)' '$(Convert-ToMsysPath $output)' facebook-582"
    $result = & $gitBash -lc $cmd 2>&1
    if ($LASTEXITCODE -ne 0) { throw "DEX patch failed for $name`n$($result -join "`n")" }
    $text = $result -join "`n"
    if ($text -notmatch [regex]::Escape($expected)) { throw "Resolver did not confirm $name target`n$text" }
    if (-not (Test-Path -LiteralPath $output)) { throw "Patched DEX was not written: $output" }
    Write-Output $text
}

Invoke-Facebook582Patch 'classes2.dex' 'feedEdge=1 shortsPool=0 sponsoredSelector=1 videoAdRequest=0 storyAdFetch=0'
Invoke-Facebook582Patch 'classes4.dex' 'feedEdge=0 shortsPool=1 sponsoredSelector=0 videoAdRequest=0 storyAdFetch=0'
Invoke-Facebook582Patch 'classes7.dex' 'feedEdge=0 shortsPool=0 sponsoredSelector=0 videoAdRequest=0 storyAdFetch=1'
Invoke-Facebook582Patch 'classes13.dex' 'feedEdge=0 shortsPool=0 sponsoredSelector=0 videoAdRequest=1 storyAdFetch=0'

& $pythonExe $zipTool replace --apk (Resolve-Path -LiteralPath $ApkPath).Path --patch-dir $patchedDex --out $unsigned
if ($LASTEXITCODE -ne 0) { throw "Failed to assemble patched APK ($LASTEXITCODE)" }

& $zipalign -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed ($LASTEXITCODE)" }

& $apksigner sign --ks $keystore --ks-pass 'pass:android' --ks-key-alias androiddebugkey `
    --key-pass 'pass:android' --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed ($LASTEXITCODE)" }

& $apksigner verify --verbose --print-certs $signed
if ($LASTEXITCODE -ne 0) { throw "apksigner verification failed ($LASTEXITCODE)" }
Write-Output "SIGNED_APK=$signed"
Write-Output "APK_SIZE=$((Get-Item -LiteralPath $signed).Length)"

if ($Install) {
    if (-not $DeviceSerial) { throw '-DeviceSerial is required with -Install' }
    $adb = Join-Path $sdkRoot 'platform-tools/adb.exe'
    & $adb -s $DeviceSerial install -r $signed
    if ($LASTEXITCODE -ne 0) { throw "adb install failed ($LASTEXITCODE)" }
    & $adb -s $DeviceSerial shell monkey -p com.facebook.katana 1
    if ($LASTEXITCODE -ne 0) { throw "Failed to launch Facebook after install ($LASTEXITCODE)" }
}
