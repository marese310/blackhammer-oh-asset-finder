Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

# ============================================================
# ONCE HUMAN ASSET EXPLORER V1.0
# ============================================================

$applicationDirectory = $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($applicationDirectory)) {
    $applicationDirectory = Split-Path -Parent (
        [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
    )
}

$databaseFile = Join-Path $applicationDirectory "OnceHuman_EnhancedDatabase.csv"
$logoFile = Join-Path $applicationDirectory "assets\blachammer-logo.png"

if (
    [string]::IsNullOrWhiteSpace($global:embeddedDatabaseCsv) -and
    -not (Test-Path $databaseFile)
) {
    [System.Windows.Forms.MessageBox]::Show(
        "Database not found:`n$databaseFile",
        "Once Human Asset Explorer",
        "OK",
        "Error"
    )
    exit
}

if (-not (Test-Path -LiteralPath $logoFile -PathType Leaf)) {
    [System.Windows.Forms.MessageBox]::Show(
        "Application logo not found:`n$logoFile",
        "Once Human Asset Explorer",
        "OK",
        "Error"
    )
    exit
}

if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    throw "LOCALAPPDATA is not set. Cannot locate the manual asset database."
}

$userDataFolder = Join-Path $env:LOCALAPPDATA "OnceHumanAssetExplorer"
$manualDatabaseFile = Join-Path $userDataFolder "manual_items.csv"
$legacyManualDatabaseFile = Join-Path $applicationDirectory "manual_items.csv"

New-Item `
    -ItemType Directory `
    -Path $userDataFolder `
    -Force `
    -ErrorAction Stop |
    Out-Null

if (
    -not (Test-Path $manualDatabaseFile) -and
    (Test-Path $legacyManualDatabaseFile) -and
    [IO.Path]::GetFullPath($legacyManualDatabaseFile) -ne
        [IO.Path]::GetFullPath($manualDatabaseFile)
) {
    Copy-Item `
        -LiteralPath $legacyManualDatabaseFile `
        -Destination $manualDatabaseFile `
        -ErrorAction Stop
}

# ------------------------------------------------------------
# LOAD DATABASES
# ------------------------------------------------------------

if (
    -not (Test-Path $manualDatabaseFile) -or
    (Get-Item $manualDatabaseFile).Length -eq 0
) {
    Set-Content `
        -LiteralPath $manualDatabaseFile `
        -Value "Category,SubCategory,AssetName,DisplayName,Source,SearchTerms,Notes" `
        -Encoding UTF8
}

function Load-AssetDatabase {

    if ([string]::IsNullOrWhiteSpace($global:embeddedDatabaseCsv)) {
        $generated = @(Import-Csv -LiteralPath $databaseFile)
    }
    else {
        $generated = @(
            ConvertFrom-Csv -InputObject $global:embeddedDatabaseCsv
        )
    }

    $manual = @(
        Import-Csv -LiteralPath $manualDatabaseFile |
            Where-Object {
                -not [string]::IsNullOrWhiteSpace($_.AssetName)
            }
    )

    return @($generated) + @($manual)
}

function Save-ManualDatabase {

    param(
        [AllowEmptyCollection()]
        [object[]]$Records
    )

    $columns = @(
        "Category",
        "SubCategory",
        "AssetName",
        "DisplayName",
        "Source",
        "SearchTerms",
        "Notes"
    )

    $temporaryFile = "$manualDatabaseFile.$PID.$([guid]::NewGuid().ToString('N')).tmp"

    try {
        if ($null -eq $Records -or $Records.Count -eq 0) {
            Set-Content `
                -LiteralPath $temporaryFile `
                -Value ($columns -join ",") `
                -Encoding UTF8 `
                -ErrorAction Stop
        }
        else {
            @($Records) |
                Select-Object -Property $columns |
                Export-Csv `
                    -LiteralPath $temporaryFile `
                    -NoTypeInformation `
                    -Encoding UTF8 `
                    -ErrorAction Stop
        }

        Move-Item `
            -LiteralPath $temporaryFile `
            -Destination $manualDatabaseFile `
            -Force `
            -ErrorAction Stop
    }
    finally {
        if (Test-Path -LiteralPath $temporaryFile) {
            Remove-Item -LiteralPath $temporaryFile -ErrorAction SilentlyContinue
        }
    }
}

function Reload-AssetDatabase {

    $script:db = @(Load-AssetDatabase)
    Update-CategoryList
    Update-SubCategoryList
    Update-Results
}

function Find-ManualRecordIndex {

    param(
        [Parameter(Mandatory = $true)]
        [object]$Record,
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [object[]]$ManualRecords
    )

    for ($index = 0; $index -lt $ManualRecords.Count; $index++) {
        $candidate = $ManualRecords[$index]

        if (
            $candidate.Source -eq "Manual" -and
            $candidate.AssetName -ieq $Record.AssetName -and
            $candidate.DisplayName -eq $Record.DisplayName -and
            $candidate.Category -eq $Record.Category -and
            $candidate.SubCategory -eq $Record.SubCategory -and
            $candidate.SearchTerms -eq $Record.SearchTerms -and
            $candidate.Notes -eq $Record.Notes
        ) {
            return $index
        }
    }

    return -1
}

function Get-SelectedAssetRecord {

    if ($grid.SelectedRows.Count -eq 0) {
        return $null
    }

    $recordIndex = [int]$grid.SelectedRows[0].Cells["RecordIndex"].Value

    if (
        $recordIndex -lt 0 -or
        $recordIndex -ge $script:currentResults.Count
    ) {
        return $null
    }

    return $script:currentResults[$recordIndex]
}

$script:db = @(Load-AssetDatabase)

# Categories considered useful for Gameplay Only mode.
$gameplayCategories = @(
    "Animal",
    "Deviant",
    "Equipment",
    "Interactive",
    "KeyItem",
    "Loot",
    "Monster",
    "NPC",
    "Plant",
    "Resource",
    "Structure",
    "Vehicle",
    "Weapon"
)

$script:currentResults = @()

# ============================================================
# FORM
# ============================================================

$form = New-Object System.Windows.Forms.Form
$form.Text = "Once Human Asset Explorer V1.0 - BlacHammer"
$form.Size = New-Object System.Drawing.Size(1450, 850)
$form.MinimumSize = New-Object System.Drawing.Size(1100, 650)
$form.StartPosition = "CenterScreen"
$form.BackColor = [System.Drawing.Color]::FromArgb(28, 30, 34)

# ============================================================
# BRANDING
# ============================================================

$logoImage = $null
try {
    $logoImage = [System.Drawing.Image]::FromFile($logoFile)
}
catch {
    [System.Windows.Forms.MessageBox]::Show(
        "Could not load the application logo:`n$($_.Exception.Message)",
        "Logo Load Failed",
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Error
    )
    exit
}

$logoPicture = New-Object System.Windows.Forms.PictureBox
$logoPicture.Location = New-Object System.Drawing.Point(12, 4)
$logoPicture.Size = New-Object System.Drawing.Size(44, 44)
$logoPicture.SizeMode = [System.Windows.Forms.PictureBoxSizeMode]::Zoom
$logoPicture.Image = $logoImage
$logoPicture.BackColor = $form.BackColor
$form.Controls.Add($logoPicture)

$title = New-Object System.Windows.Forms.Label
$title.Text = "ONCE HUMAN ASSET EXPLORER V1.0"
$title.Location = New-Object System.Drawing.Point(65, 5)
$title.Size = New-Object System.Drawing.Size(500, 28)
$title.Font = New-Object System.Drawing.Font("Segoe UI", 15, [System.Drawing.FontStyle]::Bold)
$title.ForeColor = [System.Drawing.Color]::FromArgb(80, 200, 255)
$title.Anchor = "Top,Left"

