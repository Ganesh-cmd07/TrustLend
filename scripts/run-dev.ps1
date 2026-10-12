# Script to launch TrustLend Spring Boot backend with environment variables loaded from .env
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and ($line -match '^([^=]+)=(.*)$')) {
            $key = $matches[1].Trim()
            $val = $matches[2].Trim()
            [Environment]::SetEnvironmentVariable($key, $val, 'Process')
            Set-Item -Path "env:$key" -Value $val
        }
    }
}

Push-Location (Join-Path $root "backend")
try {
    Write-Host "Starting TrustLend Backend on port 4040..."
    .\mvnw.cmd spring-boot:run
}
finally {
    Pop-Location
}
