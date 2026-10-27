@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
set ROOT=%~dp0..
cd /d "%ROOT%"
if "%PORT%"=="" set PORT=8080

echo [1/4] Starting ChromaDB ...
where docker >nul 2>&1
if errorlevel 1 (
  echo       Docker not found, fallback to built-in local vector store
  goto build
)
docker info >nul 2>&1
if errorlevel 1 (
  echo       Docker daemon not running, fallback to built-in local vector store
  goto build
)
curl -s -m 3 http://localhost:8000/api/v2/heartbeat >nul 2>&1
if not errorlevel 1 (
  echo       ChromaDB already running
  goto build
)
docker rm -f literag-chroma >nul 2>&1
docker run -d --name literag-chroma -p 8000:8000 -v "%ROOT%/data/chroma:/data" -e IS_PERSISTENT=TRUE -e ANONYMIZED_TELEMETRY=FALSE chromadb/chroma:1.0.15 >nul
echo       ChromaDB started on localhost:8000

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
