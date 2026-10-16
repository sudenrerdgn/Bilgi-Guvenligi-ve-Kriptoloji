$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sampleDirectory = Join-Path $projectRoot 'samples'
New-Item -ItemType Directory -Force -Path $sampleDirectory | Out-Null
# ASCII ve sikistirilmamis icerik, Wireshark'ta metni dogrudan gormemizi saglar.
$content = "BT /F1 18 Tf 50 780 Td (KRIPTOLOJI GUVENSIZ PDF TESTI) Tj 0 -30 Td /F1 12 Tf (Bu PDF sifrelenmeden sunucu uzerinden iletilmistir.) Tj ET`n"
$objects = @(
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    "<< /Length $($content.Length) >>`nstream`n${content}endstream"
)
$pdf = [System.Text.StringBuilder]::new()
[void]$pdf.Append("%PDF-1.4`n")
$offsets = [System.Collections.Generic.List[int]]::new()
for ($index = 0; $index -lt $objects.Count; $index++) {
    $offsets.Add($pdf.Length)
    [void]$pdf.Append("$($index + 1) 0 obj`n$($objects[$index])`nendobj`n")
}
$xrefOffset = $pdf.Length
[void]$pdf.Append("xref`n0 6`n0000000000 65535 f `n")
foreach ($offset in $offsets) { [void]$pdf.Append($offset.ToString('D10') + " 00000 n `n") }
[void]$pdf.Append("trailer`n<< /Size 6 /Root 1 0 R >>`nstartxref`n${xrefOffset}`n%%EOF`n")
[System.IO.File]::WriteAllBytes((Join-Path $sampleDirectory 'wireshark-test.pdf'), [System.Text.Encoding]::ASCII.GetBytes($pdf.ToString()))
Write-Host 'samples/wireshark-test.pdf olusturuldu.'
