<#
.SYNOPSIS
    Starts the Local Runtime Agent Windows Service and prints its status. Run elevated.
#>
[CmdletBinding()]
param(
    [string]$ServiceId = "local-runtime-agent"
)
$ErrorActionPreference = "Stop"

Start-Service -Name $ServiceId
Get-Service -Name $ServiceId
