param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
while (!(Test-Path -LiteralPath (Join-Path $taskRoot 'gradlew.bat'))) {
    $taskParent = Split-Path -Parent $taskRoot
    if (!$taskParent -or $taskParent -eq $taskRoot) { throw 'Cannot locate Microbot repository.' }
    $taskRoot = $taskParent
}
if (!$JavaHome) { throw 'Set JAVA_HOME to a working JDK 17 or newer.' }
$taskJava = Join-Path $JavaHome 'bin/java.exe'
$taskJar = Join-Path $JavaHome 'bin/jar.exe'
Push-Location $taskRoot
$taskPreviousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    & $taskJava -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon --console=plain '-Dorg.gradle.java.installations.auto-detect=false' :client:compileJava
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    $taskOutput = Join-Path $taskRoot 'runelite-client/build/libs/CorruptedGauntletPlugin.jar'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $taskOutput) | Out-Null
    & $taskJar --create --file $taskOutput -C runelite-client/build/classes/java/main net/runelite/client/plugins/microbot/corruptedgauntlet
    if ($LASTEXITCODE -ne 0) { throw 'Packaging failed.' }
    Write-Output $taskOutput
} finally { $env:JAVA_HOME = $taskPreviousJavaHome; Pop-Location }