$form.Controls.Add($title)

$brandLabel = New-Object System.Windows.Forms.Label
$brandLabel.Text = "BY BLACHAMMER"
$brandLabel.Location = New-Object System.Drawing.Point(570, 12)
$brandLabel.Size = New-Object System.Drawing.Size(170, 22)
$brandLabel.Font = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
$brandLabel.ForeColor = [System.Drawing.Color]::FromArgb(180, 110, 255)
$brandLabel.Anchor = "Top,Left"
$form.Controls.Add($brandLabel)

$discordInviteUrl = "https://discord.gg/qeGAX6VCUz"
$discordLink = New-Object System.Windows.Forms.LinkLabel
$discordLink.Text = "Join the Discord"
$discordLink.Location = New-Object System.Drawing.Point(760, 12)
$discordLink.Size = New-Object System.Drawing.Size(160, 22)
$discordLink.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$discordLink.LinkColor = [System.Drawing.Color]::FromArgb(120, 160, 255)
$discordLink.ActiveLinkColor = [System.Drawing.Color]::FromArgb(180, 110, 255)
$discordLink.VisitedLinkColor = [System.Drawing.Color]::FromArgb(120, 160, 255)
$discordLink.LinkArea = New-Object System.Windows.Forms.LinkArea(0, $discordLink.Text.Length)
$discordLink.Anchor = "Top,Left"
$discordLink.Add_LinkClicked({
    try {
        Start-Process -FilePath $discordInviteUrl -ErrorAction Stop
    }
    catch {
        [System.Windows.Forms.MessageBox]::Show(
            "Could not open the Discord invite:`n$($_.Exception.Message)",
            "Discord Link Failed",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        )
    }
}.GetNewClosure())
$form.Controls.Add($discordLink)

$form.Add_FormClosed({
    if ($null -ne $logoImage) {
        $logoImage.Dispose()
    }
}.GetNewClosure())

# ============================================================
# SEARCH
# ============================================================

$searchLabel = New-Object System.Windows.Forms.Label
$searchLabel.Text = "Search"
$searchLabel.Location = New-Object System.Drawing.Point(15, 52)
$searchLabel.AutoSize = $true
$searchLabel.ForeColor = [System.Drawing.Color]::White

$form.Controls.Add($searchLabel)

$searchBox = New-Object System.Windows.Forms.TextBox
$searchBox.Location = New-Object System.Drawing.Point(15, 72)
$searchBox.Size = New-Object System.Drawing.Size(380, 27)
$searchBox.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$searchBox.Anchor = "Top,Left"

$form.Controls.Add($searchBox)

# ============================================================
# CATEGORY FILTER
# ============================================================

$categoryLabel = New-Object System.Windows.Forms.Label
$categoryLabel.Text = "Category"
$categoryLabel.Location = New-Object System.Drawing.Point(410, 52)
$categoryLabel.AutoSize = $true
$categoryLabel.ForeColor = [System.Drawing.Color]::White

$form.Controls.Add($categoryLabel)

$categoryBox = New-Object System.Windows.Forms.ComboBox
$categoryBox.Location = New-Object System.Drawing.Point(410, 72)
$categoryBox.Size = New-Object System.Drawing.Size(170, 27)
$categoryBox.DropDownStyle = "DropDownList"

[void]$categoryBox.Items.Add("All")

$script:db |
    Select-Object -ExpandProperty Category -Unique |
    Sort-Object |
    ForEach-Object {
        if (-not [string]::IsNullOrWhiteSpace($_)) {
            [void]$categoryBox.Items.Add($_)
        }
    }

$categoryBox.SelectedIndex = 0

$form.Controls.Add($categoryBox)

# ============================================================
# SUBCATEGORY FILTER
# ============================================================

$subCategoryLabel = New-Object System.Windows.Forms.Label
$subCategoryLabel.Text = "SubCategory"
$subCategoryLabel.Location = New-Object System.Drawing.Point(595, 52)
$subCategoryLabel.AutoSize = $true
$subCategoryLabel.ForeColor = [System.Drawing.Color]::White

$form.Controls.Add($subCategoryLabel)

$subCategoryBox = New-Object System.Windows.Forms.ComboBox
$subCategoryBox.Location = New-Object System.Drawing.Point(595, 72)
$subCategoryBox.Size = New-Object System.Drawing.Size(200, 27)
$subCategoryBox.DropDownStyle = "DropDownList"

[void]$subCategoryBox.Items.Add("All")
$subCategoryBox.SelectedIndex = 0

$form.Controls.Add($subCategoryBox)

# ============================================================
# OPTIONS
# ============================================================

$hideVisual = New-Object System.Windows.Forms.CheckBox
$hideVisual.Text = "Hide Visual"
$hideVisual.Location = New-Object System.Drawing.Point(815, 73)
$hideVisual.AutoSize = $true
$hideVisual.Checked = $true
$hideVisual.ForeColor = [System.Drawing.Color]::White

$form.Controls.Add($hideVisual)

$hideWorld = New-Object System.Windows.Forms.CheckBox
$hideWorld.Text = "Hide World"
$hideWorld.Location = New-Object System.Drawing.Point(920, 73)
$hideWorld.AutoSize = $true
$hideWorld.Checked = $true
$hideWorld.ForeColor = [System.Drawing.Color]::White

$form.Controls.Add($hideWorld)

$gameplayOnly = New-Object System.Windows.Forms.CheckBox
$gameplayOnly.Text = "Gameplay Only"
$gameplayOnly.Location = New-Object System.Drawing.Point(1025, 73)
$gameplayOnly.AutoSize = $true
$gameplayOnly.ForeColor = [System.Drawing.Color]::White

$form.Controls.Add($gameplayOnly)

$exactMatch = New-Object System.Windows.Forms.CheckBox
$exactMatch.Text = "Exact Asset Name"
$exactMatch.Location = New-Object System.Drawing.Point(1150, 73)
$exactMatch.AutoSize = $true
$exactMatch.ForeColor = [System.Drawing.Color]::White

$form.Controls.Add($exactMatch)

$baseAssetsOnly = New-Object System.Windows.Forms.CheckBox
$baseAssetsOnly.Text = "Base Assets Only"
$baseAssetsOnly.Location = New-Object System.Drawing.Point(1280, 73)
$baseAssetsOnly.AutoSize = $true
$baseAssetsOnly.Checked = $false
$baseAssetsOnly.ForeColor = [System.Drawing.Color]::White

$form.Controls.Add($baseAssetsOnly)

# ============================================================
# BUTTONS
# ============================================================

$searchButton = New-Object System.Windows.Forms.Button
$searchButton.Text = "Search"
$searchButton.Location = New-Object System.Drawing.Point(15, 112)
$searchButton.Size = New-Object System.Drawing.Size(110, 34)

$form.Controls.Add($searchButton)

$resetButton = New-Object System.Windows.Forms.Button
$resetButton.Text = "Reset"
$resetButton.Location = New-Object System.Drawing.Point(135, 112)
$resetButton.Size = New-Object System.Drawing.Size(110, 34)

$form.Controls.Add($resetButton)

$exportButton = New-Object System.Windows.Forms.Button
$exportButton.Text = "Export Results"
$exportButton.Location = New-Object System.Drawing.Point(255, 112)
$exportButton.Size = New-Object System.Drawing.Size(130, 34)

$form.Controls.Add($exportButton)

