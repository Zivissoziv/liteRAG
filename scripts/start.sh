#!/usr/bin/env bash
# LiteRAG 一键启动：向量库 -> 前端构建 -> 后端打包 -> 启动服务
# 元数据库为内置 SQLite 单文件（server/data/db/literag.db），无需启动
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PORT="${PORT:-8080}"

echo "[1/4] 启动向量库 ..."
if [ "${VECTOR_PROVIDER:-qdrant}" = "local" ]; then
  echo "      已指定本地向量库，跳过 Qdrant"
else
  bash "$ROOT/scripts/qdrant.sh" start
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
