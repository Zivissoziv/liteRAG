@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
set ROOT=%~dp0..
cd /d "%ROOT%"
if "%PORT%"=="" set PORT=8080

echo [1/4] Starting vector store ...
rem Metadata DB is built-in SQLite (server\data\db\literag.db), nothing to start.
if /i "%VECTOR_PROVIDER%"=="local" (
  echo       Local vector store selected, skip Qdrant
  goto build
)
curl -s -m 2 http://localhost:6333/healthz >nul 2>&1
if not errorlevel 1 (
  echo       Qdrant is up on localhost:6333
  goto build
)
if not exist "tools\qdrant\qdrant.exe" (
  echo       tools\qdrant\qdrant.exe not found, fallback to built-in local vector store
  goto build
)
echo       Starting Qdrant ...
pushd tools\qdrant
start "literag-qdrant" /b qdrant.exe > qdrant.log 2>&1
popd
set /a QTRY=0
:waitq
timeout /t 1 /nobreak >nul
curl -s -m 2 http://localhost:6333/healthz >nul 2>&1
if not errorlevel 1 (
  echo       Qdrant started on localhost:6333
  goto build
)
set /a QTRY+=1
if %QTRY% lss 30 goto waitq
echo       Qdrant start timed out, see tools\qdrant\qdrant.log

:build
echo [2/4] Building frontend ...
if not exist "web\node_modules" (
  pushd web
  call npm install --no-audit --no-fund
  popd
)
pushd web
call npm run build
popd
if errorlevel 1 exit /b 1
echo       web/dist ready

echo [3/4] Packaging backend ...
pushd server
call mvn -q -DskipTests package
popd
if errorlevel 1 exit /b 1
echo       target/literag-server-1.0.0.jar ready

echo [4/4] Starting service on http://127.0.0.1:%PORT%
cd server
java -Dfile.encoding=UTF-8 -jar target/literag-server-1.0.0.jar --server.port=%PORT%
