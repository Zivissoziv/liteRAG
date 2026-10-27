<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">MCP 接入</h1>
      <div class="header-actions">
        <el-button :loading="probing" @click="selfTest">
          <el-icon><Cpu /></el-icon>协议自测
        </el-button>
      </div>
    </div>

    <div class="endpoint-grid">
      <div class="card endpoint">
        <div class="ep-head">
          <span class="ep-name">Streamable HTTP</span>
          <el-tag size="small" type="success">推荐</el-tag>
        </div>
        <div class="mono ep">POST {{ origin }}/mcp</div>
      </div>
      <div class="card endpoint">
        <div class="ep-head">
          <span class="ep-name">HTTP + SSE</span>
          <el-tag size="small">旧客户端</el-tag>
        </div>
        <div class="mono ep">GET {{ origin }}/mcp/sse</div>
        <div class="ep-note mono">POST /mcp/message?sessionId=…</div>
      </div>
      <div class="card endpoint">
        <div class="ep-head">
          <span class="ep-name">访问控制</span>
          <span class="dot" :class="requireKey ? 'ok' : 'off'"></span>
        </div>
        <div class="ep-state">{{ requireKey ? '已启用 API Key' : '未启用鉴权' }}</div>
        <div class="ep-note">{{ requireKey ? '请求需携带 X-API-Key' : '可在系统设置中配置 API Key' }}</div>
      </div>
    </div>

    <div class="card block">
      <div class="section-head">
        <h2 class="section-title"><el-icon><Tools /></el-icon>可用工具</h2>
      </div>
      <el-table :data="tools" size="small">
        <el-table-column label="工具" width="200">
          <template #default="{ row }">
            <span class="mono tool-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" prop="description" />
        <el-table-column label="参数" width="270">
          <template #default="{ row }">
            <span class="params">{{ row.params }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card block">
      <div class="section-head">
        <h2 class="section-title"><el-icon><Setting /></el-icon>客户端配置</h2>
        <el-button size="small" @click="copy(currentSnippet)">
          <el-icon><CopyDocument /></el-icon>复制配置
        </el-button>
      </div>

      <el-tabs v-model="snippet">
        <el-tab-pane label="Cursor / Claude Code" name="cursor" />
        <el-tab-pane label="Claude Desktop" name="claude" />
        <el-tab-pane label="命令行" name="curl" />
      </el-tabs>

      <div class="code-wrap">
        <pre class="code">{{ currentSnippet }}</pre>
      </div>

      <p v-if="snippet === 'claude'" class="claude-note">
        Claude Desktop 需通过 mcp-remote 代理远程 MCP；若客户端支持直接填写 URL，可参考 Cursor 的写法。
      </p>
    </div>

    <div class="card block">
      <div class="section-head">
        <h2 class="section-title"><el-icon><Notebook /></el-icon>资源</h2>
      </div>
      <div class="res-list">
        <div class="res-item">
          <span class="mono">literag://knowledge-bases</span>
          <span class="res-desc">知识库清单</span>
        </div>
        <div class="res-item">
          <span class="mono">literag://kb/{kbId}/documents</span>
          <span class="res-desc">知识库文档列表</span>
        </div>
        <div class="res-item">
          <span class="mono">literag://doc/{docId}</span>
          <span class="res-desc">文档全文</span>
        </div>
      </div>
    </div>

    <div v-if="probeResult" class="card">
      <div class="section-head">
        <h2 class="section-title"><el-icon><CircleCheck /></el-icon>自测结果</h2>
      </div>
      <pre class="code">{{ probeResult }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { systemApi } from '../api'

const origin = ref('http://localhost:8080')
const requireKey = ref(false)
const snippet = ref('cursor')
const probing = ref(false)
const probeResult = ref('')

const tools = [
  {
    name: 'search_knowledge',
    description: '语义检索知识库，返回片段、来源文档与相似度',
    params: 'query*, kb_ids[], top_k, min_score'
  },
  {
    name: 'ask_knowledge',
    description: '检索增强问答，答案带引用编号',
    params: 'question*, kb_ids[], top_k'
  },
  { name: 'list_knowledge_bases', description: '列出全部知识库及统计信息', params: '无' },
  { name: 'list_documents', description: '列出指定知识库下的文档，可按目录或关键字过滤', params: 'kb_id*, folder, keyword, limit' },
  { name: 'get_document', description: '读取文档全文，支持分段读取', params: 'doc_id*, offset, limit' }
]

const base = computed(() => `${origin.value}/mcp`)

const currentSnippet = computed(() => {
  const keyLine = requireKey.value ? ',\n      "headers": { "X-API-Key": "<你的密钥>" }' : ''
  if (snippet.value === 'cursor') {
    return `// .cursor/mcp.json 或 Claude Code 的 mcp 配置
{
  "mcpServers": {
    "literag": {
      "url": "${base.value}",
      "type": "http"${keyLine}
    }
  }
}`
  }
  if (snippet.value === 'claude') {
    return `// claude_desktop_config.json
{
  "mcpServers": {
    "literag": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "${base.value}"${requireKey.value ? ',\n        "--header",\n        "X-API-Key: <你的密钥>"' : ''}
      ]
    }
  }
}`
  }
  const keyHeader = requireKey.value ? ` \\\n  -H 'X-API-Key: <你的密钥>'` : ''
  return `# 列出工具
curl -X POST ${base.value} \\
  -H 'Content-Type: application/json'${keyHeader} \\
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# 调用检索
curl -X POST ${base.value} \\
  -H 'Content-Type: application/json'${keyHeader} \\
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search_knowledge","arguments":{"query":"如何接入 MCP","top_k":3}}}'`
})

const copy = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

const selfTest = async () => {
  probing.value = true
  probeResult.value = ''
  try {
    const call = async (body) => {
      const res = await fetch('/mcp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })
      return { status: res.status, body: await res.json() }
    }
    const init = await call({
      jsonrpc: '2.0',
      id: 1,
      method: 'initialize',
      params: {
        protocolVersion: '2025-06-18',
        capabilities: {},
        clientInfo: { name: 'literag-web-selftest', version: '1.0.0' }
      }
    })
    const list = await call({ jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} })
    const serverInfo = init.body?.result?.serverInfo
    const names = (list.body?.result?.tools || []).map((t) => t.name).join(', ')
    probeResult.value =
      `initialize → HTTP ${init.status}，协议 ${init.body?.result?.protocolVersion}，服务 ${serverInfo?.name} v${serverInfo?.version}\n` +
      `tools/list → ${list.body?.result?.tools?.length || 0} 个工具：${names}`
  } catch (e) {
    probeResult.value = `自测失败：${e.message}`
  } finally {
    probing.value = false
  }
}

onMounted(async () => {
  const devPort = window.location.port === '5173'
  origin.value = devPort
    ? `${window.location.protocol}//${window.location.hostname}:8080`
    : window.location.origin
  try {
    const res = await systemApi.status()
    requireKey.value = !!res?.mcp?.requireApiKey
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.endpoint-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.endpoint {
  min-height: 96px;
}
.ep-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-caption);
  color: var(--text-muted);
}
.ep-name {
  font-weight: var(--fw-strong);
  color: var(--text);
  font-size: var(--fs-body);
}
.ep {
  margin-top: 10px;
  color: var(--text);
  word-break: break-all;
}
.ep-note {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  margin-top: 6px;
  word-break: break-all;
}
.ep-state {
  font-size: var(--fs-body);
  color: var(--text);
  margin-top: 10px;
}
.block {
  margin-bottom: 12px;
}
.tool-name {
  color: var(--accent-text);
  font-weight: var(--fw-strong);
}
.params {
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: var(--fs-caption);
  color: var(--text-muted);
}
.code-wrap {
  position: relative;
}
.claude-note {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  margin: 10px 0 0;
  line-height: 1.6;
}
.res-list {
  display: flex;
  flex-direction: column;
}
.res-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 9px 0;
  border-bottom: 0.5px solid var(--border-subtle);
  font-size: var(--fs-caption);
}
.res-item:last-child {
  border-bottom: 0;
}
.res-item .mono {
  color: var(--text);
  min-width: 260px;
}
.res-desc {
  color: var(--text-faint);
}
@media (max-width: 1080px) {
  .endpoint-grid {
    grid-template-columns: 1fr;
  }
}
</style>
