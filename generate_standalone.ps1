#requires -Version 5.1

param(
    [string]$GuiFile = (Join-Path $PSScriptRoot "search_asset_gui.ps1"),
    [string]$DatabaseFile = (Join-Path $PSScriptRoot "OnceHuman_EnhancedDatabase.csv"),
    [string]$OutputFile = (Join-Path $PSScriptRoot "OnceHumanAssetExplorer_Standalone.ps1")
)

$ErrorActionPreference = "Stop"

$guiFile = [IO.Path]::GetFullPath($GuiFile)
$databaseFile = [IO.Path]::GetFullPath($DatabaseFile)
$logoFile = Join-Path $PSScriptRoot "assets\blachammer-logo.png"
$outputFile = [IO.Path]::GetFullPath($OutputFile)
$temporaryOutputFile = "$outputFile.$PID.tmp"

foreach ($requiredFile in @($guiFile, $databaseFile, $logoFile)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required input file was not found: $requiredFile"
    }
}

$records = @(Import-Csv -LiteralPath $databaseFile)
if ($records.Count -eq 0) {
    throw "The enhanced database contains no records."
}

$guiSource = [IO.File]::ReadAllText($guiFile)
$guiEntryPoint = $guiSource.IndexOf("Add-Type -AssemblyName System.Windows.Forms")
if ($guiEntryPoint -lt 0) {
    throw "The GUI entry point was not found in the supplied script."
}
$guiSource = $guiSource.Substring($guiEntryPoint)

$databaseLoader = '$generated = @(Import-Csv -LiteralPath $databaseFile)'
if (-not $guiSource.Contains($databaseLoader)) {
    throw "The expected generated-database loader was not found in the supplied GUI script."
}

$databaseBytes = [IO.File]::ReadAllBytes($databaseFile)
$compressedStream = New-Object System.IO.MemoryStream
$gzipStream = New-Object System.IO.Compression.GZipStream(
    $compressedStream,
    [System.IO.Compression.CompressionMode]::Compress,
    $true
)

try {
    $gzipStream.Write($databaseBytes, 0, $databaseBytes.Length)
}
finally {
    $gzipStream.Dispose()
}

$compressedBytes = $compressedStream.ToArray()
$compressedStream.Dispose()
$encodedDatabase = [Convert]::ToBase64String($compressedBytes)
$base64Lines = New-Object System.Collections.Generic.List[string]

for ($offset = 0; $offset -lt $encodedDatabase.Length; $offset += 100) {
    $length = [Math]::Min(100, $encodedDatabase.Length - $offset)
    $base64Lines.Add($encodedDatabase.Substring($offset, $length))
}

$embeddedPreamble = @"
# Embedded compressed UTF-8 database. Manual entries remain in LOCALAPPDATA.
`$global:embeddedDatabaseCsv = `$null
`$embeddedDatabaseBase64 = @'
$($base64Lines -join "`r`n")
'@
`$compressedDatabase = [Convert]::FromBase64String(`$embeddedDatabaseBase64)
`$inputStream = New-Object System.IO.MemoryStream(,`$compressedDatabase)
`$gzipStream = New-Object System.IO.Compression.GZipStream(
    `$inputStream,
    [System.IO.Compression.CompressionMode]::Decompress
)
`$databaseReader = New-Object System.IO.StreamReader(
    `$gzipStream,
    [System.Text.Encoding]::UTF8,
    `$true
)
try {
    `$global:embeddedDatabaseCsv = `$databaseReader.ReadToEnd()
}
finally {
    `$databaseReader.Dispose()
    `$gzipStream.Dispose()
    `$inputStream.Dispose()
}
Remove-Variable embeddedDatabaseBase64, compressedDatabase

"@

$standaloneSource = $embeddedPreamble + $guiSource

try {
    [IO.File]::WriteAllText(
        $temporaryOutputFile,
        $standaloneSource,
        (New-Object System.Text.UTF8Encoding($true))
    )
    Move-Item `
        -LiteralPath $temporaryOutputFile `
        -Destination $outputFile `
        -Force
}
finally {
    if (Test-Path -LiteralPath $temporaryOutputFile) {
        Remove-Item -LiteralPath $temporaryOutputFile -Force
    }
}

$outputAssetsDirectory = Join-Path (Split-Path -Parent $outputFile) "assets"
New-Item -ItemType Directory -Path $outputAssetsDirectory -Force -ErrorAction Stop |
    Out-Null
Copy-Item `
    -LiteralPath $logoFile `
    -Destination (Join-Path $outputAssetsDirectory "blachammer-logo.png") `
    -Force `
    -ErrorAction Stop

Write-Host "Standalone script created: $outputFile"
Write-Host "Embedded records: $($records.Count)"
Write-Host "Database size: $([Math]::Round($databaseBytes.Length / 1MB, 2)) MB"
Write-Host "Compressed payload: $([Math]::Round($compressedBytes.Length / 1MB, 2)) MB"
