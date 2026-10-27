#!/usr/bin/env bash
# LiteRAG 一键启动：ChromaDB -> 前端构建 -> 后端打包 -> 启动服务
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PORT="${PORT:-8080}"

echo "[1/4] 启动 ChromaDB ..."
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  if ! curl -s -m 3 http://localhost:8000/api/v2/heartbeat >/dev/null 2>&1; then
    docker rm -f literag-chroma >/dev/null 2>&1 || true
    docker run -d --name literag-chroma -p 8000:8000 \
      -v "$ROOT/data/chroma:/data" \
      -e IS_PERSISTENT=TRUE -e ANONYMIZED_TELEMETRY=FALSE \
      chromadb/chroma:1.0.15 >/dev/null
    echo "      ChromaDB 已启动（localhost:8000）"
  else
    echo "      ChromaDB 已在运行"
  fi
else
  echo "      未检测到 Docker，将使用内置本地向量库"
fi

echo "[2/4] 构建前端 ..."
if [ ! -d web/node_modules ]; then
  (cd web && npm install --no-audit --no-fund)
fi
(cd web && npm run build >/dev/null)
echo "      完成 web/dist"

echo "[3/4] 打包后端 ..."
(cd server && mvn -q -DskipTests package)
echo "      完成 target/literag-server-1.0.0.jar"

echo "[4/4] 启动服务：http://127.0.0.1:${PORT}"
cd server
exec java -Dfile.encoding=UTF-8 -jar target/literag-server-1.0.0.jar --server.port="$PORT"
