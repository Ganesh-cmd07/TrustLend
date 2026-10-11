[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$envNames = @(
    'SPRING_DATASOURCE_URL',
    'SPRING_DATASOURCE_USERNAME',
    'SPRING_DATASOURCE_PASSWORD',
    'TRUSTLEND_JWT_SECRET'
)
$previousEnvironment = @{}
foreach ($name in $envNames) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

$dbPasswordSecure = $null
$jwtSecretSecure = $null
$dbPassword = $null
$jwtSecret = $null
$exitCode = 0

try {
    $aivenHost = (Read-Host 'Aiven MySQL hostname').Trim()
    if ([string]::IsNullOrWhiteSpace($aivenHost) -or $aivenHost -match '[/\\@?#:\s]' -or $aivenHost -notmatch '(^|\.)aivencloud\.com$') {
        throw 'Enter the Aiven hostname shown in the service connection details (aivencloud.com). Do not use localhost.'
    }

    $portText = (Read-Host 'Aiven classic MySQL port').Trim()
    $aivenPort = 0
    if (-not [int]::TryParse($portText, [ref]$aivenPort) -or $aivenPort -lt 1 -or $aivenPort -gt 65535) {
        throw 'Enter a valid classic MySQL port from Aiven (1-65535).'
    }

    $dbUsername = (Read-Host 'Aiven MySQL username').Trim()
    if ([string]::IsNullOrWhiteSpace($dbUsername)) {
        throw 'A database username is required.'
    }

    $dbPasswordSecure = Read-Host 'Aiven MySQL password' -AsSecureString
    $dbPassword = [System.Net.NetworkCredential]::new('', $dbPasswordSecure).Password
    if ([string]::IsNullOrEmpty($dbPassword)) {
        throw 'A database password is required.'
    }

    if ([string]::IsNullOrWhiteSpace($previousEnvironment['TRUSTLEND_JWT_SECRET'])) {
        $jwtSecretSecure = Read-Host 'Existing stable TRUSTLEND_JWT_SECRET' -AsSecureString
        $jwtSecret = [System.Net.NetworkCredential]::new('', $jwtSecretSecure).Password
    }
    else {
        $jwtSecret = $previousEnvironment['TRUSTLEND_JWT_SECRET']
    }

    if ([string]::IsNullOrWhiteSpace($jwtSecret) -or
        [System.Text.Encoding]::UTF8.GetByteCount($jwtSecret) -lt 32) {
        throw 'TRUSTLEND_JWT_SECRET must be the existing stable secret and at least 32 UTF-8 bytes.'
    }

    $env:SPRING_DATASOURCE_URL = 'jdbc:mysql://{0}:{1}/Trustlend?sslMode=REQUIRED&serverTimezone=UTC' -f $aivenHost, $aivenPort
    $env:SPRING_DATASOURCE_USERNAME = $dbUsername
    $env:SPRING_DATASOURCE_PASSWORD = $dbPassword
    $env:TRUSTLEND_JWT_SECRET = $jwtSecret

    $projectRoot = Split-Path -Parent $PSScriptRoot
    $backendPath = Join-Path $projectRoot 'backend'
    Push-Location $backendPath
    try {
        Write-Host 'Starting TrustLend backend with the supplied Aiven settings.'
        & .\mvnw.cmd spring-boot:run
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}
finally {
    foreach ($name in $envNames) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    if ($dbPasswordSecure) { $dbPasswordSecure.Dispose() }
    if ($jwtSecretSecure) { $jwtSecretSecure.Dispose() }
    $dbPassword = $null
    $jwtSecret = $null
    $dbUsername = $null
}

if ($exitCode -ne 0) {
    throw "Spring Boot exited with code $exitCode. Check the backend startup error without sharing credentials."
}
