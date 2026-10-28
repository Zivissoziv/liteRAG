@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0..\server"

echo [1/3] Compiling server classes ...
call mvn -q -B -DskipTests compile
if errorlevel 1 goto fail

echo [2/3] Resolving runtime classpath ...
call mvn -q -B dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 goto fail

rem classpath 可能超过 cmd 变量 8K 上限，写到 Java @argfile 里，避免截断
(for /f "usebackq delims=" %%i in ("target\cp.txt") do echo -cp "target\classes;%%i") > target\migrate.args

echo [3/3] Migrating H2 -> SQLite ...
java @target\migrate.args com.literag.tool.DbMigrate %*
goto end

:fail
echo Failed: see the messages above.
exit /b 1

:end
endlocal
