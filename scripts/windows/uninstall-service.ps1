<#
.SYNOPSIS
    Stops and removes the Local Runtime Agent Windows Service. Run elevated.
#>
[CmdletBinding()]
param(
    [string]$ServiceId = "local-runtime-agent"
)
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$appHome = (Resolve-Path (Join-Path $scriptDir "..\..")).Path
$serviceExe = Join-Path $appHome "bin\$ServiceId.exe"

if (-not (Test-Path $serviceExe)) {
    throw "Service wrapper not found at $serviceExe (was it installed?)."
}

& $serviceExe stop
& $serviceExe uninstall
Write-Host "Uninstalled service '$ServiceId'."
