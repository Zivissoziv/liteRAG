# LiteRAG

本地部署的轻量级 RAG 知识库：上传文档 → 自动解析、分块、向量化 → 网页里检索问答，或通过 **MCP** 把知识库接进 Cursor / Claude。

Vue 3 + Spring Boot 3 + Qdrant，`java -jar` 一个进程同时提供 UI、REST API 与 MCP 端点，运行期不依赖 Python。

![概览](docs/screenshots/02-dashboard.png)

## 功能一览

| 能力 | 说明 |
| --- | --- |
| 文档接入 | docx / pdf / xlsx / xls / pptx / md / txt / csv / html，多文件上传、异步解析入库；也可在线新建与编辑 Wiki 页面 |
| 分块策略 | **上传时按文件选择**：智能推荐（按类型路由）/ 标题结构 / 表格优先 / 固定长度，策略随文档保存，重建索引沿用 |
| 目录归档 | 知识库内多级目录（≤6 层），父目录自动含子目录；删除目录只删结构，文档上移 |
| 原样预览 | 抽屉内看原始版面：PDF 浏览器原生渲染，docx 还原段落与表格，xlsx 带行列标与工作表切换，pptx 按幻灯片坐标渲染 |
| 检索问答 | 向量召回 + 大模型总结，答案带 `[1][2]` 引用；未配置对话模型时降级为返回原文片段 |
| Embedding | OpenAI 兼容接口（硅基流动 / 通义 / OpenAI / 智谱）、Ollama 本地模型、内置离线哈希模型，可随时切换与测活 |
| 向量存储 | Qdrant（推荐，原生二进制零依赖）、ChromaDB（v1/v2 自适应）、进程内余弦检索，设置页一键切换 |
| 权限 | 账号密码登录，三档角色 + 知识库级可见范围，文档、目录、检索结果、统计全链路过滤 |
| MCP | 原生 JSON-RPC 2.0，同时支持 Streamable HTTP 与 HTTP+SSE，暴露 5 个工具 + 4 类资源 |

## 界面

**知识库与文档**：左侧目录树、右侧文档列表，状态、片段数、更新时间一目了然。

![知识库详情](docs/screenshots/04-kb-detail.png)

上传时逐份选择分块规则，智能推荐会按文件类型自动路由：

<p align="center"><img src="docs/screenshots/05-upload.png" width="620" alt="上传文档"></p>

**检索问答**：先看片段相似度，再决定是否交给大模型总结；命中自带来源文档与出处。

![检索问答](docs/screenshots/06-search.png)

**MCP 接入**：端点、可用工具、客户端配置一页齐活，复制即可贴进 Cursor / Claude Desktop。

![MCP 接入](docs/screenshots/08-mcp.png)

| 用户与角色 | 系统设置 |
| --- | --- |
| ![](docs/screenshots/09-users.png) | ![](docs/screenshots/10-settings.png) |

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.2.5 / Java 17 / Spring Data JPA / SQLite（默认，单文件免安装）/ MySQL（可选） |
| 解析 | Apache POI（docx、xlsx、pptx）、PDFBox（pdf） |
| 向量库 | Qdrant 1.x（原生二进制或 Docker）、ChromaDB 1.0.x，均不可用时回退内置本地向量库 |
| 前端 | Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router（hash 路由） |
| 预览 | 浏览器原生 PDF、docx-preview、SheetJS、POI 自绘 HTML |
| 协议 | MCP 2025-06-18（Streamable HTTP）/ 2024-11-05（HTTP+SSE） |

## 快速开始

环境要求：JDK 17+、Maven 3.8+、Node.js 18+；数据库无需安装（内置 SQLite 单文件库），向量库可选（Qdrant 单文件可执行程序，或 Docker 跑 ChromaDB）。

**方式一：一键启动**

```bash
scripts\start.bat        # Windows
bash scripts/start.sh    # macOS / Linux
```

脚本会准备向量库（Docker 可用时拉起 ChromaDB）→ 构建前端 → 打包后端 → 启动服务，然后访问 <http://127.0.0.1:8080>。

**方式二：开发模式**

```bash
docker compose up -d chroma        # 或自行运行 qdrant 可执行文件（REST 端口 6333）
cd server && mvn spring-boot:run   # 后端 8080
cd web && npm install && npm run dev   # 前端 5173，/api 与 /mcp 已代理到 8080
```

**方式三：只跑 jar**

```bash
cd server && mvn -DskipTests package
java -jar target/literag-server-1.0.0.jar --server.port=8080
```

打包时会把 `web/dist` 复制进 jar；不存在则只提供 API 与 MCP。

**演示账号**（首次启动自动创建，登录页可点击填充）

