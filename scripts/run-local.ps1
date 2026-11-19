# Starts a RuneLite development client with Boss Loadout Optimiser side-loaded.
#   powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
#
# This is the supported way to run an unpublished plugin: the client is launched from Gradle with
# the plugin registered as a built-in external plugin (see BossOptimiserPluginTest). Copying a JAR
# into a normal RuneLite install does not work.
#
# Jagex accounts: follow https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts once, so the
# development client can reuse your launcher credentials.
. "$PSScriptRoot\_env.ps1"
& .\gradlew.bat --no-daemon run @args
exit $LASTEXITCODE
