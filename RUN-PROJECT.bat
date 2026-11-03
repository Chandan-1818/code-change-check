@echo off
setlocal EnableExtensions
title Change-Sensitive Regression Test Selection System

rem ==================================================================
rem  One-click launcher
rem  Builds the project with Maven and starts the interactive
rem  Change-Sensitive Regression Test Selection application.
rem  Double-click this file, or run it from cmd / PowerShell.
rem ==================================================================

rem ---- Step 1: project directory (the directory of this script) ----
cd /d "%~dp0"
if not exist "pom.xml" goto :no_project

set "APP_JAR=target\CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar"

echo ========================================================
echo  CHANGE-SENSITIVE REGRESSION TEST SELECTION SYSTEM
echo ========================================================
echo.
echo Starting application...
echo.

rem ---- Step 2: Java ----
echo Checking Java...
where java >nul 2>&1
if errorlevel 1 goto :no_java
java -version >nul 2>&1
if errorlevel 1 goto :no_java

rem ---- Step 3: Maven ----
echo Checking Maven...
where mvn >nul 2>&1
if errorlevel 1 goto :no_maven
call mvn -version >nul 2>&1
if errorlevel 1 goto :no_maven

rem ---- Step 4: build (never launch an outdated JAR after a failure) ----
echo.
echo Building project...
echo.
call mvn -B package
if errorlevel 1 goto :build_failed
echo.
echo Build successful!

rem ---- Step 5: locate the shaded executable JAR ----
call :locate_jar
if not defined APP_JAR goto :no_jar

rem ---- Step 6: launch the interactive application ----
echo.
echo Starting regression testing application...
echo.
echo ========================================================
echo.

java -jar "%APP_JAR%" --interactive
set "APP_EXIT=%ERRORLEVEL%"

echo.
if not "%APP_EXIT%"=="0" (
    echo [ERROR] The application exited with code %APP_EXIT%.
    echo         Review the messages printed above for details.
    goto :fail
)

echo Application closed normally.
echo.
pause
endlocal
exit /b 0

rem ==================================================================
rem  Subroutines
rem ==================================================================

:locate_jar
rem Prefer the shaded executable artifact. Fall back to any other
rem target\*.jar, but never to the non-shaded "original-*.jar" that
rem the Maven Shade plugin leaves behind.
set "APP_JAR="
if exist "target\CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar" (
    set "APP_JAR=target\CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar"
    goto :eof
)
for %%F in ("target\*.jar") do (
    if not defined APP_JAR (
        echo %%~nxF | findstr /b /i "original-" >nul || set "APP_JAR=%%~fF"
    )
)
goto :eof

rem ==================================================================
rem  Error handling (always pauses so the message can be read)
rem ==================================================================

:no_project
echo.
echo [ERROR] pom.xml was not found in:
echo         %CD%
echo.
echo         This launcher must be placed in the project root directory,
echo         the folder that contains pom.xml and the src folder.
goto :fail

:no_java
echo.
echo [ERROR] Java is not available on this system.
echo.
echo         Install a JDK 17 or higher and make sure "java" is on your PATH.
echo         Verify the installation with:  java -version
goto :fail

:no_maven
echo.
echo [ERROR] Apache Maven is not available on this system.
echo.
echo         Install Maven 3.8 or higher and make sure "mvn" is on your PATH.
echo         Verify the installation with:  mvn -version
goto :fail

:build_failed
echo.
echo [ERROR] The Maven build FAILED - the application was NOT started.
echo.
echo         The errors are printed above. An outdated JAR is never launched;
echo         fix the build, then run this launcher again.
goto :fail

:no_jar
echo.
echo [ERROR] No executable JAR was found in the target directory.
echo.
echo         Expected: target\CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar
echo         Build the project first:  mvn -B package
goto :fail

:fail
echo.
pause
endlocal
exit /b 1