| 账号 | 密码 | 角色 | 可见范围 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 管理员 | 全部页面，含模型配置、用户管理、系统设置 |
| `editor` | `editor123` | 编辑者 | 除模型配置、用户管理、系统设置 |
| `viewer` | `viewer123` | 只读 | 概览、知识库、检索问答、MCP 接入，无写操作 |

## 关键配置

### 数据库

默认使用 **SQLite** 单文件库，落在 `server/data/db/literag.db`，免安装、无额外进程，**停服务数据仍在**（WAL 模式下同目录还有 `-wal` / `-shm` 两个附属文件，属正常）。表结构由 Hibernate 首次启动自动创建。

| 场景 | 做法 |
| --- | --- |
| 默认 SQLite | 无需配置。已开 WAL + `busy_timeout=10000` + `transaction_mode=IMMEDIATE`，实测 8 个文件并发上传无冲突 |
| 改用 MySQL | `java -jar literag-server.jar --spring.profiles.active=mysql`，或用环境变量 `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` / `SPRING_DATASOURCE_URL` 覆盖连接串 |
| 换回 H2 | 覆盖 `spring.datasource.url`、`driver-class-name`、`spring.jpa.database-platform` 三项即可 |

MySQL 需先建库，字符集必须是 utf8mb4（否则部分中文与 emoji 会截断）：

```sql
CREATE DATABASE literag DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

从旧版 H2 库迁移现有数据（覆盖式写入，可重复执行；先以 SQLite 配置启动一次服务，让 Hibernate 建好表）：

```bash
scripts/migrate-db.bat           # 预检：只打印两端表结构与行数，不写数据
scripts/migrate-db.bat --force   # 实际迁移，完成后自动校准自增序列
```

![](docs/screenshots/11-database.png)

### Embedding 模型

| 供应商 | provider | baseUrl | modelName | 维度 |
| --- | --- | --- | --- | --- |
| 硅基流动 | `openai` | `https://api.siliconflow.cn/v1` | `BAAI/bge-m3` | 1024 |
| 通义千问 | `openai` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `text-embedding-v3` | 1024 |
| OpenAI | `openai` | `https://api.openai.com/v1` | `text-embedding-3-small` | 1536 |
| Ollama | `ollama` | `http://localhost:11434` | `bge-m3` | 1024 |
| 离线演示 | `local` | 留空 | `local-hash-256` | 256 |

任何 OpenAI 兼容服务都填 `openai`；`local` 为零依赖的哈希向量，仅用于跑通流程，正式使用请换成真实模型。对话模型（CHAT 类型）可选，不配也能检索。

> 新建知识库时不再单独选模型与分块参数：**向量模型取「模型配置」中的默认 Embedding 模型，分块长度与重叠取「系统设置 · 检索与分块」的默认值**。更换 Embedding 模型后必须重建索引，否则维度不一致。

### 向量库

- `literag.vector.provider` 支持 `qdrant`（默认）/ `chroma` / `local` / `auto`，也可在「系统设置」里切换，**切换后需对已有知识库重建索引**，向量不会自动迁移。
- Qdrant 是单个可执行文件（Windows zip 约 27 MB，解压即 `qdrant.exe`），REST 端口 `6333`，`http://localhost:6333/dashboard` 有自带管理界面。liteRAG 用内置 REST 客户端访问，无需 SDK。
- collection 在首次写入时按实际向量维度创建，相似度统一使用 Cosine，score 语义（0~1，越大越相关）与 Chroma、本地引擎一致。

### 分块

| 策略 | 行为 |
| --- | --- |
| 智能推荐（默认） | md / pptx / Wiki → 标题结构；xlsx / xls → 表格优先（无表格结构回退固定长度）；其余 → 固定长度 |
| 标题结构 | 按 Markdown 标题层级切分，每块首行带章节路径（如 `部署指南 > Docker 方式`），命中自带出处 |
| 表格优先 | 识别解析出的工作表小节，按「表头 + 行组」打包，每块重复表头，单独一块即可读 |
| 固定长度 | 段落 → 句子 → 硬切三级回退 + 相邻块重叠（默认 800 字 / 120 字重叠，在系统设置调整） |

### 权限

| 权限点 | 管理员 | 编辑者 | 只读 | 覆盖操作 |
| --- | :---: | :---: | :---: | --- |
| `content:read` | ✓ | ✓ | ✓ | 浏览、预览、下载、检索、统计 |
| `content:write` | ✓ | ✓ | | 建库、上传、新建 Wiki、编辑、移动、删除、重建索引 |
| `config:manage` | ✓ | | | 模型配置、向量库参数、MCP 密钥、系统设置 |
| `user:manage` | ✓ | | | 用户新建、改角色、停用、重置密码、删除 |

知识库还可设「全员可见」或「指定角色 / 用户可见」，受限库的文档、目录、分块、原文件预览、检索命中、统计全部继承该范围，管理员始终可见。密码使用 PBKDF2-HMAC-SHA256（12 万次迭代 + 随机盐），连续 5 次错误锁定 60 秒；令牌存库并下发 HttpOnly Cookie，登出、改密、停用或改角色后立即失效。

