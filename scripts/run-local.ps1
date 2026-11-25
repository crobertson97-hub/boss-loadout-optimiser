# Starts a RuneLite development client with Boss Loadout Optimiser side-loaded.
#   powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1            # normal
#   powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1 -FullLogs  # full RuneLite debug logging
#
# This is the supported way to run an unpublished plugin: the client is launched from Gradle with
# the plugin registered as a built-in external plugin (see BossOptimiserPluginTest). Copying a JAR
# into a normal RuneLite install does not work.
#
# Jagex accounts: sign in from the client's sidebar ("Add Jagex Account"); see docs/local-setup.md.
param([switch]$FullLogs)
. "$PSScriptRoot\_env.ps1"
$extra = @()
if ($FullLogs) { $extra += '-PrlDebug' }
# The Gradle daemon is kept running between launches (gradle.properties), so only the first launch
# after a reboot pays Gradle's start-up cost.
& .\gradlew.bat run @extra @args
exit $LASTEXITCODE
