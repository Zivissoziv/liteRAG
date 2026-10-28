#!/usr/bin/env bash
# LiteRAG 一键启动：探测向量库 -> 前端构建 -> 后端打包 -> 启动服务
# 元数据库为内置 SQLite 单文件（server/data/db/literag.db），无需启动
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PORT="${PORT:-8080}"

echo "[1/4] 探测向量库 ..."
if curl -s -m 2 http://localhost:6333/collections >/dev/null 2>&1; then
  echo "      Qdrant 已就绪（localhost:6333）"
elif curl -s -m 2 http://localhost:8000/api/v2/heartbeat >/dev/null 2>&1; then
  echo "      ChromaDB 已就绪（localhost:8000）"
else
  echo "      未检测到 Qdrant / ChromaDB，将回退到内置本地向量库"
  echo "      需要 Qdrant 可执行：tools/qdrant/qdrant.exe（Windows）或 docker run -p 6333:6333 qdrant/qdrant"
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
