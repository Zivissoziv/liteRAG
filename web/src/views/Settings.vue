<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">系统设置</h1>
      <div class="header-actions">
        <el-button :loading="loading" @click="load">重新加载</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      </div>
    </div>

    <div class="stack">
      <div class="card">
        <div class="section-head">
          <h2 class="section-title"><el-icon><Coin /></el-icon>向量存储</h2>
        </div>

        <div class="status-row">
          <span class="dot" :class="chroma?.available ? 'ok' : 'off'"></span>
          <span class="status-text">
            {{ chroma?.available ? `ChromaDB 已连接，API ${chroma.version}` : 'ChromaDB 未连接' }}
          </span>
          <span class="mono url">{{ chroma?.url }}</span>
          <el-button size="small" text @click="refreshStatus">刷新</el-button>
        </div>
        <div class="status-row">
          <span class="dot" :class="qdrant?.available ? 'ok' : 'off'"></span>
          <span class="status-text">
            {{ qdrant?.available ? 'Qdrant 已连接' : 'Qdrant 未连接' }}
          </span>
          <span class="mono url">{{ qdrant?.url }}</span>
        </div>

        <el-form label-width="120px" label-position="left" class="form">
          <el-form-item label="存储策略">
            <el-radio-group v-model="form.vectorProvider">
              <el-radio-button value="auto">自动</el-radio-button>
              <el-radio-button value="chroma">ChromaDB</el-radio-button>
              <el-radio-button value="qdrant">Qdrant</el-radio-button>
              <el-radio-button value="local">本地</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="ChromaDB 地址">
            <el-input v-model="form.chromaUrl" placeholder="http://localhost:8000" class="w-lg" />
          </el-form-item>
          <el-form-item label="API 版本">
            <el-select v-model="form.chromaApiVersion" class="w-md">
              <el-option value="auto" label="自动探测" />
              <el-option value="v2" label="v2（Chroma 0.5+ / 1.x）" />
              <el-option value="v1" label="v1（Chroma 0.4.x）" />
            </el-select>
          </el-form-item>
          <div class="row2 wide">
            <el-form-item label="Tenant">
              <el-input v-model="form.chromaTenant" placeholder="default_tenant" />
            </el-form-item>
            <el-form-item label="Database">
              <el-input v-model="form.chromaDatabase" placeholder="default_database" />
            </el-form-item>
          </div>
          <el-form-item label="Qdrant 地址">
            <el-input v-model="form.qdrantUrl" placeholder="http://localhost:6333" class="w-lg" />
          </el-form-item>
          <el-form-item label="Qdrant Key">
            <el-input v-model="form.qdrantApiKey" placeholder="未启用认证则留空" show-password class="w-lg" />
          </el-form-item>
          <p class="hint form-hint">切换存储策略后需对已有知识库重建索引；向量不会在引擎间自动迁移。</p>
        </el-form>
      </div>

      <div class="card">
        <div class="section-head">
          <h2 class="section-title"><el-icon><Aim /></el-icon>检索与分块</h2>
        </div>
        <el-form label-width="120px" label-position="left" class="form">
          <div class="row2">
            <el-form-item label="分块长度">
              <el-input-number v-model="num.chunkSize" :min="200" :max="4000" :step="50" controls-position="right" />
            </el-form-item>
            <el-form-item label="重叠长度">
              <el-input-number v-model="num.chunkOverlap" :min="0" :max="1000" :step="10" controls-position="right" />
            </el-form-item>
          </div>
          <div class="row2">
            <el-form-item label="返回片段数">
              <el-input-number v-model="num.defaultTopK" :min="1" :max="50" controls-position="right" />
            </el-form-item>
            <el-form-item label="相似度阈值">
              <el-input-number v-model="num.minScore" :min="0" :max="1" :step="0.05" :precision="2" controls-position="right" />
            </el-form-item>
          </div>
          <div class="row2">
            <el-form-item label="上下文上限">
              <el-input-number v-model="num.maxContextChars" :min="500" :max="50000" :step="500" controls-position="right" />
            </el-form-item>
            <el-form-item label="问答温度">
              <el-input-number v-model="num.llmTemperature" :min="0" :max="1.5" :step="0.1" :precision="1" controls-position="right" />
            </el-form-item>
          </div>
          <p class="hint form-hint">上下文上限为拼给大模型的参考资料字符数；问答温度建议 0.1 至 0.3。</p>
        </el-form>
      </div>

      <div class="card">
        <div class="section-head">
          <h2 class="section-title"><el-icon><Files /></el-icon>元数据库</h2>
          <el-button size="small" text :loading="loading" @click="load">刷新</el-button>
        </div>

        <div class="status-row">
          <span class="dot" :class="db?.available ? 'ok' : 'off'"></span>
          <span class="status-text">{{ dbTitle }}</span>
          <span class="mono url">{{ db?.file || db?.url }}</span>
        </div>
        <p class="hint">
          默认使用 SQLite 单文件库（<code>server/data/db/literag.db</code>），停服务数据仍在、无需额外进程；
          需要多人共享或更大并发时，可用 <code>--spring.profiles.active=mysql</code> 切到 MySQL。
          表结构由 Hibernate 自动创建，切换库前请先迁移数据。
        </p>
      </div>

      <div class="card">
        <div class="section-head">
          <h2 class="section-title"><el-icon><Key /></el-icon>MCP 访问控制</h2>
        </div>
        <el-form label-width="120px" label-position="left" class="form">
          <el-form-item label="API Key">
            <el-input v-model="form.mcpApiKey" placeholder="留空则不校验" show-password class="w-lg" />
            <el-button size="small" text @click="genKey">随机生成</el-button>
          </el-form-item>
        </el-form>
        <p class="hint">部署在公网时必须配置，客户端通过 X-API-Key 或 Authorization: Bearer 携带。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { systemApi } from '../api'