$copyButton = New-Object System.Windows.Forms.Button
$copyButton.Text = "Copy Asset Name"
$copyButton.Location = New-Object System.Drawing.Point(395, 112)
$copyButton.Size = New-Object System.Drawing.Size(145, 34)

$form.Controls.Add($copyButton)

$addAssetButton = New-Object System.Windows.Forms.Button
$addAssetButton.Text = "Add New Asset"
$addAssetButton.Location = New-Object System.Drawing.Point(550, 112)
$addAssetButton.Size = New-Object System.Drawing.Size(135, 34)

$form.Controls.Add($addAssetButton)

# ============================================================
# EDIT AND DELETE MANUAL ASSET BUTTONS
# ============================================================

$editAssetButton = New-Object System.Windows.Forms.Button
$editAssetButton.Text = "Edit Manual Asset"
$editAssetButton.Location = New-Object System.Drawing.Point(695, 112)
$editAssetButton.Size = New-Object System.Drawing.Size(145, 34)
$editAssetButton.Enabled = $false

$form.Controls.Add($editAssetButton)

$deleteAssetButton = New-Object System.Windows.Forms.Button
$deleteAssetButton.Text = "Delete Manual Asset"
$deleteAssetButton.Location = New-Object System.Drawing.Point(850, 112)
$deleteAssetButton.Size = New-Object System.Drawing.Size(155, 34)
$deleteAssetButton.Enabled = $false
$deleteAssetButton.ForeColor = [System.Drawing.Color]::DarkRed

$form.Controls.Add($deleteAssetButton)

$undoDeleteButton = New-Object System.Windows.Forms.Button
$undoDeleteButton.Text = "Undo Delete"
$undoDeleteButton.Location = New-Object System.Drawing.Point(1015, 112)
$undoDeleteButton.Size = New-Object System.Drawing.Size(120, 34)
$undoDeleteButton.Enabled = $false

$form.Controls.Add($undoDeleteButton)

$script:lastDeletedAsset = $null

# ============================================================
# RESULT COUNT
# ============================================================

$countLabel = New-Object System.Windows.Forms.Label
$countLabel.Text = "0 results"
$countLabel.Location = New-Object System.Drawing.Point(1145, 120)
$countLabel.Size = New-Object System.Drawing.Size(260, 24)
$countLabel.ForeColor = [System.Drawing.Color]::FromArgb(255, 210, 70)
$countLabel.Font = New-Object System.Drawing.Font(
    "Segoe UI",
    10,
    [System.Drawing.FontStyle]::Bold
)

$form.Controls.Add($countLabel)

# ============================================================
# DATA GRID
# ============================================================

$grid = New-Object System.Windows.Forms.DataGridView
$grid.Location = New-Object System.Drawing.Point(15, 160)
$grid.Size = New-Object System.Drawing.Size(1400, 470)
$grid.Anchor = "Top,Bottom,Left,Right"

$grid.ReadOnly = $true
$grid.AllowUserToAddRows = $false
$grid.AllowUserToDeleteRows = $false
$grid.MultiSelect = $false
$grid.SelectionMode = "FullRowSelect"
$grid.AutoGenerateColumns = $true
$grid.AutoSizeColumnsMode = "Fill"
$grid.RowHeadersVisible = $false

$grid.BackgroundColor = [System.Drawing.Color]::FromArgb(35, 38, 42)
$grid.ForeColor = [System.Drawing.Color]::Black

$form.Controls.Add($grid)

# ============================================================
# DETAILS PANEL
# ============================================================

$detailsGroup = New-Object System.Windows.Forms.GroupBox
$detailsGroup.Text = "Selected Asset Details"
$detailsGroup.Location = New-Object System.Drawing.Point(15, 640)
$detailsGroup.Size = New-Object System.Drawing.Size(1400, 135)
$detailsGroup.Anchor = "Bottom,Left,Right"
$detailsGroup.ForeColor = [System.Drawing.Color]::White

$form.Controls.Add($detailsGroup)

# Display Name

$detailDisplayLabel = New-Object System.Windows.Forms.Label
$detailDisplayLabel.Text = "Display Name:"
$detailDisplayLabel.Location = New-Object System.Drawing.Point(15, 27)
$detailDisplayLabel.Size = New-Object System.Drawing.Size(100, 22)

$detailsGroup.Controls.Add($detailDisplayLabel)

$detailDisplayValue = New-Object System.Windows.Forms.TextBox
$detailDisplayValue.Location = New-Object System.Drawing.Point(120, 24)
$detailDisplayValue.Size = New-Object System.Drawing.Size(350, 25)
$detailDisplayValue.ReadOnly = $true

$detailsGroup.Controls.Add($detailDisplayValue)

# Asset Name

$detailAssetLabel = New-Object System.Windows.Forms.Label
$detailAssetLabel.Text = "Asset Name:"
$detailAssetLabel.Location = New-Object System.Drawing.Point(15, 62)
$detailAssetLabel.Size = New-Object System.Drawing.Size(100, 22)

$detailsGroup.Controls.Add($detailAssetLabel)

$detailAssetValue = New-Object System.Windows.Forms.TextBox
$detailAssetValue.Location = New-Object System.Drawing.Point(120, 59)
$detailAssetValue.Size = New-Object System.Drawing.Size(600, 25)
$detailAssetValue.ReadOnly = $true

$detailsGroup.Controls.Add($detailAssetValue)

# Category

$detailCategoryLabel = New-Object System.Windows.Forms.Label
$detailCategoryLabel.Text = "Category:"
$detailCategoryLabel.Location = New-Object System.Drawing.Point(500, 27)
$detailCategoryLabel.Size = New-Object System.Drawing.Size(75, 22)

$detailsGroup.Controls.Add($detailCategoryLabel)

$detailCategoryValue = New-Object System.Windows.Forms.TextBox
$detailCategoryValue.Location = New-Object System.Drawing.Point(575, 24)
$detailCategoryValue.Size = New-Object System.Drawing.Size(170, 25)
$detailCategoryValue.ReadOnly = $true

$detailsGroup.Controls.Add($detailCategoryValue)

# SubCategory

$detailSubLabel = New-Object System.Windows.Forms.Label
$detailSubLabel.Text = "SubCategory:"
$detailSubLabel.Location = New-Object System.Drawing.Point(770, 27)
$detailSubLabel.Size = New-Object System.Drawing.Size(95, 22)

$detailsGroup.Controls.Add($detailSubLabel)

$detailSubValue = New-Object System.Windows.Forms.TextBox
$detailSubValue.Location = New-Object System.Drawing.Point(865, 24)
$detailSubValue.Size = New-Object System.Drawing.Size(200, 25)
$detailSubValue.ReadOnly = $true

$detailsGroup.Controls.Add($detailSubValue)

# Source

$detailSourceLabel = New-Object System.Windows.Forms.Label
$detailSourceLabel.Text = "Source:"
$detailSourceLabel.Location = New-Object System.Drawing.Point(1080, 27)
$detailSourceLabel.Size = New-Object System.Drawing.Size(60, 22)

$detailsGroup.Controls.Add($detailSourceLabel)

$detailSourceValue = New-Object System.Windows.Forms.TextBox
$detailSourceValue.Location = New-Object System.Drawing.Point(1140, 24)
$detailSourceValue.Size = New-Object System.Drawing.Size(235, 25)
$detailSourceValue.ReadOnly = $true

$detailsGroup.Controls.Add($detailSourceValue)

# Copy Asset

$copyDetailAssetButton = New-Object System.Windows.Forms.Button
$copyDetailAssetButton.Text = "Copy Asset Name"
$copyDetailAssetButton.Location = New-Object System.Drawing.Point(750, 58)
$copyDetailAssetButton.Size = New-Object System.Drawing.Size(145, 30)

