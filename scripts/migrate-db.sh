#!/usr/bin/env bash
# 旧版 H2 元数据库 -> SQLite 迁移（覆盖式，可重复执行）
# 用法：./scripts/migrate-db.sh [h2库路径] [sqlite库路径] [--force]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/server"

echo "[1/3] 编译 server ..."
mvn -q -B -DskipTests compile

echo "[2/3] 解析运行时 classpath ..."
mvn -q -B dependency:build-classpath -Dmdep.outputFile=target/cp.txt
CP="$(cat target/cp.txt)"

echo "[3/3] 迁移 H2 -> SQLite ..."
java -cp "target/classes:${CP}" com.literag.tool.DbMigrate "$@"
