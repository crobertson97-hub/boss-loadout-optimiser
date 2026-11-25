# Compiles the plugin and runs the whole test-suite.
#   powershell -ExecutionPolicy Bypass -File scripts\build.ps1
. "$PSScriptRoot\_env.ps1"
& .\gradlew.bat build @args
exit $LASTEXITCODE
