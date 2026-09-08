param([string]$SuperFurnace = (Join-Path $PSScriptRoot '..\..\SuperFurnace'))
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$api = Join-Path $project 'lib\spigot-1.12.2-R0.1-SNAPSHOT-b1648.jar'
$testOutput = Join-Path ([IO.Path]::GetTempPath()) ('cuilian-furnace-test-' + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $testOutput | Out-Null
$sources = @(Get-ChildItem -LiteralPath $PSScriptRoot -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem -LiteralPath (Join-Path $SuperFurnace 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += Join-Path $project 'src\lvhaoxuan\custom\cuilian\listener\FurnaceListener.java'
& javac -encoding UTF-8 -source 8 -target 8 -cp $api -d $testOutput $sources
if ($LASTEXITCODE -ne 0) { throw '回归测试编译失败' }
& java -cp "$testOutput;$api" FurnaceRegression
if ($LASTEXITCODE -ne 0) { throw '熔炉回归测试失败' }
Write-Output "测试编译输出：$testOutput"
