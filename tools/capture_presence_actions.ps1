param(
    [int]$Loops = 8,
    [int]$OpenSeconds = 16,
    [int]$BackgroundSeconds = 8,
    [switch]$ToggleWifi
)

$ErrorActionPreference = 'Stop'

Write-Host "Clearing logs and restarting Messenger..."
adb logcat -c | Out-Null
adb shell am force-stop com.facebook.orca | Out-Null

for ($i = 1; $i -le $Loops; $i++) {
    Write-Host ("Cycle {0}/{1}: launch" -f $i, $Loops)
    adb shell monkey -p com.facebook.orca -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds $OpenSeconds

    Write-Host ("Cycle {0}/{1}: background" -f $i, $Loops)
    adb shell input keyevent 3 | Out-Null
    Start-Sleep -Seconds $BackgroundSeconds
}

Write-Host "Final launch and settle..."
adb shell monkey -p com.facebook.orca -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 30

if ($ToggleWifi.IsPresent) {
    Write-Host "Toggling Wi-Fi..."
    adb shell svc wifi disable | Out-Null
    Start-Sleep -Seconds 10
    adb shell svc wifi enable | Out-Null
    Start-Sleep -Seconds 15
}

$allLogs = adb logcat -d
$presenceLines = $allLogs | Select-String 'PresenceCapture\[|PresenceStatusSentHook: blocked'

if (-not $presenceLines) {
    Write-Host "No PresenceCapture lines found."
    exit 0
}

Write-Host ""
Write-Host "=== Presence Lines ==="
$presenceLines | ForEach-Object { $_.Line }

$actions = $presenceLines |
    ForEach-Object {
        if ($_.Line -match 'action=(\d+)') { $matches[1] }
        elseif ($_.Line -match 'action=NA') { 'NA' }
    } |
    Where-Object { $_ -ne $null }

$tags = $presenceLines |
    ForEach-Object {
        if ($_.Line -match 'PresenceCapture\[([^\]]+)\]') { $matches[1] }
    } |
    Where-Object { $_ -ne $null }

$blockedCount = ($presenceLines | Select-String 'blocked ' | Measure-Object).Count

Write-Host ""
Write-Host "=== Action Counts ==="
if ($actions) {
    $actions | Group-Object | Sort-Object Count -Descending | ForEach-Object {
        Write-Host ("action={0} count={1}" -f $_.Name, $_.Count)
    }
} else {
    Write-Host "No action tokens parsed."
}

Write-Host ""
Write-Host "=== Tag Counts ==="
if ($tags) {
    $tags | Group-Object | Sort-Object Count -Descending | ForEach-Object {
        Write-Host ("tag={0} count={1}" -f $_.Name, $_.Count)
    }
} else {
    Write-Host "No capture tags parsed."
}

Write-Host ""
Write-Host ("Blocked events: {0}" -f $blockedCount)
