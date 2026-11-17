$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
& (Join-Path $projectPath 'gradlew.bat') build --no-daemon -p $projectPath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Test report: $projectPath/build/reports/tests/test/index.html"
Write-Host "JAR: $projectPath/build/libs/app.jar"
