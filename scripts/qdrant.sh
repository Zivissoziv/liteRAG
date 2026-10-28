#!/usr/bin/env bash
# 本地 Qdrant 管理：./scripts/qdrant.sh start|stop|restart|status
#
# 二进制放在 tools/qdrant/ 下，按平台自动选择：
#   Windows x64  tools/qdrant/qdrant.exe
#   Linux x64    tools/qdrant/linux-x64/qdrant
#   macOS arm64  tools/qdrant/darwin-arm64/qdrant（缺失时自动下载）
# 数据目录固定为 tools/qdrant/storage（Qdrant 以当前工作目录为根）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
QDIR="$ROOT/tools/qdrant"
LOG="$QDIR/qdrant.log"
PIDFILE="$QDIR/qdrant.pid"
URL="${QDRANT_URL:-http://127.0.0.1:6333}"
VERSION="${QDRANT_VERSION:-1.20.0}"

bin_path() {
  case "$(uname -s)" in
    Linux)  echo "$QDIR/linux-x64/qdrant" ;;
    Darwin) echo "$QDIR/darwin-arm64/qdrant" ;;
    *)      echo "$QDIR/qdrant.exe" ;;
  esac
}

is_up() { curl -fsS -m 2 "$URL/healthz" >/dev/null 2>&1; }

# 监听该端口的进程号（pidfile 只在 Linux/macOS 上可靠，Windows 走 netstat）
pid_on_port() {
  local port="$1"
  case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*)
      netstat -ano 2>/dev/null | grep -E "TCP.*:${port}[[:space:]]" | grep -i listen \
        | head -1 | awk '{print $NF}'
      ;;
    *)
      command -v lsof >/dev/null 2>&1 && lsof -ti ":${port}" -sTCP:LISTEN 2>/dev/null | head -1
      ;;
  esac
}

ensure_bin() {
  local bin; bin="$(bin_path)"
  [ -f "$bin" ] && return 0
  local asset=""
  case "$bin" in
    */linux-x64/*)    asset="qdrant-x86_64-unknown-linux-gnu.tar.gz" ;;
    */darwin-arm64/*) asset="qdrant-aarch64-apple-darwin.tar.gz" ;;
    *)
      echo "      缺少 $bin" >&2
      echo "      请下载 Qdrant Windows 版，解压后把 qdrant.exe 放到 tools/qdrant/" >&2
      return 1
      ;;
  esac
  echo "      未找到 $bin，下载 Qdrant ${VERSION} ..."
  mkdir -p "$(dirname "$bin")"
  curl -fL --retry 3 --connect-timeout 10 -o "$QDIR/qdrant.tar.gz" \
    "https://github.com/qdrant/qdrant/releases/download/v${VERSION}/${asset}"
  tar -xzf "$QDIR/qdrant.tar.gz" -C "$(dirname "$bin")"
  rm -f "$QDIR/qdrant.tar.gz"
  chmod +x "$bin"
}

start() {
  if is_up; then
    echo "      Qdrant 已在运行（$URL）"
    return 0
  fi
  ensure_bin || return 1
  mkdir -p "$QDIR"
  ( cd "$QDIR" && nohup "$(bin_path)" >>"$LOG" 2>&1 & echo $! > "$PIDFILE" )
  for _ in $(seq 1 60); do
    if is_up; then
      echo "      Qdrant 已启动（$URL，日志 tools/qdrant/qdrant.log）"
      return 0
    fi
    sleep 0.5
  done
  echo "      Qdrant 启动超时，请查看 $LOG" >&2
  return 1
}

stop() {
  local port="${URL##*:}"
  if [ -f "$PIDFILE" ]; then
    local pid; pid="$(cat "$PIDFILE" 2>/dev/null || true)"
    if [ -n "${pid}" ]; then
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$PIDFILE"
  fi
  local _i
  for _i in $(seq 1 10); do is_up || break; sleep 0.5; done

  if is_up; then
    local pid2; pid2="$(pid_on_port "$port" | tr -d '\r')"
    if [ -n "${pid2}" ]; then
      case "$(uname -s)" in
        MINGW*|MSYS*|CYGWIN*) taskkill //PID "$pid2" //F >/dev/null 2>&1 || true ;;
        *)                    kill "$pid2" 2>/dev/null || true ;;
      esac
      for _i in $(seq 1 10); do is_up || break; sleep 0.5; done
    fi
  fi

  if is_up; then
    echo "      ${URL} 仍有响应且无法自动停止，请手动结束占用该端口的进程" >&2
    return 1
  fi
  echo "      Qdrant 已停止"
}

status() {
  if is_up; then
    local ver
    ver="$(curl -fsS -m 2 "$URL/" | sed -n 's/.*"version":"\([^"]*\)".*/\1/p')"
    echo "      运行中  $URL  ${ver:+v$ver}"
  else
    echo "      未运行  $URL"
  fi
}

case "${1:-start}" in
  start)   start ;;
  stop)    stop ;;
  restart) stop || true; start ;;
  status)  status ;;
  *)
    echo "用法: $0 {start|stop|restart|status}" >&2
    exit 2
    ;;
esac