$detailsGroup.Controls.Add($copyDetailAssetButton)

# Copy Display Name

$copyDisplayButton = New-Object System.Windows.Forms.Button
$copyDisplayButton.Text = "Copy Display Name"
$copyDisplayButton.Location = New-Object System.Drawing.Point(905, 58)
$copyDisplayButton.Size = New-Object System.Drawing.Size(155, 30)

$detailsGroup.Controls.Add($copyDisplayButton)

# Search Terms

$detailSearchLabel = New-Object System.Windows.Forms.Label
$detailSearchLabel.Text = "Search Terms:"
$detailSearchLabel.Location = New-Object System.Drawing.Point(15, 96)
$detailSearchLabel.Size = New-Object System.Drawing.Size(100, 22)

$detailsGroup.Controls.Add($detailSearchLabel)

$detailSearchValue = New-Object System.Windows.Forms.TextBox
$detailSearchValue.Location = New-Object System.Drawing.Point(120, 93)
$detailSearchValue.Size = New-Object System.Drawing.Size(820, 25)
$detailSearchValue.ReadOnly = $true

$detailsGroup.Controls.Add($detailSearchValue)

# Notes

$detailNotesLabel = New-Object System.Windows.Forms.Label
$detailNotesLabel.Text = "Notes:"
$detailNotesLabel.Location = New-Object System.Drawing.Point(1080, 60)
$detailNotesLabel.Size = New-Object System.Drawing.Size(55, 22)

$detailsGroup.Controls.Add($detailNotesLabel)

$detailNotesValue = New-Object System.Windows.Forms.TextBox
$detailNotesValue.Location = New-Object System.Drawing.Point(1140, 57)
$detailNotesValue.Size = New-Object System.Drawing.Size(235, 60)
$detailNotesValue.Multiline = $true
$detailNotesValue.ReadOnly = $true

$detailsGroup.Controls.Add($detailNotesValue)

# ============================================================
# UPDATE SUBCATEGORY LIST
# ============================================================

function Update-CategoryList {

    $selectedCategory = [string]$categoryBox.SelectedItem

    $categoryBox.Items.Clear()
    [void]$categoryBox.Items.Add("All")

    $script:db |
        Select-Object -ExpandProperty Category -Unique |
        Sort-Object |
        ForEach-Object {
            if (-not [string]::IsNullOrWhiteSpace($_)) {
                [void]$categoryBox.Items.Add($_)
            }
        }

    $selectedIndex = $categoryBox.Items.IndexOf($selectedCategory)
    if ($selectedIndex -ge 0) {
        $categoryBox.SelectedIndex = $selectedIndex
    }
    else {
        $categoryBox.SelectedIndex = 0
    }
}

function Update-SubCategoryList {

    $selectedCategory = [string]$categoryBox.SelectedItem

    $subCategoryBox.Items.Clear()
    [void]$subCategoryBox.Items.Add("All")

    $source = $script:db

    if (
        -not [string]::IsNullOrWhiteSpace($selectedCategory) -and
        $selectedCategory -ne "All"
    ) {
        $source = @(
            $script:db | Where-Object {
                $_.Category -eq $selectedCategory
            }
        )
    }

    $source |
        Select-Object -ExpandProperty SubCategory -Unique |
        Sort-Object |
        ForEach-Object {

            if (-not [string]::IsNullOrWhiteSpace($_)) {
                [void]$subCategoryBox.Items.Add($_)
            }
        }

    $subCategoryBox.SelectedIndex = 0
}

# ============================================================
# SEARCH FUNCTION
# ============================================================

function Update-Results {

    $term = $searchBox.Text.Trim()

    $selectedCategory = [string]$categoryBox.SelectedItem
    $selectedSubCategory = [string]$subCategoryBox.SelectedItem

    $filtered = $script:db

    # Hide Visual assets

    if ($hideVisual.Checked) {

        $filtered = @(
            $filtered | Where-Object {
                $_.Category -ne "Visual"
            }
        )
    }

    # Hide World assets

    if ($hideWorld.Checked) {

        $filtered = @(
            $filtered | Where-Object {
                $_.Category -ne "World"
            }
        )
    }

    # Gameplay Only

    if ($gameplayOnly.Checked) {

        $filtered = @(
            $filtered | Where-Object {
                $_.Category -in $gameplayCategories
            }
        )
    }

    # Category

    if (
        -not [string]::IsNullOrWhiteSpace($selectedCategory) -and
        $selectedCategory -ne "All"
    ) {

        $filtered = @(
            $filtered | Where-Object {
                $_.Category -eq $selectedCategory
            }
        )
    }

    # SubCategory

    if (
        -not [string]::IsNullOrWhiteSpace($selectedSubCategory) -and
        $selectedSubCategory -ne "All"
    ) {

        $filtered = @(
            $filtered | Where-Object {
                $_.SubCategory -eq $selectedSubCategory
            }
        )
    }

    # Base Assets Only
    # Removes common derivative/visual/component records.

    if ($baseAssetsOnly.Checked) {

        $filtered = @(
            $filtered | Where-Object {

                $name = $_.AssetName.ToLowerInvariant()

                $name -notmatch '_albedo(?:_alpha)?' -and
                $name -notmatch '_normal' -and
                $name -notmatch '_control' -and
                $name -notmatch '_feature' -and
                $name -notmatch '_lod[0-9]+'
            }
        )
    }

    # Search text

    if (-not [string]::IsNullOrWhiteSpace($term)) {

        if ($exactMatch.Checked) {

            $filtered = @(
                $filtered | Where-Object {
                    $_.AssetName -ieq $term
                }
            )
        }

        else {

            $escapedTerm = [regex]::Escape($term)

            $filtered = @(
                $filtered | Where-Object {

                    $_.AssetName -match $escapedTerm -or
                    $_.DisplayName -match $escapedTerm -or
                    $_.Category -match $escapedTerm -or
                    $_.SubCategory -match $escapedTerm -or
                    $_.SearchTerms -match $escapedTerm
                }
            )
        }
    }

    $script:currentResults = @(
        $filtered |
            Sort-Object Category, SubCategory, DisplayName, AssetName
    )

    # Limit massive GUI results while preserving the real count.
    $displayResults = @(
        $script:currentResults |
            Select-Object -First 5000 `
                Category,
                SubCategory,
                AssetName,
                DisplayName
    )

    $table = New-Object System.Data.DataTable

    [void]$table.Columns.Add("Category")
    [void]$table.Columns.Add("SubCategory")
    [void]$table.Columns.Add("AssetName")
    [void]$table.Columns.Add("DisplayName")
    [void]$table.Columns.Add("RecordIndex")

    for ($index = 0; $index -lt $displayResults.Count; $index++) {

        $item = $displayResults[$index]

        $row = $table.NewRow()

        $row["Category"] = [string]$item.Category
        $row["SubCategory"] = [string]$item.SubCategory
        $row["AssetName"] = [string]$item.AssetName
        $row["DisplayName"] = [string]$item.DisplayName
        $row["RecordIndex"] = $index

        $table.Rows.Add($row)
    }

    $grid.DataSource = $null
    $grid.DataSource = $table
    $grid.Columns["RecordIndex"].Visible = $false

    $count = $script:currentResults.Count

    if ($count -gt 5000) {

        $countLabel.Text = "$count results - showing first 5000"
    }

    else {

        $countLabel.Text = "$count results"
    }
}

# ============================================================
# CATEGORY CHANGED
# ============================================================

$categoryBox.Add_SelectedIndexChanged({

    Update-SubCategoryList
})

# ============================================================
# SEARCH BUTTON
# ============================================================

$searchButton.Add_Click({

    Update-Results
})

# ============================================================
# ENTER KEY SEARCH
# ============================================================

$searchBox.Add_KeyDown({

    param($sender, $eventArgs)

    if ($eventArgs.KeyCode -eq [System.Windows.Forms.Keys]::Enter) {

        Update-Results

        $eventArgs.SuppressKeyPress = $true
    }
})


# RESET
# ============================================================

$resetButton.Add_Click({

    $searchBox.Clear()

    $hideVisual.Checked = $true
    $hideWorld.Checked = $true
    $gameplayOnly.Checked = $false
    $exactMatch.Checked = $false
    $baseAssetsOnly.Checked = $false

    $categoryBox.SelectedIndex = 0

    Update-SubCategoryList
    Update-Results
})

# ============================================================
# COPY SELECTED ASSET NAME
# ============================================================

function Copy-SelectedAsset {

    if ($grid.SelectedRows.Count -eq 0) {
        return
    }

    $assetName = $grid.SelectedRows[0].Cells["AssetName"].Value

    if (-not [string]::IsNullOrWhiteSpace([string]$assetName)) {

        [System.Windows.Forms.Clipboard]::SetText(
            [string]$assetName
        )

        $countLabel.Text = "Copied: $assetName"
    }
}

$copyButton.Add_Click({

    Copy-SelectedAsset
})

$grid.Add_CellDoubleClick({

    param($sender, $eventArgs)

    if ($eventArgs.RowIndex -ge 0) {
        Copy-SelectedAsset
    }
})

# ============================================================
# EXPORT FILTERED RESULTS
# ============================================================

$exportButton.Add_Click({

    if ($script:currentResults.Count -eq 0) {

        [System.Windows.Forms.MessageBox]::Show(
            "There are no results to export.",
            "Once Human Asset Explorer"
        )

        return
    }

    $saveDialog = New-Object System.Windows.Forms.SaveFileDialog

    $saveDialog.Title = "Export Asset Search Results"
    $saveDialog.Filter = "CSV files (*.csv)|*.csv"
    $saveDialog.FileName = "OnceHuman_SearchResults.csv"
    $saveDialog.InitialDirectory = $applicationDirectory

    if (
        $saveDialog.ShowDialog() -eq
        [System.Windows.Forms.DialogResult]::OK
    ) {

        $script:currentResults |
            Export-Csv `
                $saveDialog.FileName `
                -NoTypeInformation `
                -Encoding UTF8

        [System.Windows.Forms.MessageBox]::Show(
            "Export complete:`n$($saveDialog.FileName)",
            "Once Human Asset Explorer"
        )
    }
})

