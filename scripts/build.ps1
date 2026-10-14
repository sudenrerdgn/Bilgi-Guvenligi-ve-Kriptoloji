$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputPath = Join-Path $projectRoot 'build/classes'
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/main/java') -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
# Kaynaklari UTF-8 ile derleyerek Turkce arayuz metinlerini koruruz.
& javac --release 21 -encoding UTF-8 -d $outputPath @sources
if ($LASTEXITCODE -ne 0) { throw 'Java derlemesi basarisiz.' }
Copy-Item -Path (Join-Path $projectRoot 'src/main/resources/*') -Destination $outputPath -Recurse -Force
Write-Host 'Derleme tamamlandi.'
