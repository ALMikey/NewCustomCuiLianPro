@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem ============ 依赖 classpath（按 lib 目录实际文件名） ============
set CP=lib\LLib.jar;lib\spigot-1.12.2-R0.1-SNAPSHOT-b1648.jar;lib\AttributePlus-3.3.3.0.jar;lib\SX-Attribute-3.6.4.jar
set BUILD=build_out
set OUTJAR=dist\NewCustomCuiLianPro.jar
set VERSION=4.4.21

rem ============ 需要 JDK（javac / jar 在 PATH 上） ============
where javac >nul 2>nul
if errorlevel 1 (
    echo [错误] 找不到 javac，请改用 NetBeans 的「清理并构建项目」(F11)。
    exit /b 1
)

if exist "%BUILD%" rmdir /s /q "%BUILD%"
mkdir "%BUILD%"

rem ============ 收集源码并编译（target 1.8） ============
dir /s /b src\*.java > srcs.txt
javac -encoding UTF-8 -nowarn -source 1.8 -target 1.8 -cp "%CP%" -d "%BUILD%" @srcs.txt
if errorlevel 1 (
    echo [错误] 编译失败，请检查上方报错。
    del srcs.txt
    exit /b 1
)

rem ============ 拷贝资源 ============
copy src\*.yml "%BUILD%\" >nul

rem ============ 打包（jar 会自动生成最小 manifest） ============
pushd "%BUILD%"
jar cf "..\%OUTJAR%" .
popd

del srcs.txt

if not exist "dist2" mkdir dist2
copy "%OUTJAR%" "dist2\NewCustomCuiLianPro-%VERSION%.jar" >nul

echo.
echo [完成] %OUTJAR%
echo [完成] dist2\NewCustomCuiLianPro-%VERSION%.jar
