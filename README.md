# LiteRAG 轻量级 RAG Wiki 文档管理系统

一套可以完全自持的 RAG 知识库系统：**Vue 3 前端 + Spring Boot 后端 + Qdrant / ChromaDB 向量库**，
支持 docx / pdf / xlsx / pptx / md / txt 文档上传与解析，支持自定义 Embedding 模型，
并通过 **MCP（Model Context Protocol）** 把知识库以工具形式提供给 Cursor / Claude Desktop 等 AI 客户端使用。

---

## 1. 能力总览

| 能力 | 说明 |
| --- | --- |
| 文档知识管理 | 上传 docx / pdf / xlsx / pptx / md / txt，自动解析、分块、向量化入库；支持在线新建与编辑 Wiki 页面 |
| 分块策略 | 上传时按文档选择：智能推荐（默认，按类型路由）/ 标题结构 / 表格优先 / 固定长度；详见 5.2 分块策略 |
| 分块策略 | 上传时按文档选择：智能推荐（默认，按类型路由）/ 标题结构 / 表格优先 / 固定长度；详见 5.2 分块策略 |
| 目录归档 | 知识库内可建多级目录（最多 6 层），上传、新建 Wiki、迁移文档时指定归属；侧栏按目录浏览，选中父目录自动包含子目录；删除目录只删结构，文档上移到上一级 |
| 原始文件预览 | 抽屉内直接看原样版面：PDF 走浏览器原生渲染，docx 还原段落与表格，xlsx 带行号列标与工作表切换，pptx 按幻灯片坐标渲染 |
| 解析与分块 | 段落感知分块，默认 800 字 / 120 字重叠，可在系统中按知识库调整 |
| 自定义 Embedding | OpenAI 兼容接口（硅基流动、通义、OpenAI、智谱等）、Ollama 本地模型、内置离线哈希模型，三者可随时切换与测活 |
| 向量存储 | 支持 Qdrant（推荐，原生二进制零依赖）与 ChromaDB（v1/v2 自适应），在系统设置中切换；均不可用时自动回退到进程内余弦检索 |
| 检索 | 相似度检索，支持按知识库过滤、TopK、最低相似度阈值 |
| RAG 问答 | 向量召回 + 大模型总结，答案带 `[1][2]` 引用编号；未配置对话模型时降级为返回原文片段 |
| 登录与权限 | 账号密码登录，三档角色（管理员 / 编辑者 / 只读）控制菜单、页面与写操作；PBKDF2 密码哈希、可撤销令牌、登录失败锁定、最后管理员保护 |
| 数据可见性 | 知识库可设为「全员可见」或「指定角色 / 用户可见」，文档、目录、分块、检索结果、概览统计都按知识库可见范围过滤，越权访问返回 403 |
| MCP 服务 | 原生 JSON-RPC 2.0 实现，同时支持 Streamable HTTP（推荐）与 HTTP+SSE（旧版兼容），暴露 5 个工具 + 3 类资源 |
| 单端口部署 | 前端构建产物可打进 jar，`java -jar` 一个进程同时提供 UI、REST API 与 MCP 端点 |

---

## 2. 技术栈

- 后端：Spring Boot 3.2.5 / Java 17 / Spring Data JPA / H2（文件模式，免安装）
- 解析：Apache POI（docx、xlsx、pptx）、PDFBox（pdf）
- 向量库：Qdrant 1.x（原生二进制或 Docker，推荐）或 ChromaDB 1.0.x（Docker）；都没有时用内置本地向量库
- 前端：Vue 3 + Vite 5 + Element Plus + Pinia + Vue Router（hash 路由）
- 预览：浏览器原生 PDF 渲染、docx-preview（docx）、SheetJS（xlsx / csv）、POI 自绘 HTML（pptx）
- 协议：MCP 2025-06-18（Streamable HTTP）/ 2024-11-05（HTTP+SSE）

> 运行期不依赖 Python。仓库里的 `scripts/make_samples.py` 仅用于生成演示文档，可用可不用。

---

## 3. 目录结构

```
liteRAG/
├─ server/                     # Spring Boot 后端
│  └─ src/main/java/com/literag/
│     ├─ controller/           # REST 接口
│     ├─ service/              # 知识库、文档、入库、检索、问答、设置
│     ├─ parser/               # docx/pdf/xlsx/pptx/text 解析器
│     ├─ vector/               # Qdrant / Chroma 客户端 + 本地向量库 + 统一抽象
│     ├─ ai/                   # Embedding / Chat 客户端（openai / ollama / local）
│     └─ mcp/                  # MCP Server（工具、资源、两种传输）
├─ web/                        # Vue 3 前端
│  └─ src/views/               # 概览、知识库、检索问答、模型配置、MCP 接入、设置
├─ scripts/                    # 演示文档生成、启停脚本
├─ data/                       # 运行期数据（Chroma 落盘、日志、截图）
└─ docker-compose.yml          # ChromaDB
```

