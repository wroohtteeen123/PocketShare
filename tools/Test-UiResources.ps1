$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$themeSource = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/io/pocketshare/PocketTheme.kt'))
if ($themeSource.Contains('applyStyle(R.style.BrandSurfaces')) { throw 'Brand overlay must not overwrite dynamic colors' }
foreach ($activityFile in @('MainActivity.kt', 'HelpActivity.kt', 'DirectoryPickerActivity.kt')) {
    $source = [IO.File]::ReadAllText((Join-Path $project "app/src/main/java/io/pocketshare/$activityFile"))
    if ($source.Contains('delegate.localNightMode')) { throw "$activityFile must inherit application night mode" }
}
$appearanceSource = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/io/pocketshare/AppearanceSettings.kt'))
if (!$appearanceSource.Contains('AppCompatDelegate.setDefaultNightMode(modes[selected])')) { throw 'Theme selection must update the active night mode' }
$res = Join-Path $project 'app/src/main/res'
$nightColors = [xml]([IO.File]::ReadAllText((Join-Path $res 'values-night/colors.xml')))
foreach ($token in @('brand_primarycontainer', 'brand_secondarycontainer', 'brand_surfacevariant', 'brand_container')) {
    if ($nightColors.SelectSingleNode("/resources/color[@name='$token']").InnerText -ne '#281E24') { throw 'Dark theme must retain the light palette pink card distribution' }
}
foreach ($token in @('brand_onprimarycontainer', 'brand_onsecondarycontainer')) {
    if ($nightColors.SelectSingleNode("/resources/color[@name='$token']").InnerText -ne '#F3E7ED') { throw 'Deep pink cards need light foreground text' }
}
Get-ChildItem $res -Recurse -Filter '*.xml' | ForEach-Object { [xml]([IO.File]::ReadAllText($_.FullName)) | Out-Null }
$zh = [xml]([IO.File]::ReadAllText((Join-Path $res 'values/strings.xml')))
$en = [xml]([IO.File]::ReadAllText((Join-Path $res 'values-en/strings.xml')))
$translated = @{}
foreach ($entry in $en.resources.string) { $translated[$entry.name] = $entry.InnerText }
foreach ($entry in $zh.resources.string) {
    if (!$translated.ContainsKey($entry.name)) { throw "Missing English: $($entry.name)" }
    $left = [regex]::Matches($entry.InnerText, '%\d+\$[sd]') | ForEach-Object Value | Sort-Object
    $right = [regex]::Matches($translated[$entry.name], '%\d+\$[sd]') | ForEach-Object Value | Sort-Object
    if (($left -join ',') -ne ($right -join ',')) { throw "Placeholder mismatch: $($entry.name)" }
}
if ($zh.resources.string.Count -ne $en.resources.string.Count) { throw 'Translation key count mismatch' }
$layout = [xml]([IO.File]::ReadAllText((Join-Path $res 'layout/activity_main.xml')))
$ns = [Xml.XmlNamespaceManager]::new($layout.NameTable)
$ns.AddNamespace('android', 'http://schemas.android.com/apk/res/android')
foreach ($id in @('home_page', 'settings_page', 'logs_page', 'archive_page')) {
    $page = $layout.SelectSingleNode("//*[@android:id='@+id/$id']", $ns)
    if ($page.GetAttribute('paddingTop', $ns.LookupNamespace('android')) -eq '56dp') { throw "$id has redundant title spacing" }
}
foreach ($key in @('nav_home', 'nav_settings', 'nav_logs')) {
    $title = $layout.SelectSingleNode("//TextView[@android:text='@string/$key']", $ns)
    if (!$title -or $title.GetAttribute('style') -ne '@style/PageTitle') { throw "$key must use PageTitle" }
}
foreach ($id in @('settings_page', 'logs_page')) {
    $node = $layout.SelectSingleNode("//*[@android:id='@+id/$id']", $ns)
    if ($node.GetAttribute('visibility', $ns.LookupNamespace('android')) -ne 'gone') { throw "$id must start hidden" }
}
$accountPanel = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/io/pocketshare/AccountPanel.kt'))
if (!$accountPanel.Contains('isSaveEnabled = false')) { throw 'Account password must not be saved to view state' }
if (!$layout.SelectSingleNode("//*[@android:id='@+id/manage_accounts']", $ns)) { throw 'Missing account management entry' }
foreach ($file in Get-ChildItem (Join-Path $res 'layout') -Filter '*.xml') {
    $raw = [IO.File]::ReadAllText($file.FullName)
    if ($raw -match '(?:text|hint|helperText|contentDescription)="[^"@]*[\u4e00-\u9fff]') { throw "Unlocalized UI text in $($file.Name)" }
}
Write-Output "PASS: XML parsing, $($zh.resources.string.Count) bilingual strings, format arguments, hidden pages, password state and UI text extraction."
$mainText = [IO.File]::ReadAllText((Join-Path $res 'layout/activity_main.xml'))
$qrActivity = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/io/pocketshare/MainActivity.kt'))
if ($qrActivity.Contains('R.color.brand_qr_ink')) { throw 'QR ink must follow the active theme' }
if (!$qrActivity.Contains('MaterialColors.getColor(qrCode, com.google.android.material.R.attr.colorPrimary)') -or !$qrActivity.Contains('qrInk != lastQrInk')) { throw 'QR must resolve and invalidate its cached theme color' }
$settingsText = [IO.File]::ReadAllText((Join-Path $res 'layout/settings_support.xml'))
if ($mainText.Contains('@drawable/pocketshare_logo') -or $settingsText.Contains('@drawable/pocketshare_logo')) { throw 'Page logo regression' }
if ($mainText.Contains('@layout/settings_appearance')) { throw 'Appearance must be in the help panel' }
if (!$mainText.Contains('@+id/open_help') -or !$mainText.Contains('@+id/choose_speed_interval')) { throw 'Missing help or speed control' }
$service = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/io/pocketshare/SmbService.kt'))
if ($service.Contains('setLargeIcon')) { throw 'Duplicate notification icon regression' }
foreach ($license in @('GPL-3.0.txt', 'Apache-2.0.txt', 'native-components.txt')) {
    if (!(Test-Path (Join-Path $project "app/src/main/assets/licenses/$license"))) { throw "Missing $license" }
}
Write-Output 'PASS: simplified pages, help entry, refresh control, single notification icon and offline licenses.'
$helpLayout = [xml]([IO.File]::ReadAllText((Join-Path $res 'layout/activity_help.xml')))
$helpNs = [Xml.XmlNamespaceManager]::new($helpLayout.NameTable)
$helpNs.AddNamespace('android', 'http://schemas.android.com/apk/res/android')
$helpButton = $layout.SelectSingleNode("//*[@android:id='@+id/open_help']", $ns)
$backButton = $helpLayout.SelectSingleNode("//*[@android:id='@+id/back_help']", $helpNs)
if ($helpButton.ParentNode.Name -ne 'FrameLayout') { throw 'Help button must float over pages without an opaque toolbar' }
if ($helpButton.GetAttribute('style') -ne '@style/CornerAction' -or $backButton.GetAttribute('style') -ne '@style/CornerAction') { throw 'Help and back must share position/size style' }
$records = $layout.SelectSingleNode("//*[@android:text='@string/records_title']", $ns)
if ($records.HasAttribute('textColor', $ns.LookupNamespace('android')) -or $records.GetAttribute('style') -ne '@style/SettingsSectionToggle') { throw 'Records title must use the shared collapsible heading color' }
$helpSource = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/io/pocketshare/AppearanceSettings.kt'))
if ($helpSource.Contains('helpDialog') -or !$helpSource.Contains('HelpActivity::class.java')) { throw 'Help must be a standalone activity' }
$palette = [xml]([IO.File]::ReadAllText((Join-Path $res 'values/colors.xml')))
foreach ($name in @('brand_primarycontainer', 'brand_secondarycontainer', 'brand_surfacevariant', 'brand_container')) {
    if (($palette.resources.color | Where-Object name -eq $name).InnerText -ne '@color/logo_background') { throw "Brand pink mismatch: $name" }
}
Write-Output 'PASS: fullscreen help navigation, matching floating buttons, consistent records title and shared brand-pink surfaces.'
$directoryField = $layout.SelectSingleNode("//*[@android:id='@+id/share_directory']", $ns)
if ($directoryField.Name -ne 'com.google.android.material.textfield.TextInputEditText') { throw 'Directory must be directly editable' }
if ($directoryField.ParentNode.GetAttribute('id', $ns.LookupNamespace('android')) -ne '@+id/directory_input') { throw 'Directory input must support inline errors' }
if ($layout.SelectSingleNode("//*[@android:id='@+id/manual_directory']", $ns)) { throw 'Separate manual directory button must be removed' }
Write-Output 'PASS: unified editable directory field and no separate manual-input button.'
$archivePage = $layout.SelectSingleNode("//*[@android:id='@+id/archive_page']", $ns)
if (!$archivePage -or $archivePage.GetAttribute('visibility', $ns.LookupNamespace('android')) -ne 'gone') { throw 'Archive page must exist and start hidden' }
foreach ($file in @('XZ-Java.txt', 'Commons-NOTICE.txt')) {
    if (!(Test-Path (Join-Path $project "app/src/main/assets/licenses/$file"))) { throw "Missing license: $file" }
}
Write-Output 'PASS: fourth archive page and compression component notices.'
$sambaSource = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/io/pocketshare/SmbService.kt'))
foreach ($setting in @('getBoolean("directory_cache", false)', 'kernel change notify = yes', 'change notify = yes')) {
    if (!$sambaSource.Contains($setting)) { throw "Missing local-writer SMB refresh setting: $setting" }
}
if ($sambaSource.Contains('smb2 leases = no')) { throw 'File leases should not be disabled by the directory refresh fix' }
$cacheSwitch = $layout.SelectSingleNode("//*[@android:id='@+id/directory_cache']", $ns)
if ($cacheSwitch.Name -ne 'com.google.android.material.materialswitch.MaterialSwitch') { throw 'Directory cache must be a switch' }
$archiveSource = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/io/pocketshare/AutoArchivePage.kt'))
if (!$archiveSource.Contains('findViewById<MaterialSwitch>(R.id.archive_monitor)') -or $archiveSource.Contains('button(R.string.archive_start)') -or $archiveSource.Contains('button(R.string.archive_stop)')) { throw 'Monitoring must use one switch instead of two buttons' }
$archiveLayout = [xml]([IO.File]::ReadAllText((Join-Path $res 'layout/archive_panel.xml')))
foreach ($style in @('PageTitle', 'PageCaption', 'DashboardCard', 'SecondaryAction', 'LogDashboardCard', 'LogContent')) {
    if (!$archiveLayout.SelectSingleNode("//*[@style='@style/$style']")) { throw "Archive page missing shared style: $style" }
}
Write-Output 'PASS: archive page uses shared title, cards, controls and log styles.'
Write-Output 'PASS: monitoring/cache switches, caching off by default, notifications enabled and file leases retained.'

