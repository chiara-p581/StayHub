param([Parameter(Mandatory=$true)][string]$WildFlyHome)
$ErrorActionPreference = 'Stop'
$cli = Join-Path $WildFlyHome 'bin\jboss-cli.bat'
if (-not (Test-Path -LiteralPath $cli)) { throw "No se encontró jboss-cli.bat en $WildFlyHome" }
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
} finally {
    $rng.Dispose()
}
$key = [Convert]::ToBase64String($bytes)
$read = & $cli --connect --command='/system-property=stayhub.mfa.master-key:read-resource' 2>&1 | Out-String
if ($read -match '"outcome" => "success"') {
    $operation = "/system-property=stayhub.mfa.master-key:write-attribute(name=value,value=`"$key`")"
} else {
    $operation = "/system-property=stayhub.mfa.master-key:add(value=`"$key`")"
}
$tempCli = [IO.Path]::GetTempFileName()
try {
    Set-Content -LiteralPath $tempCli -Value $operation -Encoding Ascii -NoNewline
    $result = & $cli --connect "--file=$tempCli" 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or $result -notmatch '"outcome" => "success"') {
        Write-Host $result
        throw 'WildFly rechazo la configuracion MFA'
    }
} finally {
    Remove-Item -LiteralPath $tempCli -Force -ErrorAction SilentlyContinue
}
Write-Host 'Clave MFA configurada. Recarga WildFly antes de desplegar el WAR.'