---

## 4. 快速开始

### 4.1 环境要求

- JDK 17+、Maven 3.8+
- Node.js 18+
- Docker（可选，用于跑 ChromaDB；Qdrant 用原生二进制即可，无需 Docker）

### 4.2 方式一：一键启动（推荐）

```bash
# Windows
scripts\start.bat

# macOS / Linux
bash scripts/start.sh
```

脚本会依次：启动 ChromaDB 容器 → 构建前端（如 dist 不存在）→ 打包后端 → 启动服务。

访问 <http://127.0.0.1:8080>。

### 4.3 方式二：开发模式

```bash
# 1. 启动向量库（二选一；都没有可跳过，系统会自动回退本地向量库）
docker compose up -d chroma                    # ChromaDB（需要 Docker）
# 或 Qdrant：下载 https://github.com/qdrant/qdrant/releases 对应平台的压缩包，
#    解压得到单个 qdrant 可执行文件，直接运行即可（REST 端口 6333）

# 2. 启动后端
cd server && mvn spring-boot:run

# 3. 启动前端（另开终端）
cd web && npm install && npm run dev
# 打开 http://127.0.0.1:5173 ，/api 与 /mcp 已配置代理到 8080
```

### 4.4 方式三：只跑 jar

```bash
cd server && mvn -DskipTests package
java -jar target/literag-server-1.0.0.jar --server.port=8080
```

> 打包时会自动把 `web/dist` 复制进 jar；若 `web/dist` 不存在，则只提供 API 与 MCP。

---

## 5. 使用流程

### 5.1 登录与角色

首次启动会自动创建三个演示账号，登录页点击即可自动填充：

| 账号 | 密码 | 角色 | 界面可见范围 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 管理员 | 全部页面，可管理用户与系统配置 |
| `editor` | `editor123` | 编辑者 | 除模型配置、用户管理、系统设置 |
| `viewer` | `viewer123` | 只读 | 概览、知识库、检索问答、MCP 接入，无任何写按钮 |

权限点与角色的对应关系固定为下表，角色不可自定义，避免在小团队场景里引入额外的权限管理成本：

| 权限点 | 管理员 | 编辑者 | 只读 | 覆盖的操作 |
| --- | :---: | :---: | :---: | --- |
| `content:read` | ✓ | ✓ | ✓ | 浏览知识库、文档、预览与下载、检索、统计 |
| `content:write` | ✓ | ✓ | | 建库、上传、新建 Wiki、编辑、移动、删除、重建索引 |
| `config:manage` | ✓ | | | 模型配置、向量库参数、MCP 密钥、系统设置 |
| `user:manage` | ✓ | | | 新建用户、改角色、停用、重置密码、删除 |

- 登录态使用服务端签发的随机令牌，存在 `auth_token` 表并下发为 HttpOnly Cookie，因此浏览器直接发起的下载与预览无需额外处理；登出、改密、被停用或被调整角色时令牌立即失效。
- 密码以 PBKDF2-HMAC-SHA256（12 万次迭代、随机盐）存储；同一账号连续 5 次密码错误会锁定 60 秒。
- 系统会阻止「停用 / 删除 / 降级最后一名管理员」以及「删除或停用自己的账号」。
- 用户的默认密码建议登录后在左下角用户菜单中修改；管理员重置他人密码后，该用户下次登录需使用新密码。
- **MCP 端点（`/mcp`）不走登录态**，沿用独立的 `X-API-Key` 鉴权，见 7.4；这样 Cursor 等客户端无需模拟浏览器登录。

**分块策略**在**上传文档时按文档选择**（知识库只保留分块长度与重叠参数）：

| 策略 | 行为 |
| --- | --- |
| 智能推荐（默认） | md / pptx / Wiki → 标题结构；xlsx / xls → 表格优先（无表格结构时回退固定长度）；其余 → 固定长度 |
| 标题结构 | 按 Markdown 标题层级切分，每块首行带章节路径（如 `部署指南 > Docker 方式`），检索命中自带出处 |
| 表格优先 | 识别解析出的工作表小节，按「表头 + 行组」打包，每块都重复表头，单独一块即可读 |
| 固定长度 | 段落 → 句子 → 硬切三级回退 + 相邻块重叠（原有行为） |

