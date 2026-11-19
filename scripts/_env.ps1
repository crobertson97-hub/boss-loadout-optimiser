# Shared environment setup for build.ps1 / test.ps1 / run-local.ps1. Dot-source it.
#
#  1. Finds a JDK (a JRE is not enough: Gradle needs javac). RuneLite plugins target Java 11,
#     any JDK 11+ can build them.
#  2. Works around "java.io.IOException: Unable to establish loopback connection", which Gradle
#     hits on some Windows machines when Java cannot create its internal socket files in %TEMP%
#     (seen with 8.3 short user-profile paths such as C:\Users\FIRSTN~1.LAS). Pointing
#     jdk.net.unixdomain.tmpdir at a plain directory inside the project fixes it.

$ErrorActionPreference = 'Stop'
$script:ProjectRoot = Split-Path -Parent $PSScriptRoot

function Test-Jdk([string]$dir) {
	return ($dir -and (Test-Path (Join-Path $dir 'bin\javac.exe')))
}

if (-not (Test-Jdk $env:JAVA_HOME)) {
	$candidates = @()
	foreach ($base in @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Amazon Corretto", "$env:ProgramFiles\Zulu")) {
		if (Test-Path $base) {
			$candidates += Get-ChildItem $base -Directory | Where-Object { Test-Jdk $_.FullName } | Sort-Object Name -Descending
		}
	}
	if ($candidates.Count -eq 0) {
		throw "No JDK found. Install a JDK 11 or newer (for example Eclipse Temurin 17 JDK) and set JAVA_HOME. JAVA_HOME is currently '$env:JAVA_HOME', which is not a JDK."
	}
	$env:JAVA_HOME = $candidates[0].FullName
}
Write-Host "Using JDK: $env:JAVA_HOME"

$sockDir = Join-Path $script:ProjectRoot '.tmp\sock'
New-Item -ItemType Directory -Force $sockDir | Out-Null
$sockOpt = "-Djdk.net.unixdomain.tmpdir=$sockDir"
if (-not ($env:JAVA_TOOL_OPTIONS -like '*jdk.net.unixdomain.tmpdir*')) {
	$env:JAVA_TOOL_OPTIONS = ("$env:JAVA_TOOL_OPTIONS $sockOpt").Trim()
}

Set-Location $script:ProjectRoot
# Java prints "Picked up JAVA_TOOL_OPTIONS" on stderr; that must not abort the calling script.
$ErrorActionPreference = 'Continue'
