<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">概览</h1>
      <div class="header-actions">
        <el-button @click="router.push('/search')">检索测试</el-button>
        <el-button type="primary" @click="router.push('/kb')">
          <el-icon v-if="canWrite"><Plus /></el-icon>{{ canWrite ? '新建知识库' : '浏览知识库' }}
        </el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label"><el-icon><Collection /></el-icon>知识库</div>
        <div class="stat-value">{{ over?.kbCount ?? '-' }}</div>
        <div class="stat-foot">{{ over?.docCount ?? 0 }} 篇文档</div>
      </div>
      <div class="stat-card">
        <div class="stat-label"><el-icon><Files /></el-icon>文档</div>
        <div class="stat-value">{{ over?.docCount ?? '-' }}</div>
        <div class="stat-foot">
          <span>就绪 <b>{{ over?.readyCount ?? 0 }}</b></span>
          <span :class="{ alert: (over?.processingCount ?? 0) > 0 }">处理中 <b>{{ over?.processingCount ?? 0 }}</b></span>
          <span :class="{ danger: (over?.failedCount ?? 0) > 0 }">失败 <b>{{ over?.failedCount ?? 0 }}</b></span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label"><el-icon><Grid /></el-icon>知识片段</div>
        <div class="stat-value">{{ over?.chunkCount ?? '-' }}</div>
        <div class="stat-foot">分块 {{ over?.vector?.chunkSize ?? '-' }} 字 / 重叠 {{ over?.vector?.chunkOverlap ?? '-' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label"><el-icon><Cpu /></el-icon>向量后端</div>
        <div class="stat-value">{{ engineLabel(activeEngine) }}</div>
        <div class="stat-foot">
          <span class="dot" :class="engineOk ? 'ok' : 'off'"></span>
          <span>{{ engineFoot }}</span>
        </div>
      </div>
    </div>

    <div class="two-col">
      <div class="card">
        <div class="section-head">
          <h2 class="section-title">最近文档</h2>
          <el-button size="small" text @click="load">刷新</el-button>
        </div>
        <el-table :data="over?.recentDocuments || []" size="small" @row-click="openDoc" style="cursor: pointer">
          <el-table-column label="文档" min-width="160">
            <template #default="{ row }">
              <div class="doc-cell">
                <FileBadge :doc="row" />
                <span class="doc-name">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="知识库" prop="kbName" width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="84">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上传时间" width="136">
            <template #default="{ row }">
              <span class="time">{{ formatTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!(over?.recentDocuments || []).length" class="empty">暂无文档</div>
      </div>

      <div class="card status-card">
        <div class="section-head">
          <h2 class="section-title">服务状态</h2>
        </div>
        <div class="kv">
          <div class="kv-row">
            <span>向量库</span>
            <el-tag size="small" :type="engineOk ? (activeEngine === 'local' ? 'info' : 'success') : 'warning'">
              {{ engineLabel(activeEngine) }}
            </el-tag>
          </div>
          <div class="kv-row">
            <span>地址</span>
            <b class="mono">{{ engineUrl }}</b>
          </div>
          <div class="kv-row">
            <span>API 版本</span>
            <b>{{ activeEngine === 'chroma' ? (vec.chroma?.version || '-') : '-' }}</b>
          </div>
          <div class="kv-row">
            <span>租户 / 库</span>
            <b class="mono">{{ activeEngine === 'chroma' ? `${vec.chroma?.tenant || '-'} / ${vec.chroma?.database || '-'}` : '-' }}</b>
          </div>
          <div class="kv-row">
            <span>模型配置</span>
            <b>{{ over?.modelCount ?? 0 }} 个</b>
          </div>
          <div class="kv-row">
            <span>MCP 端点</span>
            <b class="mono">POST /mcp</b>
          </div>
        </div>
      </div>
    </div>

    <div class="card kb-section">
      <div class="section-head">
        <h2 class="section-title">知识库</h2>
        <el-button size="small" text @click="router.push('/kb')">全部知识库</el-button>
      </div>
      <div v-if="(over?.knowledgeBases || []).length" class="kb-list">
        <div
          v-for="kb in (over?.knowledgeBases || []).slice(0, 4)"
          :key="kb.id"
          class="kb-row"
          @click="router.push(`/kb/${kb.id}`)"
        >
          <div class="kb-tile">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="kb-main">
            <div class="kb-name">{{ kb.name }}</div>
            <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
          </div>
          <div class="kb-meta">
            <span><b>{{ kb.docCount || 0 }}</b> 文档</span>
            <span><b>{{ kb.chunkCount || 0 }}</b> 片段</span>
          </div>
          <el-icon class="chev"><ArrowRight /></el-icon>
        </div>
      </div>
      <div v-else class="empty">暂无知识库</div>
    </div>

    <DocDrawer v-model="docOpen" :doc-id="currentDocId" @changed="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { systemApi } from '../api'
import { formatTime, statusText, statusType } from '../utils/format'
import FileBadge from '../components/FileBadge.vue'
import DocDrawer from '../components/DocDrawer.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const canWrite = computed(() => auth.permissions.includes('content:write'))
const over = ref(null)
const docOpen = ref(false)
const currentDocId = ref(null)
let timer = null

const vec = computed(() => over.value?.vector || {})
const activeEngine = computed(() => vec.value.active || '')

const engineLabel = (a) => ({ qdrant: 'Qdrant', chroma: 'ChromaDB', local: '本地' }[a] || (a ? '不可用' : '-'))

const engineOk = computed(() => {
  const a = activeEngine.value
  if (a === 'local') return true
  if (a === 'qdrant') return !!vec.value.qdrant?.available
  if (a === 'chroma') return !!vec.value.chroma?.available
  return false
})

const engineUrl = computed(() => {
  const a = activeEngine.value
  if (a === 'qdrant') return vec.value.qdrant?.url || '-'
  if (a === 'chroma') return vec.value.chroma?.url || '-'
  return '进程内'
})

const engineFoot = computed(() => {
  const a = activeEngine.value
  if (a === 'local') return '进程内余弦检索'
  return engineOk.value ? '连接正常' : '未连接'
})

const load = async () => {
  over.value = await systemApi.overview()
}

const openDoc = (row) => {
  currentDocId.value = row.id
  docOpen.value = true
}

onMounted(() => {
  load()
  timer = setInterval(load, 5000)
})
onUnmounted(() => timer && clearInterval(timer))
</script>

<style scoped>
.two-col {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
  margin-bottom: 12px;
}
.doc-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.doc-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.time {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.status-card {
  display: flex;
  flex-direction: column;
}
.kv {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  flex: 1;
  min-height: 0;
}.kv-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 40px;
  font-size: var(--fs-body);
  color: var(--text-muted);
  border-bottom: 0.5px solid var(--border-subtle);
}
.kv-row:last-child {
  border-bottom: 0;
}
.kv-row b {
  color: var(--text);
  font-weight: normal;
  font-size: var(--fs-caption);
  text-align: right;
  word-break: break-all;
}
.kb-section {
  margin-top: 0;
}
.kb-list {
  display: flex;
  flex-direction: column;
}
.kb-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  margin: 0 -8px;
  border-radius: var(--r-control);
  border-bottom: 0.5px solid var(--border-subtle);
  cursor: pointer;
  transition: background-color var(--dur) var(--ease);
}
.kb-row:last-child {
  border-bottom: 0;
}
.kb-row:hover {
  background: var(--bg-subtle);
}
.kb-tile {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-control);
  background: var(--accent-bg);
  border: 0.5px solid var(--accent-border);
  color: var(--accent-text);
  font-size: 14px;
}
.kb-main {
  min-width: 0;
  flex: 1;
}
.kb-name {
  font-size: var(--fs-body);
  font-weight: var(--fw-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kb-desc {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kb-meta {
  display: flex;
  gap: 18px;
  font-size: var(--fs-caption);
  color: var(--text-faint);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.kb-meta b {
  color: var(--text-muted);
  font-weight: var(--fw-strong);
}
.chev {
  font-size: 13px;
  color: var(--gray-8);
  flex: 0 0 13px;
  transition: transform var(--dur) var(--ease), color var(--dur) var(--ease);
}
.kb-row:hover .chev {
  transform: translateX(2px);
  color: var(--text-muted);
}
@media (max-width: 1080px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
