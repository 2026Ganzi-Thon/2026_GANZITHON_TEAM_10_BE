$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$buildPath = Join-Path ([System.IO.Path]::GetTempPath()) 'moyang-sheep-build'
& (Join-Path $projectPath 'gradlew.bat') test bootJar --no-daemon "-PbuildDirOverride=$buildPath" -p $projectPath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Test report: $buildPath/reports/tests/test/index.html"
Write-Host "JAR: $buildPath/libs/app.jar"
