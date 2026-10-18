param(
    [Parameter(Mandatory = $true)]
    [string]$DexRoot,
    [string]$OutputRoot = (Join-Path $env:TEMP ("mpro-ads-patch-582-test-" + [Guid]::NewGuid()))
)

$ErrorActionPreference = 'Stop'
$patcher = Join-Path $PSScriptRoot 'patch_dex/patch_dex.sh'
$gitBash = 'C:\Program Files\Git\bin\bash.exe'
if (-not (Test-Path $patcher)) { throw "patcher missing: $patcher" }
if (-not (Test-Path $gitBash)) { throw "Git Bash missing: $gitBash" }
foreach ($name in @('classes2.dex', 'classes4.dex', 'classes7.dex', 'classes13.dex')) {
    if (-not (Test-Path (Join-Path $DexRoot $name))) { throw "missing $name in $DexRoot" }
}

New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null

function Convert-ToMsysPath([string]$path) {
    $p = $path.Replace('\', '/')
    if ($p -match '^([A-Za-z]):/(.*)$') { return "/$($matches[1].ToLower())/$($matches[2])" }
    return $p
}

function Invoke-Patch([string]$name, [string]$expectedTargets, [string]$expectedCounts) {
    $input = Join-Path $DexRoot $name
    $output = Join-Path $OutputRoot ($name -replace '\.dex$', '.patched.dex')
    $cmd = "'$(Convert-ToMsysPath $patcher)' ads '$(Convert-ToMsysPath $input)' '$(Convert-ToMsysPath $output)' facebook-582"
    $result = & $gitBash -lc $cmd 2>&1
    if ($LASTEXITCODE -ne 0) { throw "patch failed for $name`n$result" }
    $text = ($result -join "`n")
    if ($text -notmatch [regex]::Escape($expectedTargets)) { throw "unexpected resolver results for $name`n$text" }
    if ($text -notmatch [regex]::Escape($expectedCounts)) { throw "unexpected patch counts for $name`n$text" }
    if (-not (Test-Path $output)) { throw "patched DEX missing: $output" }
    Write-Output "$name`n$text"
}

Invoke-Patch 'classes2.dex' 'affiliateProductOverlay=false brandedAffiliateProductOverlay=false feedReelPreserved=true' 'feedEdge=1 shortsPool=0 sponsoredSelector=1 videoAdRequest=0 storyAdFetch=0 affiliateOverlay=0 affiliateOverlayOutputsVerified=true'
Invoke-Patch 'classes4.dex' 'shortsPool=true' 'feedEdge=0 shortsPool=1 sponsoredSelector=0 videoAdRequest=0 storyAdFetch=0 affiliateOverlay=0 affiliateOverlayOutputsVerified=true'
Invoke-Patch 'classes7.dex' 'storyAdFetch=true' 'feedEdge=0 shortsPool=0 sponsoredSelector=0 videoAdRequest=0 storyAdFetch=1 affiliateOverlay=0 affiliateOverlayOutputsVerified=true'
Invoke-Patch 'classes13.dex' 'videoAdRequest=true storyAdFetch=false affiliateProductOverlay=true brandedAffiliateProductOverlay=true feedReelPreserved=true' 'feedEdge=0 shortsPool=0 sponsoredSelector=0 videoAdRequest=1 storyAdFetch=0 affiliateOverlay=2 affiliateOverlayOutputsVerified=true'
Write-Output 'FACEBOOK_582_ADS_PATCH_TEST=PASS'