# ============================================================
# DISPLAY SELECTED ASSET DETAILS
# ============================================================

$grid.Add_SelectionChanged({

    if ($grid.SelectedRows.Count -eq 0) {
        $editAssetButton.Enabled = $false
        $deleteAssetButton.Enabled = $false
        $detailDisplayValue.Clear()
        $detailAssetValue.Clear()
        $detailCategoryValue.Clear()
        $detailSubValue.Clear()
        $detailSourceValue.Clear()
        $detailSearchValue.Clear()
        $detailNotesValue.Clear()
        return
    }

    $record = Get-SelectedAssetRecord

    if ($null -eq $record) {
        $editAssetButton.Enabled = $false
        $deleteAssetButton.Enabled = $false
        return
    }

    $isManual = $record.Source -eq "Manual"
    $editAssetButton.Enabled = $isManual
    $deleteAssetButton.Enabled = $isManual

    $detailDisplayValue.Text = [string]$record.DisplayName
    $detailAssetValue.Text = [string]$record.AssetName
    $detailCategoryValue.Text = [string]$record.Category
    $detailSubValue.Text = [string]$record.SubCategory
    $detailSourceValue.Text = [string]$record.Source
    $detailSearchValue.Text = [string]$record.SearchTerms
    $detailNotesValue.Text = [string]$record.Notes
})

# ============================================================
# DETAILS COPY BUTTONS
# ============================================================

$copyDetailAssetButton.Add_Click({

    if (-not [string]::IsNullOrWhiteSpace($detailAssetValue.Text)) {

        [System.Windows.Forms.Clipboard]::SetText(
            $detailAssetValue.Text
        )
    }
})

$copyDisplayButton.Add_Click({

    if (-not [string]::IsNullOrWhiteSpace($detailDisplayValue.Text)) {

        [System.Windows.Forms.Clipboard]::SetText(
            $detailDisplayValue.Text
        )
    }
})

# ============================================================
# MANUAL ASSET ENTRY WINDOW
# ============================================================

