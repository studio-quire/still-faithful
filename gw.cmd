@echo off
rem Runs the Gradle wrapper with the portable JDK in local\tools\jdk21 when present (docs/building.md).
if exist "%~dp0local\tools\jdk21\bin\java.exe" set "JAVA_HOME=%~dp0local\tools\jdk21"
call "%~dp0gradlew.bat" %*