const loading = ref(false)
const saving = ref(false)
const chroma = ref(null)
const qdrant = ref(null)
const db = ref(null)
const form = reactive({
  vectorProvider: 'auto',
  chromaUrl: '',
  chromaApiVersion: 'auto',
  chromaTenant: 'default_tenant',
  chromaDatabase: 'default_database',
  qdrantUrl: '',
  qdrantApiKey: '',
  mcpApiKey: ''
})
const num = reactive({
  chunkSize: 800,
  chunkOverlap: 120,
  defaultTopK: 6,
  minScore: 0,
  maxContextChars: 6000,
  llmTemperature: 0.2
})

const formatSize = (n) => {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

const dbTitle = computed(() => {
  if (!db.value) return '元数据库信息获取中'
  if (!db.value.available) return `元数据库不可用：${db.value.error || '未知错误'}`
  const name =
    db.value.kind === 'SQLITE' ? 'SQLite' : db.value.kind === 'MYSQL' ? 'MySQL' : db.value.product || '数据库'
  const size = db.value.size > 0 ? ` · ${formatSize(db.value.size)}` : ''
  return `${name} 已连接${size}`
})

const load = async () => {
  loading.value = true
  try {
    // /system/status 是 /settings 的超集，一次请求即可拿到设置、向量与数据库信息
    const res = await systemApi.status()
    const s = res.settings || {}
    form.vectorProvider = s.vectorProvider || 'auto'
    form.chromaUrl = s.chromaUrl || ''
    form.chromaApiVersion = s.chromaApiVersion || 'auto'
    form.chromaTenant = s.chromaTenant || 'default_tenant'
    form.chromaDatabase = s.chromaDatabase || 'default_database'
    form.qdrantUrl = s.qdrantUrl || 'http://localhost:6333'
    form.qdrantApiKey = s.qdrantApiKey || ''
    form.mcpApiKey = s.mcpApiKey || ''
    num.chunkSize = Number(s.chunkSize || 800)
    num.chunkOverlap = Number(s.chunkOverlap || 120)
    num.defaultTopK = Number(s.defaultTopK || 6)
    num.minScore = Number(s.minScore || 0)
    num.maxContextChars = Number(s.maxContextChars || 6000)
    num.llmTemperature = Number(s.llmTemperature || 0.2)
    chroma.value = res.vector?.chroma || null
    qdrant.value = res.vector?.qdrant || null
    db.value = res.database || null
  } finally {
    loading.value = false
  }
}

const refreshStatus = async () => {
  const res = await systemApi.status()
  chroma.value = res.vector?.chroma || null
  qdrant.value = res.vector?.qdrant || null
  db.value = res.database || null
  const ok = [chroma.value?.available && 'ChromaDB', qdrant.value?.available && 'Qdrant'].filter(Boolean)
  ElMessage.success(ok.length ? `${ok.join('、')} 连接正常` : 'ChromaDB / Qdrant 均无法连接')
}

const genKey = () => {
  const rand = Array.from({ length: 32 }, () =>
    'abcdefghijklmnopqrstuvwxyz0123456789'[Math.floor(Math.random() * 36)]
  ).join('')
  form.mcpApiKey = `lr_${rand}`
}

const save = async () => {
  saving.value = true
  try {
    const payload = {
      vectorProvider: form.vectorProvider,
      chromaUrl: form.chromaUrl,
      chromaApiVersion: form.chromaApiVersion,
      chromaTenant: form.chromaTenant,
      chromaDatabase: form.chromaDatabase,
      qdrantUrl: form.qdrantUrl,
      qdrantApiKey: form.qdrantApiKey,
      mcpApiKey: form.mcpApiKey,
      chunkSize: String(num.chunkSize),
      chunkOverlap: String(num.chunkOverlap),
      defaultTopK: String(num.defaultTopK),
      minScore: String(num.minScore),
      maxContextChars: String(num.maxContextChars),
      llmTemperature: String(num.llmTemperature)
    }
    const res = await systemApi.saveSettings(payload)
    chroma.value = res.vector?.chroma || null
    qdrant.value = res.vector?.qdrant || null
    ElMessage.success('设置已保存')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.form {
  margin-top: 4px;
}

/* 标签固定宽度并右对齐，控件起点形成一条干净的对齐线 */
.form :deep(.el-form-item__label) {
  width: 120px !important;
  padding-right: 16px;
  justify-content: flex-end;
  white-space: nowrap;
  color: var(--text-muted);
}

.form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.form :deep(.el-input-number) {
  width: 148px;
}

.w-md {
  width: 240px;
}

.w-lg {
  max-width: 440px;
}

.form-hint {
  margin: 12px 0 0 120px;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--r-control);
  background: var(--bg-subtle);
  border: 0.5px solid var(--border-subtle);
  margin-bottom: 18px;
  min-height: 36px;
}
.status-text {
  font-size: var(--fs-caption);
  color: var(--text-muted);
}
.url {
  color: var(--text-faint);
  margin-left: auto;
}
.row2 {
  display: grid;
  grid-template-columns: repeat(2, max-content);
  gap: 0 40px;
}
.row2.wide {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
}
.row2.wide :deep(.el-input) {
  max-width: 320px;
}
@media (max-width: 960px) {
  .row2,
  .row2.wide {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
  .form-hint {
    margin-left: 0;
  }
  .form :deep(.el-form-item__label) {
    justify-content: flex-start;
  }
}
</style>
