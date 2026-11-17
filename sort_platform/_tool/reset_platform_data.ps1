param(
  [string]$Root = (Split-Path -Parent $PSScriptRoot),
  [int]$Port = 8080,
  [switch]$NoRestart,
  [switch]$Force
)

$ErrorActionPreference = "Stop"

function Step($m) { Write-Host "[reset] $m" }

$data = Join-Path $Root "data"
if (-not (Test-Path -LiteralPath $data)) {
    throw "data folder not found: $data"
}

# Keep the supervisor password (and judge password if one was set) so the
# reset platform can still be opened. Everything else is wiped.
$preserve = @{}
$filesToRead = @(
    (Join-Path $data "state.tsv"),
    (Join-Path $data "state\users.tsv")
)
foreach ($file in $filesToRead) {
    if (-not (Test-Path -LiteralPath $file)) { continue }
    foreach ($line in Get-Content -LiteralPath $file -Encoding UTF8) {
        if ($line.StartsWith("#") -or [string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split "`t", 2
        if ($parts.Count -eq 2 -and ($parts[0] -eq "super.pass" -or $parts[0] -eq "judge.pass")) {
            $preserve[$parts[0]] = $parts[1]
        }
    }
}
if (-not $preserve.ContainsKey("super.pass")) {
    $preserve["super.pass"] = "super2026"
}

if (-not $Force) {
    Write-Host "This will clear scores, comments, cases, roles, registrations and contest state."
    Write-Host "A full backup will be created first. Type RESET to continue."
    $answer = Read-Host
    if ($answer -ne "RESET") {
        Write-Host "Cancelled."
        exit 1
    }
} else {
    Write-Host "Starting reset in 3 seconds. Press Ctrl+C to cancel."
    Start-Sleep -Seconds 3
}

$pids = @()
try {
    $pids = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique
} catch {}
if (-not $pids -or $pids.Count -eq 0) {
    $netstat = Join-Path $env:SystemRoot "System32\netstat.exe"
    if (Test-Path -LiteralPath $netstat) {
        $lines = & $netstat -ano | Select-String "LISTENING" | Select-String ":$Port "
        foreach ($line in $lines) {
            $parts = ($line.ToString().Trim() -split " +")
            if ($parts.Count -ge 5) { $pids += [int]$parts[-1] }
        }
        $pids = $pids | Select-Object -Unique
    }
}
foreach ($procId in $pids) {
    Step "stopping PID $procId on port $Port"
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2
$still = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($still) {
    throw "Port $Port is still in use; reset aborted."
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backup = Join-Path $Root "data.reset-backup-$stamp"
$n = 1
while (Test-Path -LiteralPath $backup) {
    $backup = Join-Path $Root "data.reset-backup-$stamp-$n"
    $n++
}
Step "moving data to $backup"
Move-Item -LiteralPath $data -Destination $backup
New-Item -ItemType Directory -Path $data -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $data "state") -Force | Out-Null

$utf8 = New-Object System.Text.UTF8Encoding($false)
$header = "#key`tvalue`n"
$stateFiles = @{
    (Join-Path $data "state.tsv") = $header
    (Join-Path $data "state\contest.tsv") = $header
    (Join-Path $data "state\punish.tsv") = $header
}
$users = $header
foreach ($key in @("super.pass", "judge.pass")) {
    if ($preserve.ContainsKey($key)) {
        $users += "$key`t$($preserve[$key])`n"
    }
}
$stateFiles[(Join-Path $data "state\users.tsv")] = $users
foreach ($path in $stateFiles.Keys) {
    [IO.File]::WriteAllText($path, $stateFiles[$path], $utf8)
}

Step "data cleared"
Step "kept: $($preserve.Keys -join ', ')"
Step "backup: $backup"

if (-not $NoRestart) {
    $start = Join-Path $Root "start.bat"
    if (-not (Test-Path -LiteralPath $start)) {
        throw "start.bat not found: $start"
    }
    Step "starting service"
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "start.bat" -WorkingDirectory $Root
    $ok = $false
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
        try {
            $r = Invoke-RestMethod -Uri "http://localhost:$Port/api/contest" -TimeoutSec 2
            $ok = $true
            break
        } catch {}
    }
    if ($ok) {
        Step "service is up"
        $r | ConvertTo-Json -Compress
    } else {
        Step "service did not answer yet; check the start.bat window"
    }
} else {
    Step "-NoRestart set; service not started"
}
