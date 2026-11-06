param(
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [ValidateRange(1,5)][int]$Launches = 2
)

# Exercise the actual packaged Application/providers/Activity, with no test replacements or screenshots.
$ErrorActionPreference = 'Stop'
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$logDirectory = Join-Path $PSScriptRoot '..\build\release-smoke'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

function Invoke-Device {
    param([string[]]$Arguments, [switch]$AllowFailure)
    $result = & $Adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0 -and !$AllowFailure) {
        throw "adb failed: $Arguments`n$($result | Out-String)"
    }
    return ($result | Out-String).Trim()
}

if ((Invoke-Device -Arguments @('shell','getprop','sys.boot_completed')) -ne '1') {
    throw 'Emulator must finish booting before the startup check.'
}
Invoke-Device -Arguments @('install','-r',$apkPath) | Write-Output
Invoke-Device -Arguments @('shell','svc','power','stayon','true') | Out-Null
Invoke-Device -Arguments @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
Invoke-Device -Arguments @('shell','wm','dismiss-keyguard') | Out-Null

for ($attempt=1; $attempt -le $Launches; $attempt++) {
    Invoke-Device -Arguments @('shell','am','force-stop','com.freecyber.ion') | Out-Null
    Invoke-Device -Arguments @('logcat','-c') | Out-Null
    $launch = Invoke-Device -Arguments @('shell','am','start','-W','-n','com.freecyber.ion/.MainActivity')
    # am start can report Status: ok even if a provider immediately crashes the process.
    Start-Sleep -Seconds 5
    $processId = Invoke-Device -Arguments @('shell','pidof','com.freecyber.ion') -AllowFailure
    $crash = Invoke-Device -Arguments @('logcat','-b','crash','-d')
    $activities = Invoke-Device -Arguments @('shell','dumpsys','activity','activities')
    $resumed = ($activities -split "`n" | Where-Object { $_ -match '(mResumedActivity|topResumedActivity).*com\.freecyber\.ion/.*MainActivity' }) -join "`n"
    Set-Content -LiteralPath (Join-Path $logDirectory "launch-$attempt.txt") -Value $launch
    Set-Content -LiteralPath (Join-Path $logDirectory "crash-$attempt.txt") -Value $crash
    if (!$processId -or $crash -match 'Process: com\.freecyber\.ion[, ]') {
        throw "APK startup failed on launch $attempt. Crash log: $logDirectory\crash-$attempt.txt"
    }
    if (!$resumed) { throw "MainActivity did not resume on launch $attempt. Inspect $Serial without screenshots." }
    Write-Output "PASS launch $attempt/$Launches : process $processId alive after 5s, MainActivity resumed, no app crash."
}
