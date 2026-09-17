param([Parameter(Mandatory=$true)][string]$BaoshiProject)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$out=Join-Path ([IO.Path]::GetTempPath()) ('cuilian-move-test-'+[Guid]::NewGuid())
New-Item -ItemType Directory -Path $out | Out-Null
$cp=(@('LLib.jar','spigot-1.12.2-R0.1-SNAPSHOT-b1648.jar','AttributePlus-3.3.3.0.jar','SX-Attribute-3.6.4.jar') | ForEach-Object { Join-Path $project "lib\$_" }) -join ';'
$sources=@(Get-ChildItem (Join-Path $project 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources+=@(Get-ChildItem (Join-Path $BaoshiProject 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources+=Join-Path $PSScriptRoot 'movelevel\MoveLevelRegression.java'
Push-Location $project
try {
    & javac -encoding UTF-8 --release 8 -cp $cp -d $out $sources
    if($LASTEXITCODE -ne 0){throw 'Compile failed'}
    & java '-Dfile.encoding=UTF-8' -cp "$out;$cp" lvhaoxuan.custom.cuilian.movelevel.MoveLevelRegression $BaoshiProject
    if($LASTEXITCODE -ne 0){throw 'MoveLevel regression failed'}
    Write-Output "Test output: $out"
} finally { Pop-Location }
