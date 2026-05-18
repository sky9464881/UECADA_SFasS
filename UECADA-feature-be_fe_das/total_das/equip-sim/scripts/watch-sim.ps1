param(
    [string]$Title = 'SIM Watch'
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function U([int[]]$Codes) {
    return (-join ($Codes | ForEach-Object { [char]::ConvertFromUtf32($_) }))
}

$TXT = @{
    WindowTitle     = U @(0xB85C,0xCEEC,0x20,0xC124,0xBE44,0x20,0xAD00,0xC81C,0x20,0xC2DC,0xC2A4,0xD15C)
    CastLabel       = U @(0xC8FC,0xC870,0xAE30)
    CncLabel        = U @(0xAC00,0xACF5,0xAE30)
    WashLabel       = U @(0xC138,0xCC99,0xAE30)
    AssyLabel       = U @(0xC870,0xB9BD,0xAE30)
    TestLabel       = U @(0xAC80,0xC0AC,0xAE30)
    Run             = 'RUN'
    Stop            = 'STOP'
    Open            = U @(0xC5F4,0xAE30)
    Refresh         = U @(0xC0C8,0xB85C,0xACE0,0xCE68)
    Close           = U @(0xB2EB,0xAE30)
    PickTarget      = U @(0xB300,0xC0C1,0x20,0xC124,0xBE44,0xB97C,0x20,0xC120,0xD0DD,0xD558,0xC138,0xC694,0x2E)
    NotRunning      = U @(0xC2E4,0xD589,0x20,0xC911,0xC778,0x20,0xCEE8,0xD14C,0xC774,0xB108,0xAC00,0x20,0xC544,0xB2D9,0xB2C8,0xB2E4,0x3A)
    OpenFail        = U @(0xC2E4,0xD589,0x20,0xCC3D,0x20,0xC5F4,0xAE30,0x20,0xC2E4,0xD328,0x3A)
    OpenHint        = U @(0xB354,0xBE14,0xD074,0xB9AD,0x20,0xB610,0xB294,0x20,0xC5F4,0xAE30,0x20,0xBC84,0xD2BC,0xC73C,0xB85C,0x20,0xAD00,0xC81C,0x20,0xCC3D,0xC744,0x20,0xC5FD,0xB2C8,0xB2E4,0x2E)
    PreviewPrefix   = 'Container: '
    NoSelection     = U @(0xBBF8,0xC120,0xD0DD)
}

$regularFont = New-Object System.Drawing.Font('Malgun Gothic', 9, [System.Drawing.FontStyle]::Regular)
$boldFont    = New-Object System.Drawing.Font('Malgun Gothic', 9, [System.Drawing.FontStyle]::Bold)

function Get-LabelMap {
    return @{
        CAST = $TXT.CastLabel
        CNC  = $TXT.CncLabel
        WASH = $TXT.WashLabel
        ASSY = $TXT.AssyLabel
        TEST = $TXT.TestLabel
    }
}

function Get-ShortLine([string]$Line) {
    if ($Line -match '^LINE-(\d+)$') { return $Matches[1] }
    return $Line
}

function Get-LineNumber([string]$Line) {
    if ($Line -match '^(\d+)$') { return [int]$Matches[1] }
    return 0
}

function Get-StatusOrder([string]$Status) {
    switch ($Status) {
        'RUN'  { return 0 }
        'STOP' { return 1 }
        default { return 9 }
    }
}

function Get-EquipmentOrder([string]$EquipmentCode) {
    switch ($EquipmentCode) {
        'CAST' { return 0 }
        'CNC'  { return 1 }
        'WASH' { return 2 }
        'ASSY' { return 3 }
        'TEST' { return 4 }
        default { return 9 }
    }
}

function Get-SimTargets {
    $labels = Get-LabelMap
    $defs = @(
        @{ EquipmentCode = 'CAST'; Numbers = @('01') },
        @{ EquipmentCode = 'CNC';  Numbers = @('01','02','03') },
        @{ EquipmentCode = 'WASH'; Numbers = @('01') },
        @{ EquipmentCode = 'ASSY'; Numbers = @('01','02') },
        @{ EquipmentCode = 'TEST'; Numbers = @('01','02') }
    )

    foreach ($line in @('LINE-01','LINE-02','LINE-03')) {
        foreach ($def in $defs) {
            foreach ($no in $def.Numbers) {
                $code = $def.EquipmentCode
                [pscustomobject]@{
                    Line          = $line
                    LineShort     = Get-ShortLine $line
                    EquipmentType = $labels[$code]
                    No            = $no
                    EquipmentCode = $code
                    Container     = "${line}_${code}-${no}"
                }
            }
        }
    }
}

function Get-RunningContainersMap {
    $map = @{}
    $names = docker ps --format '{{.Names}}' 2>$null
    foreach ($name in $names) {
        $n = $name.Trim()
        if ($n) { $map[$n] = $true }
    }
    return $map
}

function Get-GridRows {
    $targets = Get-SimTargets
    $running = Get-RunningContainersMap

    foreach ($t in $targets) {
        $status = $TXT.Stop
        if ($running.ContainsKey($t.Container)) { $status = $TXT.Run }
        [pscustomobject]@{
            Status        = $status
            Line          = $t.LineShort
            EquipmentType = $t.EquipmentType
            No            = $t.No
            EquipmentCode = $t.EquipmentCode
            Container     = $t.Container
        }
    }
}

function Invoke-TuiWindow {
    param([Parameter(Mandatory=$true)][string]$Container)

    $exists = docker ps --format '{{.Names}}' | Select-String -SimpleMatch $Container
    if (-not $exists) {
        [System.Windows.Forms.MessageBox]::Show(($TXT.NotRunning + ' ' + $Container), $TXT.WindowTitle, 'OK', 'Warning') | Out-Null
        return
    }

    $escapedContainer = $Container.Replace("'","''")
    $command = @"
`$Host.UI.RawUI.WindowTitle = 'SIM TUI - $escapedContainer'; cmd /c mode con: cols=30 lines=28 > `$null; docker exec -it $escapedContainer python -m sim.tui_wrapper; exit
"@

    try {
        Start-Process -FilePath 'powershell.exe' -ArgumentList @(
            '-NoLogo',
            '-ExecutionPolicy', 'Bypass',
            '-Command', $command
        ) | Out-Null
    }
    catch {
        [System.Windows.Forms.MessageBox]::Show(($TXT.OpenFail + ' ' + $_.Exception.Message), $TXT.WindowTitle, 'OK', 'Error') | Out-Null
    }
}

$form = New-Object System.Windows.Forms.Form
$form.Text = $TXT.WindowTitle
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $true
$form.ClientSize = New-Object System.Drawing.Size(420, 410)
$form.MinimumSize = New-Object System.Drawing.Size(436, 449)
$form.MaximumSize = New-Object System.Drawing.Size(436, 449)
$form.TopMost = $false
$form.Font = $regularFont

$grid = New-Object System.Windows.Forms.DataGridView
$grid.Location = New-Object System.Drawing.Point(10, 10)
$grid.Size = New-Object System.Drawing.Size(400, 285)
$grid.ReadOnly = $true
$grid.MultiSelect = $false
$grid.SelectionMode = 'FullRowSelect'
$grid.AllowUserToAddRows = $false
$grid.AllowUserToDeleteRows = $false
$grid.AllowUserToResizeRows = $false
$grid.AllowUserToResizeColumns = $false
$grid.RowHeadersVisible = $false
$grid.AutoSizeColumnsMode = 'Fill'
$grid.BackgroundColor = [System.Drawing.Color]::White
$grid.BorderStyle = 'FixedSingle'
$grid.ColumnHeadersHeightSizeMode = 'DisableResizing'
$grid.ColumnHeadersHeight = 28
$grid.RowTemplate.Height = 26
$grid.EnableHeadersVisualStyles = $false
$grid.DefaultCellStyle.Font = $regularFont
$grid.ColumnHeadersDefaultCellStyle.BackColor = [System.Drawing.Color]::FromArgb(243,244,246)
$grid.ColumnHeadersDefaultCellStyle.ForeColor = [System.Drawing.Color]::FromArgb(31,41,55)
$grid.ColumnHeadersDefaultCellStyle.Font = $boldFont
$grid.DefaultCellStyle.SelectionBackColor = [System.Drawing.Color]::FromArgb(219,234,254)
$grid.DefaultCellStyle.SelectionForeColor = [System.Drawing.Color]::FromArgb(17,24,39)

$labelInfo = New-Object System.Windows.Forms.Label
$labelInfo.Text = $TXT.OpenHint
$labelInfo.Location = New-Object System.Drawing.Point(10, 302)
$labelInfo.Size = New-Object System.Drawing.Size(400, 28)
$labelInfo.AutoSize = $false
$labelInfo.TextAlign = 'MiddleLeft'
$labelInfo.ForeColor = [System.Drawing.Color]::FromArgb(75,85,99)

$labelPreview = New-Object System.Windows.Forms.Label
$labelPreview.Text = $TXT.PreviewPrefix + $TXT.NoSelection
$labelPreview.Location = New-Object System.Drawing.Point(10, 334)
$labelPreview.Size = New-Object System.Drawing.Size(312, 28)
$labelPreview.AutoEllipsis = $true
$labelPreview.BorderStyle = 'FixedSingle'
$labelPreview.TextAlign = 'MiddleLeft'
$labelPreview.BackColor = [System.Drawing.Color]::FromArgb(249,250,251)
$labelPreview.ForeColor = [System.Drawing.Color]::FromArgb(55,65,81)
$labelPreview.Padding = New-Object System.Windows.Forms.Padding(6,0,6,0)

$btnOpen = New-Object System.Windows.Forms.Button
$btnOpen.Text = $TXT.Open
$btnOpen.Location = New-Object System.Drawing.Point(10, 370)
$btnOpen.Size = New-Object System.Drawing.Size(78, 30)

$btnRefresh = New-Object System.Windows.Forms.Button
$btnRefresh.Text = $TXT.Refresh
$btnRefresh.Location = New-Object System.Drawing.Point(94, 370)
$btnRefresh.Size = New-Object System.Drawing.Size(78, 30)

$btnClose = New-Object System.Windows.Forms.Button
$btnClose.Text = $TXT.Close
$btnClose.Location = New-Object System.Drawing.Point(332, 370)
$btnClose.Size = New-Object System.Drawing.Size(78, 30)

function Get-BaseRowColors([int]$LineNo) {
    if ($LineNo % 2 -eq 0) {
        return @{
            BackColor = [System.Drawing.Color]::FromArgb(243,244,246)
            SelectionBackColor = [System.Drawing.Color]::FromArgb(226,232,240)
        }
    }
    return @{
        BackColor = [System.Drawing.Color]::White
        SelectionBackColor = [System.Drawing.Color]::FromArgb(219,234,254)
    }
}

function Get-StatusColors([string]$Status, [int]$LineNo) {
    if ($Status -eq $TXT.Run) {
        if ($LineNo % 2 -eq 0) {
            return @{
                BackColor = [System.Drawing.Color]::FromArgb(240,253,244)
                ForeColor = [System.Drawing.Color]::FromArgb(22,101,52)
                SelectionBackColor = [System.Drawing.Color]::FromArgb(187,247,208)
                SelectionForeColor = [System.Drawing.Color]::FromArgb(20,83,45)
            }
        }
        return @{
            BackColor = [System.Drawing.Color]::FromArgb(220,252,231)
            ForeColor = [System.Drawing.Color]::FromArgb(22,101,52)
            SelectionBackColor = [System.Drawing.Color]::FromArgb(187,247,208)
            SelectionForeColor = [System.Drawing.Color]::FromArgb(20,83,45)
        }
    }

    if ($LineNo % 2 -eq 0) {
        return @{
            BackColor = [System.Drawing.Color]::FromArgb(254,242,242)
            ForeColor = [System.Drawing.Color]::FromArgb(153,27,27)
            SelectionBackColor = [System.Drawing.Color]::FromArgb(254,202,202)
            SelectionForeColor = [System.Drawing.Color]::FromArgb(127,29,29)
        }
    }
    return @{
        BackColor = [System.Drawing.Color]::FromArgb(254,226,226)
        ForeColor = [System.Drawing.Color]::FromArgb(127,29,29)
        SelectionBackColor = [System.Drawing.Color]::FromArgb(252,165,165)
        SelectionForeColor = [System.Drawing.Color]::FromArgb(127,29,29)
    }
}

function Update-Preview {
    if ($grid.SelectedRows.Count -gt 0) {
        $container = [string]$grid.SelectedRows[0].Cells['Container'].Value
        $labelPreview.Text = $TXT.PreviewPrefix + $container
    }
    else {
        $labelPreview.Text = $TXT.PreviewPrefix + $TXT.NoSelection
    }
}

function Apply-RowStyles {
    foreach ($row in $grid.Rows) {
        $lineText = [string]$row.Cells['Line'].Value
        $lineNo = Get-LineNumber $lineText
        $rowColors = Get-BaseRowColors $lineNo
        $statusColors = Get-StatusColors ([string]$row.Cells['Status'].Value) $lineNo

        $row.DefaultCellStyle.BackColor = $rowColors.BackColor
        $row.DefaultCellStyle.ForeColor = [System.Drawing.Color]::FromArgb(17,24,39)
        $row.DefaultCellStyle.SelectionBackColor = $rowColors.SelectionBackColor
        $row.DefaultCellStyle.SelectionForeColor = [System.Drawing.Color]::FromArgb(17,24,39)
        $row.DefaultCellStyle.Font = $regularFont

        $row.Cells['Status'].Style.BackColor = $statusColors.BackColor
        $row.Cells['Status'].Style.ForeColor = $statusColors.ForeColor
        $row.Cells['Status'].Style.SelectionBackColor = $statusColors.SelectionBackColor
        $row.Cells['Status'].Style.SelectionForeColor = $statusColors.SelectionForeColor
    }

    if ($grid.SelectedRows.Count -gt 0) {
        $selected = $grid.SelectedRows[0]
        $selected.DefaultCellStyle.Font = $boldFont
    }

    $grid.Refresh()
}

function Refresh-Grid {
    $rows = Get-GridRows | Sort-Object `
        @{ Expression = { Get-StatusOrder $_.Status }; Ascending = $true },
        @{ Expression = { [int]$_.Line }; Ascending = $true },
        @{ Expression = { Get-EquipmentOrder $_.EquipmentCode }; Ascending = $true },
        @{ Expression = { [int]$_.No }; Ascending = $true }

    $table = New-Object System.Data.DataTable
    [void]$table.Columns.Add('Status')
    [void]$table.Columns.Add('Line')
    [void]$table.Columns.Add('Equipment Type')
    [void]$table.Columns.Add('No')
    [void]$table.Columns.Add('EquipmentCode')
    [void]$table.Columns.Add('Container')

    foreach ($r in $rows) {
        $row = $table.NewRow()
        $row['Status'] = $r.Status
        $row['Line'] = $r.Line
        $row['Equipment Type'] = $r.EquipmentType
        $row['No'] = $r.No
        $row['EquipmentCode'] = $r.EquipmentCode
        $row['Container'] = $r.Container
        [void]$table.Rows.Add($row)
    }

    $grid.DataSource = $table
}

$grid.Add_DataBindingComplete({
    $grid.Columns['EquipmentCode'].Visible = $false
    $grid.Columns['Container'].Visible = $false
    $grid.Columns['Status'].FillWeight = 20
    $grid.Columns['Line'].FillWeight = 12
    $grid.Columns['Equipment Type'].FillWeight = 50
    $grid.Columns['No'].FillWeight = 10
    $grid.Columns['Status'].DefaultCellStyle.Alignment = 'MiddleCenter'
    $grid.Columns['Line'].DefaultCellStyle.Alignment = 'MiddleCenter'
    $grid.Columns['No'].DefaultCellStyle.Alignment = 'MiddleCenter'

    if ($grid.Rows.Count -gt 0) {
        $grid.ClearSelection()
        $grid.Rows[0].Selected = $true
        $grid.CurrentCell = $grid.Rows[0].Cells['Status']
    }

    Apply-RowStyles
    Update-Preview
})

$btnRefresh.Add_Click({ Refresh-Grid })
$btnClose.Add_Click({ $form.Close() })
$btnOpen.Add_Click({
    if ($grid.SelectedRows.Count -eq 0) {
        [System.Windows.Forms.MessageBox]::Show($TXT.PickTarget, $TXT.WindowTitle, 'OK', 'Information') | Out-Null
        return
    }
    $container = [string]$grid.SelectedRows[0].Cells['Container'].Value
    Invoke-TuiWindow -Container $container
    Refresh-Grid
})

$grid.Add_CellDoubleClick({
    if ($grid.SelectedRows.Count -gt 0) {
        $container = [string]$grid.SelectedRows[0].Cells['Container'].Value
        Invoke-TuiWindow -Container $container
        Refresh-Grid
    }
})

$grid.Add_SelectionChanged({
    Update-Preview
    Apply-RowStyles
})

$form.Controls.Add($grid)
$form.Controls.Add($labelInfo)
$form.Controls.Add($labelPreview)
$form.Controls.Add($btnOpen)
$form.Controls.Add($btnRefresh)
$form.Controls.Add($btnClose)

try {
    chcp 65001 | Out-Null
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {}

Refresh-Grid
[void]$form.ShowDialog()