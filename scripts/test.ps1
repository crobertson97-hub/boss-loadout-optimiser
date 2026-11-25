# Runs the unit, integration and reference-validation tests.
#   powershell -ExecutionPolicy Bypass -File scripts\test.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\test.ps1 --tests "*ReferenceValidationTest"
. "$PSScriptRoot\_env.ps1"
& .\gradlew.bat test @args
$code = $LASTEXITCODE
Write-Host "HTML report: $script:ProjectRoot\build\reports\tests\test\index.html"
exit $code