function Show-AddAssetWindow {

    $addForm = New-Object System.Windows.Forms.Form
    $addForm.Text = "Add New Once Human Asset"
    $addForm.Size = New-Object System.Drawing.Size(620, 520)
    $addForm.StartPosition = "CenterParent"
    $addForm.FormBorderStyle = "FixedDialog"
    $addForm.MaximizeBox = $false
    $addForm.MinimizeBox = $false
    $addForm.BackColor = [System.Drawing.Color]::FromArgb(28, 30, 34)

    $titleLabel = New-Object System.Windows.Forms.Label
    $titleLabel.Text = "ADD NEW ASSET"
    $titleLabel.Location = New-Object System.Drawing.Point(20, 15)
    $titleLabel.Size = New-Object System.Drawing.Size(300, 30)
    $titleLabel.Font = New-Object System.Drawing.Font(
        "Segoe UI",
        14,
        [System.Drawing.FontStyle]::Bold
    )
    $titleLabel.ForeColor = [System.Drawing.Color]::FromArgb(80, 200, 255)
    $addForm.Controls.Add($titleLabel)

    $assetLabel = New-Object System.Windows.Forms.Label
    $assetLabel.Text = "Internal Asset Name"
    $assetLabel.Location = New-Object System.Drawing.Point(20, 60)
    $assetLabel.AutoSize = $true
    $assetLabel.ForeColor = [System.Drawing.Color]::White
    $addForm.Controls.Add($assetLabel)

    $assetBox = New-Object System.Windows.Forms.TextBox
    $assetBox.Location = New-Object System.Drawing.Point(20, 82)
    $assetBox.Size = New-Object System.Drawing.Size(560, 25)
    $addForm.Controls.Add($assetBox)

    $displayLabel = New-Object System.Windows.Forms.Label
    $displayLabel.Text = "Display Name"
    $displayLabel.Location = New-Object System.Drawing.Point(20, 120)
    $displayLabel.AutoSize = $true
    $displayLabel.ForeColor = [System.Drawing.Color]::White
    $addForm.Controls.Add($displayLabel)

    $displayBox = New-Object System.Windows.Forms.TextBox
    $displayBox.Location = New-Object System.Drawing.Point(20, 142)
    $displayBox.Size = New-Object System.Drawing.Size(560, 25)
    $addForm.Controls.Add($displayBox)

    $categoryLabel = New-Object System.Windows.Forms.Label
    $categoryLabel.Text = "Category"
    $categoryLabel.Location = New-Object System.Drawing.Point(20, 180)
    $categoryLabel.AutoSize = $true
    $categoryLabel.ForeColor = [System.Drawing.Color]::White
    $addForm.Controls.Add($categoryLabel)

    $categoryInput = New-Object System.Windows.Forms.ComboBox
    $categoryInput.Location = New-Object System.Drawing.Point(20, 202)
    $categoryInput.Size = New-Object System.Drawing.Size(265, 25)
    $categoryInput.DropDownStyle = "DropDown"

    $manualCategories = @(
        "Animal",
        "Deviant",
        "Equipment",
        "Interactive",
        "KeyItem",
        "Loot",
        "Misc",
        "Monster",
        "NPC",
        "Plant",
        "Resource",
        "Structure",
        "Vehicle",
        "Visual",
        "Weapon",
        "World",
        "Unknown"
    )

    foreach ($item in $manualCategories) {
        [void]$categoryInput.Items.Add($item)
    }

    $addForm.Controls.Add($categoryInput)

    $subCategoryLabel = New-Object System.Windows.Forms.Label
    $subCategoryLabel.Text = "SubCategory"
    $subCategoryLabel.Location = New-Object System.Drawing.Point(315, 180)
    $subCategoryLabel.AutoSize = $true
    $subCategoryLabel.ForeColor = [System.Drawing.Color]::White
    $addForm.Controls.Add($subCategoryLabel)

    $subCategoryInput = New-Object System.Windows.Forms.TextBox
    $subCategoryInput.Location = New-Object System.Drawing.Point(315, 202)
    $subCategoryInput.Size = New-Object System.Drawing.Size(265, 25)
    $addForm.Controls.Add($subCategoryInput)

    $notesLabel = New-Object System.Windows.Forms.Label
    $notesLabel.Text = "Notes"
    $notesLabel.Location = New-Object System.Drawing.Point(20, 245)
    $notesLabel.AutoSize = $true
    $notesLabel.ForeColor = [System.Drawing.Color]::White
    $addForm.Controls.Add($notesLabel)

    $notesInput = New-Object System.Windows.Forms.TextBox
    $notesInput.Location = New-Object System.Drawing.Point(20, 267)
    $notesInput.Size = New-Object System.Drawing.Size(560, 100)
    $notesInput.Multiline = $true
    $notesInput.ScrollBars = "Vertical"
    $addForm.Controls.Add($notesInput)

    $statusLabel = New-Object System.Windows.Forms.Label
    $statusLabel.Location = New-Object System.Drawing.Point(20, 380)
    $statusLabel.Size = New-Object System.Drawing.Size(560, 25)
    $statusLabel.ForeColor = [System.Drawing.Color]::Yellow
    $addForm.Controls.Add($statusLabel)

    $saveAssetButton = New-Object System.Windows.Forms.Button
    $saveAssetButton.Text = "Save Asset"
    $saveAssetButton.Location = New-Object System.Drawing.Point(20, 420)
    $saveAssetButton.Size = New-Object System.Drawing.Size(130, 35)
    $addForm.Controls.Add($saveAssetButton)

    $cancelAssetButton = New-Object System.Windows.Forms.Button
    $cancelAssetButton.Text = "Cancel"
    $cancelAssetButton.Location = New-Object System.Drawing.Point(160, 420)
    $cancelAssetButton.Size = New-Object System.Drawing.Size(100, 35)
    $addForm.Controls.Add($cancelAssetButton)

    $saveAssetButton.Add_Click({

        $assetName = $assetBox.Text.Trim()
        $displayName = $displayBox.Text.Trim()
        $category = $categoryInput.Text.Trim()
        $subCategory = $subCategoryInput.Text.Trim()
        $notes = $notesInput.Text.Trim()

        if ([string]::IsNullOrWhiteSpace($assetName)) {
            $statusLabel.Text = "Asset Name is required."
            $statusLabel.ForeColor = [System.Drawing.Color]::Red
            return
        }

        if ([string]::IsNullOrWhiteSpace($displayName)) {
            $displayName = $assetName -replace "_", " "
        }

        if ([string]::IsNullOrWhiteSpace($category)) {
            $category = "Unknown"
        }

        if ([string]::IsNullOrWhiteSpace($subCategory)) {
            $subCategory = "Manual"
        }

        $duplicate = @(
            $script:db | Where-Object {
                $_.AssetName -ieq $assetName
            }
        )

        if ($duplicate.Count -gt 0) {
            $result = [System.Windows.Forms.MessageBox]::Show(
                "An asset with this internal name already exists:`n`n$assetName`n`nAdd it anyway?",
                "Duplicate Asset",
                [System.Windows.Forms.MessageBoxButtons]::YesNo,
                [System.Windows.Forms.MessageBoxIcon]::Warning
            )

            if ($result -ne [System.Windows.Forms.DialogResult]::Yes) {
                return
            }
        }

        $searchTerms = @(
            $category
            $subCategory
            $assetName
            $displayName
            $notes
        ) -join ";"

        $newAsset = [PSCustomObject]@{
            Category    = $category
            SubCategory = $subCategory
            AssetName   = $assetName
            DisplayName = $displayName
            Source      = "Manual"
            SearchTerms = $searchTerms
            Notes       = $notes
        }

        try {
            $newAsset |
                Export-Csv `
                    -LiteralPath $manualDatabaseFile `
                    -Append `
                    -NoTypeInformation `
                    -Encoding UTF8 `
                    -ErrorAction Stop
        }
        catch {
            $statusLabel.Text = "Could not save the manual asset."
            $statusLabel.ForeColor = [System.Drawing.Color]::Red
            [System.Windows.Forms.MessageBox]::Show(
                "Could not save the manual asset:`n$($_.Exception.Message)",
                "Save Failed",
                [System.Windows.Forms.MessageBoxButtons]::OK,
                [System.Windows.Forms.MessageBoxIcon]::Error
            )
            return
        }

        try {
            Reload-AssetDatabase
        }
        catch {
            $statusLabel.Text = "Saved, but the database could not be reloaded."
            $statusLabel.ForeColor = [System.Drawing.Color]::Red
            [System.Windows.Forms.MessageBox]::Show(
                "The asset was written to manual_items.csv, but the combined database could not be reloaded:`n$($_.Exception.Message)",
                "Reload Failed",
                [System.Windows.Forms.MessageBoxButtons]::OK,
                [System.Windows.Forms.MessageBoxIcon]::Error
            )
            return
        }

        $statusLabel.Text = "Asset saved successfully."
        $statusLabel.ForeColor = [System.Drawing.Color]::LimeGreen

        [System.Windows.Forms.MessageBox]::Show(
            "Asset added successfully:`n`n$displayName`n$assetName",
            "Asset Added",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information
        )

        $addForm.Close()
    }.GetNewClosure())

    $cancelAssetButton.Add_Click({
        $addForm.Close()
    }.GetNewClosure())

    [void]$addForm.ShowDialog($form)
}

$addAssetButton.Add_Click({
    Show-AddAssetWindow
})

# ============================================================
# EDIT MANUAL ASSET WINDOW
# ============================================================

