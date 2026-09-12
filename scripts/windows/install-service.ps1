<#
.SYNOPSIS
    Installs the Local Runtime Agent as a Windows Service via WinSW.

.DESCRIPTION
    A plain Java process cannot answer the Windows Service Control Manager, so a
    small wrapper (WinSW) hosts it. Place WinSW.exe next to this script (or set
    $env:WINSW_EXE) before running. Run from an elevated PowerShell.

    Layout assumed (distribution zip):
        <APP_HOME>\bin\      start.bat, generated service exe/xml
        <APP_HOME>\lib\      lra-agent-<version>.jar
        <APP_HOME>\config\   application*.yml
        <APP_HOME>\scripts\windows\  this script + WinSW.exe
#>
[CmdletBinding()]
param(
    [string]$ServiceId = "local-runtime-agent",
    [string]$DisplayName = "Local Runtime Agent"
)
$ErrorActionPreference = "Stop"

function Assert-Admin {
    $isAdmin = ([Security.Principal.WindowsPrincipal] `
        [Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) { throw "This script must be run as Administrator." }
}

Assert-Admin

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$appHome = (Resolve-Path (Join-Path $scriptDir "..\..")).Path
$binDir = Join-Path $appHome "bin"

$jar = Get-ChildItem -Path (Join-Path $appHome "lib") -Filter *.jar |
    Select-Object -First 1
if (-not $jar) { throw "No application jar found in $appHome\lib" }

$winsw = if ($env:WINSW_EXE) { $env:WINSW_EXE } else { Join-Path $scriptDir "WinSW.exe" }
if (-not (Test-Path $winsw)) {
    throw "WinSW.exe not found at $winsw. Download it (https://github.com/winsw/winsw/releases) and place it beside this script or set `$env:WINSW_EXE."
}

$serviceExe = Join-Path $binDir "$ServiceId.exe"
$serviceXml = Join-Path $binDir "$ServiceId.xml"
Copy-Item $winsw $serviceExe -Force

$xml = @"
<service>
  <id>$ServiceId</id>
  <name>$DisplayName</name>
  <description>PC-unit agent that manages Python AI model processes.</description>
  <executable>java</executable>
  <arguments>-jar "$($jar.FullName)" --spring.config.additional-location="file:$appHome\config\"</arguments>
  <env name="SPRING_PROFILES_ACTIVE" value="prod" />
  <workingdirectory>$appHome</workingdirectory>
  <onfailure action="restart" delay="5 sec" />
  <log mode="roll-by-size">
    <sizeThreshold>10240</sizeThreshold>
    <keepFiles>8</keepFiles>
  </log>
  <logpath>$appHome\logs</logpath>
</service>
"@
Set-Content -Path $serviceXml -Value $xml -Encoding UTF8

& $serviceExe install
Write-Host "Installed service '$ServiceId'. Start with: scripts\windows\start-service.ps1"
