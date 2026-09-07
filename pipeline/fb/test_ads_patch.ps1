param(
    [Parameter(Mandatory = $true)]
    [string]$DexRoot,
    [string]$OutputRoot = (Join-Path $env:TEMP 'mpro-ads-patch-test')
)

$ErrorActionPreference = 'Stop'
$patcher = Join-Path $PSScriptRoot 'patch_dex/patch_dex.sh'
$gitBash = 'C:\Program Files\Git\bin\bash.exe'
if (-not (Test-Path $patcher)) { throw "patcher missing: $patcher" }
if (-not (Test-Path $gitBash)) { throw "Git Bash missing: $gitBash" }
if (-not (Test-Path (Join-Path $DexRoot 'classes.dex'))) { throw "DEX root missing classes.dex: $DexRoot" }

Remove-Item $OutputRoot -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $OutputRoot | Out-Null

function Convert-ToMsysPath([string]$path) {
    $p = $path.Replace('\', '/')
    if ($p -match '^([A-Za-z]):/(.*)$') { return "/$($matches[1].ToLower())/$($matches[2])" }
    return $p
}

function Invoke-Patch([string]$name, [int]$expectedPatchedMethods) {
    $input = Join-Path $DexRoot $name
    $output = Join-Path $OutputRoot ($name -replace '\.dex$', '.patched.dex')
    $cmd = "'$(Convert-ToMsysPath $patcher)' ads '$(Convert-ToMsysPath $input)' '$(Convert-ToMsysPath $output)'"
    $result = & $gitBash -lc $cmd 2>&1
    if ($LASTEXITCODE -ne 0) { throw "patch failed for $name`n$result" }
    $text = ($result -join "`n")
    if ($text -notmatch 'patched methods: [1-9][0-9]*') {
        throw "no target method patched in $name`n$text"
    }
    $match = [regex]::Match($text, 'patched methods: (\d+)')
    if (-not $match.Success -or [int]$match.Groups[1].Value -ne $expectedPatchedMethods) {
        throw "unexpected patch count for $name; expected $expectedPatchedMethods`n$text"
    }
    Write-Output "$name`n$text"
}

Invoke-Patch 'classes.dex' 8
Invoke-Patch 'classes3.dex' 3
Invoke-Patch 'classes10.dex' 2
Invoke-Patch 'classes16.dex' 1
Write-Output 'ADS_PATCH_TEST=PASS'
