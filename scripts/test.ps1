$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'build.ps1')
$projectRoot = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $projectRoot 'build/classes'
$testClasses = Join-Path $projectRoot 'build/test-classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/test/java') -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
$mainSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/main/java') -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
# Testleri uygulama kaynaklariyla birlikte derleyerek tek bir derleme birimi olustururuz.
& javac --release 21 -encoding UTF-8 -d $testClasses @mainSources @sources
if ($LASTEXITCODE -ne 0) { throw 'Test derlemesi basarisiz.' }
& java -cp "$classes;$testClasses" tr.edu.crypto.IntegrationTest
if ($LASTEXITCODE -ne 0) { throw 'Testler basarisiz.' }
