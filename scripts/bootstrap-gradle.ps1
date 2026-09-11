param([string]$Version = "8.14.3")
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Tools = Join-Path $Root ".tools"
$GradleHome = Join-Path $Tools "gradle-$Version"
$Gradle = Join-Path $GradleHome "bin\gradle.bat"
if (Test-Path $Gradle) { Write-Output $Gradle; exit 0 }

New-Item -ItemType Directory -Force -Path $Tools | Out-Null
$Zip = Join-Path $Tools "gradle-$Version-bin.zip"
$Url = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"
Write-Host "Gradle $Version not found. Downloading the official distribution once..."
try {
    Invoke-WebRequest -Uri $Url -OutFile $Zip -UseBasicParsing
} catch {
    throw "Gradle download failed. Check Internet access, or install Gradle 8.14+ manually. URL: $Url"
}
if (Test-Path $GradleHome) { Remove-Item $GradleHome -Recurse -Force }
Expand-Archive -Path $Zip -DestinationPath $Tools -Force
Remove-Item $Zip -Force -ErrorAction SilentlyContinue
if (-not (Test-Path $Gradle)) { throw "Gradle bootstrap finished but gradle.bat was not found: $Gradle" }
Write-Output $Gradle
