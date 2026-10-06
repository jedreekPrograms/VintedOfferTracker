param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$playwrightDir = $PSScriptRoot
$repoRoot = Split-Path -Parent $playwrightDir
$privateRoot = Join-Path $repoRoot ".private"
$coreDir = Join-Path $privateRoot "flipbot-playwright-core"
$marker = Join-Path $playwrightDir ".private-core.enabled"
$coreRepository = "https://github.com/jedreekPrograms/flipbot-playwright-core.git"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git is required to bootstrap the private Playwright core."
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven is required to bootstrap the private Playwright core."
}

New-Item -ItemType Directory -Force -Path $privateRoot | Out-Null

if (-not (Test-Path (Join-Path $coreDir ".git"))) {
    Write-Host "Cloning private FlipBot Playwright core..."
    & git clone $coreRepository $coreDir
    if ($LASTEXITCODE -ne 0) {
        throw "Could not clone the private core. Make sure this Windows/Git session has access to the private GitHub repository."
    }
} else {
    Write-Host "Updating private FlipBot Playwright core..."
    & git -C $coreDir fetch origin main
    if ($LASTEXITCODE -ne 0) {
        throw "Could not fetch the private core."
    }

    & git -C $coreDir checkout main
    if ($LASTEXITCODE -ne 0) {
        throw "Could not checkout private core main."
    }

    & git -C $coreDir pull --ff-only origin main
    if ($LASTEXITCODE -ne 0) {
        throw "Could not fast-forward private core main."
    }
}

if (-not $SkipTests) {
    Write-Host "Testing private core..."
    & mvn -f (Join-Path $coreDir "pom.xml") -B test
    if ($LASTEXITCODE -ne 0) {
        throw "Private core tests failed. Public runtime was not enabled."
    }
}

Write-Host "Installing private core into the local Maven repository..."
& mvn -f (Join-Path $coreDir "pom.xml") -B -DskipTests install
if ($LASTEXITCODE -ne 0) {
    throw "Could not install the private core into the local Maven repository."
}

New-Item -ItemType File -Force -Path $marker | Out-Null

Write-Host ""
Write-Host "Private Playwright core is ready."
Write-Host "Marker: $marker"
Write-Host "Reload/reimport the playwright Maven project in your IDE."
Write-Host "You can now run mvn test or FlipBotPlaywrightApplication normally."
