param(
    [int]$ActionCode = -1,
    [string]$ActionCodes = '',
    [bool]$Capture = $true,
    [bool]$ForceBlock = $false,
    [bool]$FeatureEnabled = $true
)

$ErrorActionPreference = 'Stop'

$remotePref = '/data/user/0/com.facebook.orca/shared_prefs/mpro_pref.xml'
$remoteBackup = '/data/user/0/com.facebook.orca/shared_prefs/mpro_pref.xml.codexbak'
$localPref = Join-Path ([System.IO.Path]::GetTempPath()) 'mpro_pref_device.xml'
$localBackup = Join-Path ([System.IO.Path]::GetTempPath()) 'mpro_pref_device_backup.xml'

adb exec-out "su -c 'cat $remotePref'" > $localPref

$text = Get-Content $localPref -Raw
if ($text -notmatch '<map>') {
    Write-Host 'Primary pref XML invalid, loading backup...'
    adb exec-out "su -c 'cat $remoteBackup'" > $localBackup
    $text = Get-Content $localBackup -Raw
}

[xml]$doc = $text
$root = $doc.map
if (-not $root) {
    throw 'Unable to parse SharedPreferences XML: <map> root missing.'
}

$normalizedList = @()
if (-not [string]::IsNullOrWhiteSpace($ActionCodes)) {
    $normalizedList = $ActionCodes.Split(',') |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -match '^\d+$' } |
        Select-Object -Unique
}

if ($normalizedList.Count -eq 0 -and $ActionCode -gt 0) {
    $normalizedList = @([string]$ActionCode)
}

if ($normalizedList.Count -gt 0) {
    $ActionCode = [int]::Parse($normalizedList[0])
}

$csvCodes = [string]::Join(',', $normalizedList)

function Remove-PrefNodesByName {
    param([string]$Name)

    $nodes = @($root.SelectNodes("*[@name='$Name']"))
    foreach ($node in $nodes) {
        $null = $root.RemoveChild($node)
    }
}

function Add-BoolNode {
    param([string]$Name, [bool]$Value)

    $node = $doc.CreateElement('boolean')
    $null = $node.SetAttribute('name', $Name)
    $null = $node.SetAttribute('value', $Value.ToString().ToLowerInvariant())
    $null = $root.AppendChild($node)
}

function Add-IntNode {
    param([string]$Name, [int]$Value)

    $node = $doc.CreateElement('int')
    $null = $node.SetAttribute('name', $Name)
    $null = $node.SetAttribute('value', [string]$Value)
    $null = $root.AppendChild($node)
}

function Add-StringNode {
    param([string]$Name, [string]$Value)

    $node = $doc.CreateElement('string')
    $null = $node.SetAttribute('name', $Name)
    $node.InnerText = $Value
    $null = $root.AppendChild($node)
}

$managedKeys = @(
    'mpro_conversation_presence_status',
    'mpro_debug_presence_capture',
    'mpro_debug_presence_force_block',
    'mpro_debug_presence_action_code',
    'mpro_debug_presence_action_codes'
)

foreach ($key in $managedKeys) {
    Remove-PrefNodesByName -Name $key
}

Add-BoolNode -Name 'mpro_conversation_presence_status' -Value $FeatureEnabled
Add-BoolNode -Name 'mpro_debug_presence_capture' -Value $Capture
Add-BoolNode -Name 'mpro_debug_presence_force_block' -Value $ForceBlock
Add-IntNode -Name 'mpro_debug_presence_action_code' -Value $ActionCode
Add-StringNode -Name 'mpro_debug_presence_action_codes' -Value $csvCodes

$writerSettings = New-Object System.Xml.XmlWriterSettings
$writerSettings.Indent = $true
$writerSettings.OmitXmlDeclaration = $false
$writerSettings.Encoding = New-Object System.Text.UTF8Encoding($false)
$writer = [System.Xml.XmlWriter]::Create($localPref, $writerSettings)
$doc.Save($writer)
$writer.Dispose()

adb push $localPref /sdcard/Download/mpro_pref.xml | Out-Null
adb shell "su -c 'cp /sdcard/Download/mpro_pref.xml $remotePref; chown u0_a486:u0_a486 $remotePref; chmod 660 $remotePref'"
adb shell "su -c 'grep -n mpro_conversation_presence_status $remotePref; grep -n mpro_debug_presence $remotePref'"

Write-Host ("Presence preference keys updated successfully. actionCode={0} actionCodes={1}" -f $ActionCode, $csvCodes)