function Show-EditAssetWindow {

    $record = Get-SelectedAssetRecord

    if ($null -eq $record) {
        [System.Windows.Forms.MessageBox]::Show(
            "Select an asset first.",
            "Edit Manual Asset",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information
        )
        return
    }

    if ($record.Source -ne "Manual") {
        [System.Windows.Forms.MessageBox]::Show(
            "Only manually added assets can be edited.",
            "Protected Asset",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Warning
        )
        return
    }

    $originalAssetName = [string]$record.AssetName

    $editForm = New-Object System.Windows.Forms.Form
    $editForm.Text = "Edit Manual Asset"
    $editForm.Size = New-Object System.Drawing.Size(620, 520)
    $editForm.StartPosition = "CenterParent"
    $editForm.FormBorderStyle = "FixedDialog"
    $editForm.MaximizeBox = $false
    $editForm.MinimizeBox = $false
    $editForm.BackColor = [System.Drawing.Color]::FromArgb(28, 30, 34)

    $editTitle = New-Object System.Windows.Forms.Label
    $editTitle.Text = "EDIT MANUAL ASSET"
    $editTitle.Location = New-Object System.Drawing.Point(20, 15)
    $editTitle.Size = New-Object System.Drawing.Size(320, 30)
    $editTitle.Font = New-Object System.Drawing.Font(
        "Segoe UI",
        14,
        [System.Drawing.FontStyle]::Bold
    )
    $editTitle.ForeColor = [System.Drawing.Color]::FromArgb(80, 200, 255)
    $editForm.Controls.Add($editTitle)

    $editAssetLabel = New-Object System.Windows.Forms.Label
    $editAssetLabel.Text = "Internal Asset Name"
    $editAssetLabel.Location = New-Object System.Drawing.Point(20, 60)
    $editAssetLabel.AutoSize = $true
    $editAssetLabel.ForeColor = [System.Drawing.Color]::White
    $editForm.Controls.Add($editAssetLabel)

    $editAssetBox = New-Object System.Windows.Forms.TextBox
    $editAssetBox.Location = New-Object System.Drawing.Point(20, 82)
    $editAssetBox.Size = New-Object System.Drawing.Size(560, 25)
    $editAssetBox.Text = [string]$record.AssetName
    $editForm.Controls.Add($editAssetBox)

    $editDisplayLabel = New-Object System.Windows.Forms.Label
    $editDisplayLabel.Text = "Display Name"
    $editDisplayLabel.Location = New-Object System.Drawing.Point(20, 120)
    $editDisplayLabel.AutoSize = $true
    $editDisplayLabel.ForeColor = [System.Drawing.Color]::White
    $editForm.Controls.Add($editDisplayLabel)

    $editDisplayBox = New-Object System.Windows.Forms.TextBox
    $editDisplayBox.Location = New-Object System.Drawing.Point(20, 142)
    $editDisplayBox.Size = New-Object System.Drawing.Size(560, 25)
    $editDisplayBox.Text = [string]$record.DisplayName
    $editForm.Controls.Add($editDisplayBox)

    $editCategoryLabel = New-Object System.Windows.Forms.Label
    $editCategoryLabel.Text = "Category"
    $editCategoryLabel.Location = New-Object System.Drawing.Point(20, 180)
    $editCategoryLabel.AutoSize = $true
    $editCategoryLabel.ForeColor = [System.Drawing.Color]::White
    $editForm.Controls.Add($editCategoryLabel)

    $editCategoryBox = New-Object System.Windows.Forms.ComboBox
    $editCategoryBox.Location = New-Object System.Drawing.Point(20, 202)
    $editCategoryBox.Size = New-Object System.Drawing.Size(265, 25)
    $editCategoryBox.DropDownStyle = "DropDown"

    foreach ($category in @(
        "Animal", "Deviant", "Equipment", "Interactive", "KeyItem",
        "Loot", "Misc", "Monster", "NPC", "Plant", "Resource",
        "Structure", "Vehicle", "Visual", "Weapon", "World", "Unknown"
    )) {
        [void]$editCategoryBox.Items.Add($category)
    }

    $editCategoryBox.Text = [string]$record.Category
    $editForm.Controls.Add($editCategoryBox)

    $editSubLabel = New-Object System.Windows.Forms.Label
    $editSubLabel.Text = "SubCategory"
    $editSubLabel.Location = New-Object System.Drawing.Point(315, 180)
    $editSubLabel.AutoSize = $true
    $editSubLabel.ForeColor = [System.Drawing.Color]::White
    $editForm.Controls.Add($editSubLabel)

    $editSubBox = New-Object System.Windows.Forms.TextBox
    $editSubBox.Location = New-Object System.Drawing.Point(315, 202)
    $editSubBox.Size = New-Object System.Drawing.Size(265, 25)
    $editSubBox.Text = [string]$record.SubCategory
    $editForm.Controls.Add($editSubBox)

    $editNotesLabel = New-Object System.Windows.Forms.Label
    $editNotesLabel.Text = "Notes"
    $editNotesLabel.Location = New-Object System.Drawing.Point(20, 245)
    $editNotesLabel.AutoSize = $true
    $editNotesLabel.ForeColor = [System.Drawing.Color]::White
    $editForm.Controls.Add($editNotesLabel)

    $editNotesBox = New-Object System.Windows.Forms.TextBox
    $editNotesBox.Location = New-Object System.Drawing.Point(20, 267)
    $editNotesBox.Size = New-Object System.Drawing.Size(560, 100)
    $editNotesBox.Multiline = $true
    $editNotesBox.ScrollBars = "Vertical"
    $editNotesBox.Text = [string]$record.Notes
    $editForm.Controls.Add($editNotesBox)

    $editStatusLabel = New-Object System.Windows.Forms.Label
    $editStatusLabel.Location = New-Object System.Drawing.Point(20, 380)
    $editStatusLabel.Size = New-Object System.Drawing.Size(560, 25)
    $editStatusLabel.ForeColor = [System.Drawing.Color]::Yellow
    $editForm.Controls.Add($editStatusLabel)

    $saveEditButton = New-Object System.Windows.Forms.Button
    $saveEditButton.Text = "Save Changes"
    $saveEditButton.Location = New-Object System.Drawing.Point(20, 420)
    $saveEditButton.Size = New-Object System.Drawing.Size(130, 35)
    $editForm.Controls.Add($saveEditButton)

    $cancelEditButton = New-Object System.Windows.Forms.Button
    $cancelEditButton.Text = "Cancel"
    $cancelEditButton.Location = New-Object System.Drawing.Point(160, 420)
    $cancelEditButton.Size = New-Object System.Drawing.Size(100, 35)
    $editForm.Controls.Add($cancelEditButton)

    $saveEditButton.Add_Click({

        $newAssetName = $editAssetBox.Text.Trim()
        $newDisplayName = $editDisplayBox.Text.Trim()
        $newCategory = $editCategoryBox.Text.Trim()
        $newSubCategory = $editSubBox.Text.Trim()
        $newNotes = $editNotesBox.Text.Trim()

        if ([string]::IsNullOrWhiteSpace($newAssetName)) {
            $editStatusLabel.Text = "Asset Name is required."
            $editStatusLabel.ForeColor = [System.Drawing.Color]::Red
            return
        }

        if ([string]::IsNullOrWhiteSpace($newDisplayName)) {
            $newDisplayName = $newAssetName -replace "_", " "
        }

        if ([string]::IsNullOrWhiteSpace($newCategory)) {
            $newCategory = "Unknown"
        }

        if ([string]::IsNullOrWhiteSpace($newSubCategory)) {
            $newSubCategory = "Manual"
        }

        if ($newAssetName -ine $originalAssetName) {
            $nameConflict = @(
                $script:db | Where-Object {
                    $_.AssetName -ieq $newAssetName
                }
            )

            if ($nameConflict.Count -gt 0) {
                $editStatusLabel.Text = "That Asset Name already exists."
                $editStatusLabel.ForeColor = [System.Drawing.Color]::Red
                return
            }
        }

        try {
            $manualRecords = @(
                Import-Csv -LiteralPath $manualDatabaseFile -ErrorAction Stop
            )
        }
        catch {
            $editStatusLabel.Text = "Could not read the manual database."
            $editStatusLabel.ForeColor = [System.Drawing.Color]::Red
            [System.Windows.Forms.MessageBox]::Show(
                "Could not read manual_items.csv:`n$($_.Exception.Message)",
                "Edit Failed",
                [System.Windows.Forms.MessageBoxButtons]::OK,
                [System.Windows.Forms.MessageBoxIcon]::Error
            )
            return
        }

        $manualRecordIndex = Find-ManualRecordIndex `
            -Record $record `
            -ManualRecords $manualRecords

        if ($manualRecordIndex -lt 0) {
            $editStatusLabel.Text = "Manual record was not found."
            $editStatusLabel.ForeColor = [System.Drawing.Color]::Red
            return
        }

        $manualRecord = $manualRecords[$manualRecordIndex]
        $manualRecord.AssetName = $newAssetName
        $manualRecord.DisplayName = $newDisplayName
        $manualRecord.Category = $newCategory
        $manualRecord.SubCategory = $newSubCategory
        $manualRecord.Source = "Manual"
        $manualRecord.Notes = $newNotes
        $manualRecord.SearchTerms = @(
            $newCategory
            $newSubCategory
            $newAssetName
            $newDisplayName
            $newNotes
        ) -join ";"

        try {
            Save-ManualDatabase -Records $manualRecords
        }
        catch {
            $editStatusLabel.Text = "Could not save changes."
            $editStatusLabel.ForeColor = [System.Drawing.Color]::Red
            [System.Windows.Forms.MessageBox]::Show(
                "Could not save changes to manual_items.csv:`n$($_.Exception.Message)",
                "Save Failed",
                [System.Windows.Forms.MessageBoxButtons]::OK,
                [System.Windows.Forms.MessageBoxIcon]::Error
            )
            return
        }

        try {
            Reload-AssetDatabase
        }
        catch {
            $editStatusLabel.Text = "Saved, but the database could not be reloaded."
            $editStatusLabel.ForeColor = [System.Drawing.Color]::Red
            [System.Windows.Forms.MessageBox]::Show(
                "Changes were saved, but the combined database could not be reloaded:`n$($_.Exception.Message)",
                "Reload Failed",
                [System.Windows.Forms.MessageBoxButtons]::OK,
                [System.Windows.Forms.MessageBoxIcon]::Error
            )
            return
        }

        $editForm.Close()
        [System.Windows.Forms.MessageBox]::Show(
            "Manual asset updated successfully.",
            "Asset Updated",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information
        )
    }.GetNewClosure())

    $cancelEditButton.Add_Click({
        $editForm.Close()
    }.GetNewClosure())

    [void]$editForm.ShowDialog($form)
}

