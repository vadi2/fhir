@ECHO off
ECHO Running publication process now

for /f "usebackq" %%x in (`dir /od /b "%ProgramFiles%\java\jdk*"`) do set newestJDK=%ProgramFiles%\java\%%x\bin\java.exe
set JAVA_HOME=%newestJDK% 
echo Using %JAVA_HOME%

ant Publisher -Dargs="%*"

PAUSE