## MCP 接入

| 传输 | 地址 |
| --- | --- |
| Streamable HTTP（推荐） | `POST http://<host>:8080/mcp` |
| HTTP + SSE（旧客户端） | `GET http://<host>:8080/mcp/sse` |

```json
{
  "mcpServers": {
    "literag": {
      "url": "http://127.0.0.1:8080/mcp",
      "type": "http"
    }
  }
}
```

| 工具 | 参数 | 说明 |
| --- | --- | --- |
| `search_knowledge` | `query*`, `kb_ids[]`, `top_k`, `min_score` | 语义检索，返回片段、来源文档与相似度 |
| `ask_knowledge` | `question*`, `kb_ids[]`, `top_k` | 检索增强问答，答案带引用编号 |
| `list_knowledge_bases` | 无 | 列出知识库及统计 |
| `list_documents` | `kb_id*`, `keyword`, `limit` | 列出知识库下文档 |
| `get_document` | `doc_id*`, `offset`, `limit` | 读取文档全文，支持分段 |

资源：`literag://knowledge-bases`、`literag://kb/{kbId}`、`literag://kb/{kbId}/documents`、`literag://doc/{docId}`。
`/mcp` 不走登录态，在「系统设置」配置 MCP API Key 后客户端需带 `X-API-Key`（SSE 可用 `?apiKey=`）。Claude Desktop 可用 `npx -y mcp-remote http://127.0.0.1:8080/mcp` 转接。

## REST API

统一响应 `{ "code": 0, "message": "ok", "data": ... }`；除 `/api/auth/login` 外都需要登录（未登录 401，权限不足 403），规则集中在 `security/AuthInterceptor`。主要接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` `/logout`、GET `/api/auth/me` | 登录、登出、当前用户与权限点 |
| GET/POST | `/api/kb` | 知识库列表（仅返回可见的）/ 新建 |
| GET/PUT/DELETE | `/api/kb/{id}`、POST `/api/kb/{id}/reindex` | 详情、更新（含可见范围）、删除、重建索引 |
| GET/POST | `/api/kb/{kbId}/documents`、`/documents/upload` | 文档分页 / 多文件上传（multipart，可选 `strategy`、`folderId`） |
| GET/POST | `/api/kb/{kbId}/folders`、`/api/kb/{kbId}/wiki` | 目录树 / 新建 Wiki 页面 |
| GET/PUT/DELETE | `/api/documents/{id}`、`/chunks`、`/raw`、`/slides` | 文档详情、编辑、分块列表、原文件与版式预览 |
| POST | `/api/search`、`/api/chat/ask` | 向量检索、RAG 问答 |
| GET/POST/PUT/DELETE | `/api/models`、`/api/settings`、`/api/users` | 模型、设置、用户管理 |

## 目录结构

```
liteRAG/
├─ server/          # Spring Boot 后端（controller / service / parser / vector / ai / mcp）
├─ web/             # Vue 3 前端（views：概览、知识库、检索问答、模型、MCP、用户、设置）
├─ scripts/         # 启停脚本、演示文档生成
├─ docs/screenshots/# README 截图
├─ data/            # 运行期数据：SQLite 库、上传文件、日志（不入库）
└─ docker-compose.yml
```

运行期数据落盘：元数据 `server/data/db/literag.db`（SQLite 单文件）、原始文件 `server/data/uploads/{kbId}/...`、Qdrant 数据在其可执行文件同级 `storage/`、Chroma 在 `data/chroma/`；本地回退时向量存在 `doc_chunk.embedding` 字段。

## 常见问题

**检索结果为空或相似度很低？** 确认文档状态是「已就绪」、collection 有数据、模型一致；离线哈希模型语义能力弱，建议换 bge-m3。

**上传后一直处理中？** 看 `data/server.log`，常见原因是文件加密或扫描件 PDF 没有文本层（本系统不含 OCR）。

**向量库连不上？** `auto` 策略按 Qdrant → ChromaDB → 本地依次回退；显式指定引擎时连不上会直接报错。修好后在系统设置切回，并重建索引。

**端口被占用？** `java -jar ... --server.port=9090`，同时改 `web/vite.config.js` 的代理目标。

**数据存在哪？换数据库会丢吗？** 元数据在 `server/data/db/literag.db`，原始文件在 `server/data/uploads/`，备份只需整个 `server/data/` 目录。切库不会自动搬数据（Hibernate 只负责建表）：从旧版 H2 切到 SQLite 用 `scripts/migrate-db.bat`，已有 SQLite 数据要转 MySQL 则需自行导出导入。

## License

[Apache-2.0](LICENSE)