$accountsLayout = [xml]([IO.File]::ReadAllText((Join-Path $res 'layout/activity_accounts.xml')))
foreach ($style in @('PageTitle', 'PageCaption', 'DashboardCard', 'PrimaryAction', 'CornerAction')) {
    if (!$accountsLayout.SelectSingleNode("//*[@style='@style/$style']")) { throw "Accounts page missing shared style: $style" }
}
$accountsSource = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/io/pocketshare/AccountPanel.kt'))
if (!$accountsSource.Contains('Intent(activity, AccountsActivity::class.java)') -or $accountsSource.Contains('.setItems(labels)')) { throw 'Account list must use the separate activity, not a dialog' }
$manifestSource = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/AndroidManifest.xml'))
if (!$manifestSource.Contains('<activity android:name=".AccountsActivity" android:exported="false" />')) { throw 'Accounts activity must remain internal' }
Write-Output 'PASS: independent accounts page, shared styles and internal activity registration.'

$pickerHeader = [xml]([IO.File]::ReadAllText((Join-Path $res 'layout/directory_picker_header.xml')))
foreach ($style in @('PageCaption', 'PickerIconAction')) {
    if (!$pickerHeader.SelectSingleNode("//*[@style='@style/$style']")) { throw "Directory picker missing shared style: $style" }
}
$pickerLayout = [xml]([IO.File]::ReadAllText((Join-Path $res 'layout/activity_directory_picker.xml')))
if (!$pickerLayout.SelectSingleNode("//*[@style='@style/CornerAction']")) { throw 'Directory picker missing corner back button' }
foreach ($style in @('PageTitle', 'DashboardCard')) {
    if (!$pickerLayout.SelectSingleNode("//*[@style='@style/$style']")) { throw "Picker missing $style" }
}
$pickerActions = $pickerHeader.SelectNodes("//*[@style='@style/PickerIconAction']")
if ($pickerActions.Count -ne 3) { throw 'Picker must have three circular action buttons' }
$pickerRow = [xml]([IO.File]::ReadAllText((Join-Path $res 'layout/directory_picker_row.xml')))
if ($pickerRow.DocumentElement.Name -ne 'TextView') { throw 'Directory rows must not have individual cards' }
$pickerSource = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/io/pocketshare/DirectoryPickerActivity.kt'))
if ($pickerSource.Contains('android.R.layout.simple_list_item_1') -or !$pickerSource.Contains('list.addHeaderView(header, null, false)')) { throw 'Picker must use themed rows and a scrolling header' }
Write-Output 'PASS: directory picker shared styles, scrolling controls and themed reusable rows.'
