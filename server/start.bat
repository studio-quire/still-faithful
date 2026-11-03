@echo off
rem @NAME@ server: Minecraft beta 1.7.3's server with Fabric Loader and our @SERVER_MODS@ mods. See README.txt.
setlocal
cd /d "%~dp0"
set "JAR=minecraft_server.jar"
set "SHA1=@SERVER_SHA1@"
if not exist "%JAR%" (
  echo Put Minecraft beta 1.7.3's server jar in this folder as %JAR%. See README.txt.
  exit /b 1
)
set "GOT="
for /f "skip=1 delims=" %%h in ('certutil -hashfile "%JAR%" SHA1') do if not defined GOT set "GOT=%%h"
set "GOT=%GOT: =%"
if /i not "%GOT%"=="%SHA1%" (
  echo %JAR% isn't beta 1.7.3's server: its SHA-1 is %GOT%, expected %SHA1%.
  exit /b 1
)
if not defined JAVA set "JAVA=java"
if not defined JAVA_OPTS set "JAVA_OPTS=-Xmx1G"
"%JAVA%" %JAVA_OPTS% -cp "libraries/*;%JAR%" net.fabricmc.loader.impl.launch.knot.KnotServer nogui
exit /b %ERRORLEVEL%
