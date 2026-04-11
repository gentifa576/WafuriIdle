Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $RootDir "backend"
$FrontendDir = Join-Path $RootDir "frontend"
$BackendGradleUserHome = Join-Path $BackendDir ".gradle-user-home"
$BackendHealthUrl = "http://127.0.0.1:8080/health"
$ClientUrl = "http://127.0.0.1:5173"
$ClientPidFile = Join-Path $FrontendDir ".playtest-client.pid"
$ClientLogFile = Join-Path $FrontendDir ".playtest-client.log"

function Wait-ForUrl {
  param(
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $true)][string]$Name,
    [int]$Attempts = 60
  )

  for ($i = 0; $i -lt $Attempts; $i++) {
    try {
      Invoke-WebRequest -Uri $Url -UseBasicParsing | Out-Null
      return
    } catch {
      Start-Sleep -Seconds 1
    }
  }

  throw "$Name did not become ready in time: $Url"
}

function Stop-TrackedClientIfRunning {
  if (-not (Test-Path $ClientPidFile)) {
    return
  }

  $pidValue = (Get-Content $ClientPidFile -Raw).Trim()
  if ($pidValue) {
    $clientProcess = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
    if ($null -ne $clientProcess) {
      Write-Host "Stopping existing frontend dev server (PID $pidValue)..."
      Stop-Process -Id $clientProcess.Id -Force -ErrorAction SilentlyContinue
    }
  }

  Remove-Item $ClientPidFile -Force -ErrorAction SilentlyContinue
}

function Start-Client {
  $nodeModulesDir = Join-Path $FrontendDir "node_modules"
  if (-not (Test-Path $nodeModulesDir)) {
    throw "Frontend dependencies are missing. Run 'cd frontend; npm install' once, then rerun this script."
  }

  Stop-TrackedClientIfRunning

  Write-Host "Starting frontend dev server..."
  $frontendProcess = Start-Process `
    -FilePath "npm.cmd" `
    -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1", "--strictPort") `
    -WorkingDirectory $FrontendDir `
    -RedirectStandardOutput $ClientLogFile `
    -RedirectStandardError $ClientLogFile `
    -PassThru

  Set-Content -Path $ClientPidFile -Value $frontendProcess.Id

  try {
    Wait-ForUrl -Url $ClientUrl -Name "Frontend dev server"
  } catch {
    if (Test-Path $ClientLogFile) {
      Write-Host "Frontend log:"
      Get-Content $ClientLogFile -Tail 50
    }
    throw
  }
}

Write-Host "Stopping backend if it is already running..."
$env:GRADLE_USER_HOME = $BackendGradleUserHome
& (Join-Path $BackendDir "gradlew.bat") stopServer | Out-Null

Write-Host "Starting backend..."
& (Join-Path $BackendDir "gradlew.bat") runServer | Out-Null

Write-Host "Waiting for backend health check..."
Wait-ForUrl -Url $BackendHealthUrl -Name "Backend"

Start-Client

Write-Host ""
Write-Host "Local playtest is ready."
Write-Host "Open: $ClientUrl"
Write-Host "Frontend log: $ClientLogFile"
Write-Host "Backend log: $(Join-Path $BackendDir 'build/server/server.log')"