- 策略随文档保存，单篇「重建索引」和整库「重建索引」都会沿用各自文档的策略。
- 历史文档未选过策略，重建一次后会自动补齐为「智能推荐」。

**分块策略**在**上传文档时按文档选择**（知识库只保留分块长度与重叠参数）：

| 策略 | 行为 |
| --- | --- |
| 智能推荐（默认） | md / pptx / Wiki → 标题结构；xlsx / xls → 表格优先（无表格结构时回退固定长度）；其余 → 固定长度 |
| 标题结构 | 按 Markdown 标题层级切分，每块首行带章节路径（如 `部署指南 > Docker 方式`），检索命中自带出处 |
| 表格优先 | 识别解析出的工作表小节，按「表头 + 行组」打包，每块都重复表头，单独一块即可读 |
| 固定长度 | 段落 → 句子 → 硬切三级回退 + 相邻块重叠（原有行为） |

- 策略随文档保存，单篇「重建索引」和整库「重建索引」都会沿用各自文档的策略。
- 历史文档未选过策略，重建一次后会自动补齐为「智能推荐」。

知识库还可以单独设置**可见范围**，用于「同一个系统里放不同密级的资料」：

| 可见范围 | 效果 |
| --- | --- |
| 全员可见（默认） | 所有登录用户都能看到该知识库及其文档 |
| 指定范围 | 仅管理员 + 授权角色 / 授权用户可见，其余用户在列表、详情、检索、统计中都看不到它 |

- 受限知识库的文档、目录、分块、原文下载、在线预览、检索命中全部继承该知识库的可见范围，不会出现「库进不去但文档能预览」的绕过。
- 管理员始终可见所有知识库，避免把自己锁在门外；授权对象里不列出管理员，因为无需重复授权。
- 编辑者与管理员都能调整可见范围（编辑者需要 `content:write`）。若担心扩散，把知识库的「可见范围」交给管理员维护即可。
- MCP 侧是服务级凭据，不做逐用户过滤；如果知识库不适合出现在 MCP 里，关闭它的「MCP 开放」开关。

### 5.2 日常流程

1. **配置 Embedding 模型**：进入「模型配置」→ 新增，选择预设（硅基流动 bge-m3 / 通义 text-embedding-v3 / OpenAI / Ollama / 本地哈希），填 API Key 后点「测试连接」，再设为默认。
2. **新建知识库**：设置名称、说明与分块参数；每个知识库对应向量库中一个 collection。
3. **上传文档**：进入知识库详情 → 上传，支持一次多文件，可指定目录与标签；状态变为「已就绪」即完成入库。目录在左侧侧栏维护，支持多级嵌套与文档迁移。
4. **验证检索**：「检索问答」页输入自然语言问题，先看片段相似度，再决定是否交给大模型总结。
5. **接入 MCP**：「MCP 接入」页复制配置到 Cursor / Claude Desktop，即可在 IDE 里直接调用知识库。

> 更换 Embedding 模型后，同一知识库必须重建索引（知识库详情的「重建索引」），否则维度不一致会导致检索异常。

---

## 6. Embedding 模型配置

| 供应商 | provider | baseUrl 示例 | modelName 示例 | 维度 |
| --- | --- | --- | --- | --- |
| 硅基流动 | `openai` | `https://api.siliconflow.cn/v1` | `BAAI/bge-m3` | 1024 |
| 通义千问 | `openai` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `text-embedding-v3` | 1024 |
| OpenAI | `openai` | `https://api.openai.com/v1` | `text-embedding-3-small` | 1536 |
| 智谱 | `openai` | `https://open.bigmodel.cn/api/paas/v4` | `embedding-3` | 2048 |
| Ollama | `ollama` | `http://localhost:11434` | `bge-m3` | 1024 |
| 离线演示 | `local` | 留空 | `local-hash-256` | 256 |

- `openai`：任何 OpenAI 兼容的 `/embeddings`、`/chat/completions` 服务，baseUrl 填到 `/v1` 层即可。
- `ollama`：先 `ollama pull bge-m3`，再填 `http://localhost:11434`。
- `local`：内置哈希向量化，零配置零网络，用于验证流程；语义效果有限，正式使用请切换为真实模型。

对话模型（CHAT 类型）可选填，仅用于「检索问答」与 MCP 的 `ask_knowledge` 做总结；不配置时返回检索片段。

---

## 7. MCP 接入

### 7.1 端点

