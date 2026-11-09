param(
    [string]$KeyHex = '000102030405060708090a0b0c0d0e0f',
    [string]$BlockHex = '00112233445566778899aabbccddeeff'
)
$ErrorActionPreference = 'Stop'
if ($KeyHex -notmatch '^[0-9a-fA-F]{32}$' -or $BlockHex -notmatch '^[0-9a-fA-F]{32}$') {
    throw 'Anahtar ve blok tam 32 onaltilik karakter (16 bayt) olmali.'
}
& (Join-Path $PSScriptRoot 'build.ps1')
$projectRoot = Split-Path -Parent $PSScriptRoot
& java -cp (Join-Path $projectRoot 'build/classes') tr.edu.crypto.ManualAesDemo $KeyHex $BlockHex
if ($LASTEXITCODE -ne 0) { throw 'Manuel AES ornegi basarisiz.' }
