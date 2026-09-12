$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$testOutput = Join-Path ([IO.Path]::GetTempPath()) ('cuilian-feature-test-' + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $testOutput | Out-Null
$cp = @('LLib.jar','spigot-1.12.2-R0.1-SNAPSHOT-b1648.jar','AttributePlus-3.3.3.0.jar','SX-Attribute-3.6.4.jar') | ForEach-Object { Join-Path $project "lib\$_" }
$classpath = $cp -join ';'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $project 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += Join-Path $PSScriptRoot 'features\FeatureRegression.java'
& javac -encoding UTF-8 --release 8 -cp $classpath -d $testOutput $sources
if ($LASTEXITCODE -ne 0) { throw '功能回归编译失败' }
& java -cp "$testOutput;$classpath" FeatureRegression (Join-Path $project 'src')
if ($LASTEXITCODE -ne 0) { throw '功能回归失败' }
Write-Output "测试编译输出：$testOutput"
