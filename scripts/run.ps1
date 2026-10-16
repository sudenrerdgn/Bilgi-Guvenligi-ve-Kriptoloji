param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('server', 'client')]
    [string]$Role,
    [ValidateRange(1, 65535)]
    [int]$WebPort = 8080,
    [ValidateRange(1, 65535)]
    [int]$TcpPort = 5000
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $projectRoot 'build/classes'
if (-not (Test-Path -LiteralPath (Join-Path $classes 'tr/edu/crypto/Main.class'))) {
    throw 'Once .\scripts\build.ps1 komutunu calistirin.'
}
$appArguments = @($Role, "$WebPort")
if ($Role -eq 'server') { $appArguments += "$TcpPort" }
# Kayit klasorlerini komutun cagrildigi yerden bagimsiz olarak proje altinda tutariz.
Push-Location -LiteralPath $projectRoot
try { & java -cp $classes tr.edu.crypto.Main @appArguments }
finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'Uygulama baslatilamadi veya hata ile kapandi.' }
