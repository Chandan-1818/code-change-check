@echo off
setlocal EnableExtensions
title Change-Sensitive Regression Test Selection System (no build)

rem ==================================================================
rem  Quick launcher - starts the application from the JAR that was
rem  already built. It never builds and never launches a missing or
rem  invalid JAR. Run RUN-PROJECT.bat first if target\ is empty.
rem ==================================================================

rem ---- Step 1: project directory (the directory of this script) ----
cd /d "%~dp0"

set "APP_JAR="

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

rem ---- Step 3: locate the shaded executable JAR (no build) ----
echo Locating executable JAR...
call :locate_jar
if not defined APP_JAR goto :no_jar

rem ---- Step 4: verify that the JAR is really runnable ----
echo Verifying executable JAR...
java -jar "%APP_JAR%" --version >nul 2>&1
if errorlevel 1 goto :invalid_jar

rem ---- Step 5: launch the interactive application ----
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

:no_java
echo.
echo [ERROR] Java is not available on this system.
echo.
echo         Install a JDK 17 or higher and make sure "java" is on your PATH.
echo         Verify the installation with:  java -version
goto :fail

:no_jar
echo.
echo [ERROR] No executable JAR was found in the target directory.
echo.
echo         Expected: target\CODE-CHANGE-ANALYSIS-1.0-SNAPSHOT.jar
echo.
echo         Build the project first by double-clicking RUN-PROJECT.bat
echo         or by running:  mvn -B package
goto :fail

:invalid_jar
echo.
echo [ERROR] The JAR found was not runnable: %APP_JAR%
echo.
echo         The artifact may be incomplete or the non-shaded "original-*.jar".
echo         Rebuild it with RUN-PROJECT.bat or with:  mvn -B package
goto :fail

:fail
echo.
pause
endlocal
exit /b 1