| 传输 | 地址 | 说明 |
| --- | --- | --- |
| Streamable HTTP | `POST http://<host>:8080/mcp` | 推荐，MCP 2025-06-18 |
| HTTP+SSE | `GET http://<host>:8080/mcp/sse` | 旧版兼容，POST 到 `/mcp/message?sessionId=...` |
| 会话管理 | `GET` / `DELETE /mcp`、`GET /api/mcp/sessions` | 查询与关闭会话 |

### 7.2 客户端配置

Cursor / Claude Code（`.cursor/mcp.json`）：

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

Claude Desktop（`claude_desktop_config.json`）：

```json
{
  "mcpServers": {
    "literag": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://127.0.0.1:8080/mcp"]
    }
  }
}
```

### 7.3 暴露的工具

| 工具 | 参数 | 说明 |
| --- | --- | --- |
| `search_knowledge` | `query*`, `kb_ids[]`, `top_k`, `min_score` | 语义检索，返回片段、来源文档、相似度 |
| `ask_knowledge` | `question*`, `kb_ids[]`, `top_k` | 检索增强问答，答案带引用编号 |
| `list_knowledge_bases` | 无 | 列出知识库及统计 |
| `list_documents` | `kb_id*`, `keyword`, `limit` | 列出知识库下文档 |
| `get_document` | `doc_id*`, `offset`, `limit` | 读取文档全文，支持分段 |

资源（Resources）：`literag://knowledge-bases`、`literag://kb/{kbId}`、`literag://kb/{kbId}/documents`、`literag://doc/{docId}`。

### 7.4 鉴权

默认不校验。在「系统设置」配置 MCP API Key 后，客户端需携带 `X-API-Key: <密钥>`（SSE 可用 `?apiKey=` 查询参数）。

---

## 8. REST API 摘要

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录（公开），返回令牌并写入 HttpOnly Cookie |
| POST | `/api/auth/logout` | 退出登录，令牌立即失效 |
| GET | `/api/auth/me` | 当前用户与权限点 |
| PUT | `/api/auth/password` | 修改自己的密码（需原密码，成功后需重新登录） |
| GET/POST | `/api/users` | 用户列表 / 新建（仅管理员） |
| GET | `/api/users/roles` | 角色与权限点说明（仅管理员） |
| PUT/DELETE | `/api/users/{id}` | 修改角色与状态 / 删除（仅管理员） |
| PUT | `/api/users/{id}/password` | 重置他人密码（仅管理员） |
| GET/POST | `/api/kb` | 知识库列表（仅返回有权查看的）/ 新建 |
| GET | `/api/kb/access-options` | 授权可选的角色与用户（需 `content:write`） |
| GET/PUT/DELETE | `/api/kb/{id}` | 详情 / 更新（含可见范围）/ 删除 |
| POST | `/api/kb/{id}/reindex` | 重建索引 |
| GET | `/api/kb/{kbId}/documents` | 文档分页（status、keyword、folderId，folderId=0 表示根目录，指定目录时含子目录） |
| POST | `/api/kb/{kbId}/documents/upload` | 上传（multipart，多文件，可选 folderId） |
| GET/POST | `/api/kb/{kbId}/folders` | 目录树 / 新建目录（name、parentId） |
| PUT/DELETE | `/api/folders/{id}` | 重命名或移动目录 / 删除目录（文档上移） |
| POST | `/api/documents/{id}/move` | 调整文档归属目录 |
| POST | `/api/kb/{kbId}/wiki` | 新建 Wiki 页面 |
| GET/PUT/DELETE | `/api/documents/{id}` | 文档详情 / 编辑 Wiki / 删除 |
| POST | `/api/documents/{id}/reindex` | 单文档重建 |
| GET | `/api/documents/{id}/chunks` | 分块列表 |
| GET | `/api/documents/{id}/download` | 下载原文 |
| GET | `/api/documents/{id}/raw` | 原始文件内联返回（PDF、图片等浏览器原生预览） |
| GET | `/api/documents/{id}/slides` | pptx 版式预览（HTML） |
| POST | `/api/search` | 向量检索 |
| POST | `/api/chat/ask` | RAG 问答 |
| GET/POST/PUT/DELETE | `/api/models` | Embedding / Chat 模型配置 |
| POST | `/api/models/{id}/default`、`/api/models/test`、`/api/models/test-chat` | 设默认、测活 |
| GET/PUT | `/api/settings` | 运行期设置 |
| GET | `/api/system/status` | 系统状态 |
| GET | `/api/stats/overview` | 概览统计 |

统一响应：`{ "code": 0, "message": "ok", "data": ... }`，异常由全局处理器转换为非 0 code。

