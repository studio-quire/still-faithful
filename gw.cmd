@echo off
rem Runs the Gradle wrapper with the portable JDK in local\tools\jdk21 when present (docs/building.md).
rem The JDK goes by its real place, as in gw (the reason is there): where local is a link, a git worktree's to the
rem main checkout's, dir /AL prints "<JUNCTION>  local [the real folder]", and the JDK is taken from that folder.
if exist "%~dp0local\tools\jdk21\bin\java.exe" set "JAVA_HOME=%~dp0local\tools\jdk21"
for /f "tokens=2 delims=[]" %%t in ('dir /AL "%~dp0." 2^>nul ^| findstr /I /R /C:"> *local \["') do if exist "%%t\tools\jdk21\bin\java.exe" set "JAVA_HOME=%%t\tools\jdk21"
call "%~dp0gradlew.bat" %*