# ============================================================
# DELETE MANUAL ASSET
# ============================================================

function Remove-SelectedManualAsset {

    $record = Get-SelectedAssetRecord

    if ($null -eq $record) {
        [System.Windows.Forms.MessageBox]::Show(
            "Select an asset first.",
            "Delete Manual Asset",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information
        )
        return
    }

    if ($record.Source -ne "Manual") {
        [System.Windows.Forms.MessageBox]::Show(
            "Only manually added assets can be deleted.",
            "Protected Asset",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Warning
        )
        return
    }

    $confirmation = [System.Windows.Forms.MessageBox]::Show(
        "Delete this manual asset?`n`n$($record.DisplayName)`n$($record.AssetName)`n`nYou can undo this deletion until the GUI closes.",
        "Confirm Delete",
        [System.Windows.Forms.MessageBoxButtons]::YesNo,
        [System.Windows.Forms.MessageBoxIcon]::Warning
    )

    if ($confirmation -ne [System.Windows.Forms.DialogResult]::Yes) {
        return
    }

    try {
        $manualRecords = @(
            Import-Csv -LiteralPath $manualDatabaseFile -ErrorAction Stop
        )
    }
    catch {
        [System.Windows.Forms.MessageBox]::Show(
            "Could not read manual_items.csv:`n$($_.Exception.Message)",
            "Delete Failed",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        )
        return
    }

    $manualRecordIndex = Find-ManualRecordIndex `
        -Record $record `
        -ManualRecords $manualRecords

    if ($manualRecordIndex -lt 0) {
        [System.Windows.Forms.MessageBox]::Show(
            "The manual record could not be found.",
            "Delete Failed",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        )
        return
    }

    $remainingRecords = @(
        for ($index = 0; $index -lt $manualRecords.Count; $index++) {
            if ($index -ne $manualRecordIndex) {
                $manualRecords[$index]
            }
        }
    )

    try {
        Save-ManualDatabase -Records $remainingRecords
    }
    catch {
        [System.Windows.Forms.MessageBox]::Show(
            "Could not delete the manual asset:`n$($_.Exception.Message)",
            "Delete Failed",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        )
        return
    }

    $script:lastDeletedAsset = [PSCustomObject]@{
        Category    = [string]$record.Category
        SubCategory = [string]$record.SubCategory
        AssetName   = [string]$record.AssetName
        DisplayName = [string]$record.DisplayName
        Source      = "Manual"
        SearchTerms = [string]$record.SearchTerms
        Notes       = [string]$record.Notes
    }
    $undoDeleteButton.Enabled = $true

    try {
        Reload-AssetDatabase
    }
    catch {
        [System.Windows.Forms.MessageBox]::Show(
            "The asset was deleted, but the combined database could not be reloaded:`n$($_.Exception.Message)",
            "Reload Failed",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        )
        return
    }

    $grid.ClearSelection()
    [System.Windows.Forms.MessageBox]::Show(
        "Manual asset deleted successfully.",
        "Asset Deleted",
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Information
    )
}

function Undo-LastManualDelete {

    if ($null -eq $script:lastDeletedAsset) {
        [System.Windows.Forms.MessageBox]::Show(
            "There is no deleted manual asset to restore.",
            "Undo Delete",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information
        )
        return
    }

    $assetToRestore = $script:lastDeletedAsset
    $conflict = @(
        $script:db | Where-Object {
            $_.AssetName -ieq $assetToRestore.AssetName
        }
    )

    if ($conflict.Count -gt 0) {
        [System.Windows.Forms.MessageBox]::Show(
            "Cannot restore the deleted asset because an asset with the same internal name already exists:`n`n$($assetToRestore.AssetName)",
            "Undo Failed",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Warning
        )
        return
    }

    try {
        $manualRecords = @(
            Import-Csv -LiteralPath $manualDatabaseFile -ErrorAction Stop |
                Where-Object {
                    -not [string]::IsNullOrWhiteSpace($_.AssetName)
                }
        )

        Save-ManualDatabase -Records @(
            $manualRecords
            $assetToRestore
        )
    }
    catch {
        [System.Windows.Forms.MessageBox]::Show(
            "Could not restore the deleted manual asset:`n$($_.Exception.Message)",
            "Undo Failed",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        )
        return
    }

    try {
        Reload-AssetDatabase
    }
    catch {
        [System.Windows.Forms.MessageBox]::Show(
            "The asset was restored, but the combined database could not be reloaded:`n$($_.Exception.Message)",
            "Reload Failed",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        )
        return
    }

    $restoredName = [string]$assetToRestore.DisplayName
    $restoredAssetName = [string]$assetToRestore.AssetName
    $script:lastDeletedAsset = $null
    $undoDeleteButton.Enabled = $false

    [System.Windows.Forms.MessageBox]::Show(
        "Deletion undone successfully.`n`n$restoredName`n$restoredAssetName",
        "Asset Restored",
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Information
    )
}

$editAssetButton.Add_Click({
    Show-EditAssetWindow
})

$deleteAssetButton.Add_Click({
    Remove-SelectedManualAsset
})

$undoDeleteButton.Add_Click({
    Undo-LastManualDelete
})

# ============================================================
# INITIALIZE
# ============================================================

Update-SubCategoryList
Update-Results

[void]$form.ShowDialog()
