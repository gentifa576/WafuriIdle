Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $RootDir "backend"
$FrontendDir = Join-Path $RootDir "frontend"
$ClientPidFile = Join-Path $FrontendDir ".playtest-client.pid"
$BackendGradleUserHome = Join-Path $BackendDir ".gradle-user-home"

function Stop-TrackedClientIfRunning {
  if (-not (Test-Path $ClientPidFile)) {
    Write-Host "No tracked frontend dev server found."
    return
  }

  $pidValue = (Get-Content $ClientPidFile -Raw).Trim()
  if ($pidValue) {
    $clientProcess = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
    if ($null -ne $clientProcess) {
      Write-Host "Stopping frontend dev server (PID $pidValue)..."
      Stop-Process -Id $clientProcess.Id -Force -ErrorAction SilentlyContinue
    } else {
      Write-Host "Tracked frontend dev server is not running."
    }
  }

  Remove-Item $ClientPidFile -Force -ErrorAction SilentlyContinue
}

Stop-TrackedClientIfRunning

Write-Host "Stopping backend..."
$env:GRADLE_USER_HOME = $BackendGradleUserHome
& (Join-Path $BackendDir "gradlew.bat") stopServer | Out-Null

Write-Host "Local playtest services stopped."
