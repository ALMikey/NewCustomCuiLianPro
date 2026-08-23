@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem ============ Dependency classpath ============
set CP=lib\LLib.jar;lib\spigot-1.12.2-R0.1-SNAPSHOT-b1648.jar;lib\AttributePlus-3.3.3.0.jar;lib\SX-Attribute-3.6.4.jar
set BUILD=build_out
set OUTJAR=dist\NewCustomCuiLianPro.jar
set VERSION=4.4.27

rem ============ JDK required on PATH ============
where javac >nul 2>nul
if errorlevel 1 (
    echo [ERROR] javac was not found on PATH.
    exit /b 1
)

if exist "%BUILD%" rmdir /s /q "%BUILD%"
mkdir "%BUILD%"

rem ============ Compile for Java 8 ============
dir /s /b src\*.java > srcs.txt
javac -encoding UTF-8 -nowarn -source 1.8 -target 1.8 -cp "%CP%" -d "%BUILD%" @srcs.txt
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    del srcs.txt
    exit /b 1
)

rem ============ Copy resources ============
copy src\*.yml "%BUILD%\" >nul

rem ============ Package JAR ============
pushd "%BUILD%"
jar cf "..\%OUTJAR%" .
popd

del srcs.txt

if not exist "dist2" mkdir dist2
copy "%OUTJAR%" "dist2\NewCustomCuiLianPro-%VERSION%.jar" >nul

echo.
echo [DONE] %OUTJAR%
echo [DONE] dist2\NewCustomCuiLianPro-%VERSION%.jar
