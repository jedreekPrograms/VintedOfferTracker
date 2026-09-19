$ErrorActionPreference = "Stop"

Write-Host "FlipBot remote access setup" -ForegroundColor Cyan
Write-Host "This configures private Tailscale access to the existing Spring Boot backend on localhost:8081."
Write-Host "No router port forwarding is used."
Write-Host ""

$tailscale = Get-Command tailscale -ErrorAction SilentlyContinue
if ($null -eq $tailscale) {
    Write-Host "Tailscale CLI was not found." -ForegroundColor Yellow
    Write-Host "Install Tailscale for Windows, sign in, then run this script again."
    exit 1
}

Write-Host "Checking Tailscale..."
& tailscale status
if ($LASTEXITCODE -ne 0) {
    Write-Host "Tailscale is installed but is not ready. Open Tailscale and sign in first." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Publishing FlipBot privately inside your tailnet..."
& tailscale serve --bg http://127.0.0.1:8081
if ($LASTEXITCODE -ne 0) {
    Write-Host "Could not configure Tailscale Serve." -ForegroundColor Red
    Write-Host "If Tailscale asks you to enable Serve/HTTPS, follow the shown link and run this script again."
    exit 1
}

Write-Host ""
Write-Host "Current Tailscale Serve configuration:" -ForegroundColor Green
& tailscale serve status

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host "Install/sign in to Tailscale on your phone using the same tailnet, then copy the HTTPS .ts.net address shown above into FlipBot Mobile."
Write-Host "The PC only needs the usual FlipBot backend + Playwright running; Tailscale runs in the background."