除 `/api/auth/login` 外，所有 `/api/**` 都需要登录；未登录返回 HTTP 401，权限不足返回 HTTP 403。服务端按「路径 + 方法」判定所需权限点，规则集中在 `security/AuthInterceptor`。

---

## 9. 界面规范

所有界面 token 集中在 `web/src/styles/global.css` 顶部，改一处即可全站生效。

**颜色**：采用 [Radix Colors](https://www.radix-ui.com/colors) 明色阶，并按官方语义分层取用：

| 用途 | 取色 |
| --- | --- |
| 页面底色 / 面板 | `gray-2` / `#fff` |
| 组件底色、hover | `gray-3` |
| 边框、分隔线 | `gray-6` / `gray-5`（0.5px） |
| 主色实心（按钮） | `indigo-9`，hover `indigo-10` |
| 主色文字 / 选中底色 | `indigo-11` / `indigo-3` |
| 正文 / 次要文字 | `gray-12` / `gray-11`（均为可读对比度） |
| 语义色 | `green-9`、`amber-9`、`red-9`、`blue-9`，背景用对应 step 3，文字用 step 11 |

**字号**：仅三档 —— 12px（辅助信息）、13px（正文与控件）、20px（页面标题与统计数值）；层级通过字重、颜色、间距区分，不使用第四种字号。

**圆角**：8px（卡片、弹窗）→ 6px（输入框、按钮、标签页）→ 4px（标签、序号徽标），嵌套时逐级递减，保证同心。

**线条与阴影**：所有边框、分隔线使用 0.5px；卡片不使用阴影，仅弹窗、抽屉、下拉浮层保留功能性投影。

**动效**：仅保留颜色与背景过渡，统一 `250ms cubic-bezier(0.16, 1, 0.3, 1)`（ease-out），不使用位移与缩放动画。

**交互**：一屏一个主操作；次要或批量操作收进行内「更多」菜单，避免让用户在多个按钮之间做判断。表格在窄屏下自动隐藏次要列，保证操作列始终可见。

---

## 10. 数据结构与存储

| 内容 | 位置 |
| --- | --- |
| 元数据（H2 文件库） | `server/data/db/literag.mv.db` |
| 原始文件 | `server/data/uploads/{kbId}/...` |
| 向量（Qdrant） | Qdrant 数据目录下的 `storage/`（二进制方式为 exe 同级目录） |
| 向量（Chroma） | `data/chroma/`（由 compose 挂载） |
| 向量（本地回退） | H2 中的 `doc_chunk.embedding` 字段 |

启动时自动初始化：一个示例知识库 + 一个「本地哈希」Embedding 模型（未配置真实模型时也能跑通全链路）。

---

## 11. 常见问题

**检索结果为空或相似度很低？**
确认文档状态为「已就绪」、知识库 collection 有数据、Embedding 模型一致；本地哈希模型语义能力弱，建议换 bge-m3 等真实模型。

**向量库连不上怎么办？**
「自动」策略按 Qdrant → ChromaDB → 本地 的顺序选择，全部不可用时落到本地向量库（左下角状态会显示「本地向量库」）；显式指定某个引擎时会拒绝服务并提示。修好后可在「系统设置」切回。注意：切换引擎后向量不会自动迁移，需要对已有知识库逐个「重建索引」。

### 8.2 Qdrant 说明

- 服务端为 Rust 原生编译的单文件（Windows 版 zip 约 27 MB，解压即一个 `qdrant.exe`），无 Python / 运行时依赖；REST 端口 `6333`，浏览器打开 `http://localhost:6333/dashboard` 可看自带的管理界面。
- liteRAG 通过内置的轻量 REST 客户端访问，无需任何 SDK；可在「系统设置」配置地址、超时与 API Key（Qdrant 侧开启 `service.api_key` 时填写）。
- collection 在首次写入时按 embedding 实际维度创建（Qdrant 要求声明维度），因此**更换 embedding 模型后需对相关知识库重建索引**。
- 相似度使用 Cosine，score 语义（0~1，越大越相关）与 Chroma / 本地引擎一致，`minScore` 阈值通用。

**端口被占用？**
`java -jar ... --server.port=9090`，前端 `web/vite.config.js` 的代理目标同步调整。

**上传后一直「处理中」？**
查看 `data/server.log`。常见原因是文件加密、扫描件 PDF 无文本层（需 OCR，本系统未内置）。

**MCP 客户端连不上？**
先确认 `curl -X POST http://127.0.0.1:8080/mcp -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'` 有返回；若配置了 API Key，记得带 `X-API-Key`。
