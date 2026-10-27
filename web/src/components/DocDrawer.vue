<template>
  <el-drawer v-model="visible" class="literag-drawer" :with-header="false" @closed="reset">
    <div v-if="doc" class="drawer">
      <div class="drawer-head">
        <div class="head-main">
          <div class="title">
            <el-icon class="ext-icon"><Document /></el-icon>
            <span>{{ doc.name }}</span>
          </div>
          <div class="sub">
            <el-tag size="small">{{ doc.sourceType === 'WIKI' ? 'Wiki' : doc.fileExt?.toUpperCase() }}</el-tag>
            <el-tag size="small" :type="statusType(doc.status)">{{ statusText(doc.status) }}</el-tag>
            <span class="meta">{{ doc.charCount || 0 }} 字</span>
            <span class="meta">{{ doc.chunkCount || 0 }} 片段</span>
            <span class="meta">{{ strategyLabel }}</span>
            <span class="meta">v{{ doc.ver }}</span>
          </div>
        </div>
        <div class="head-actions">
          <el-button v-if="doc.sourceType !== 'WIKI'" size="small" @click="download">下载</el-button>
          <el-button size="small" :loading="busy" @click="reindex">重新索引</el-button>
          <el-button size="small" @click="visible = false">关闭</el-button>
        </div>
      </div>

      <el-alert
        v-if="doc.status === 'FAILED'"
        type="error"
        :closable="false"
        :title="doc.errorMsg || '入库失败'"
        style="margin-bottom: 12px"
      />

      <el-tabs v-model="tab">
        <el-tab-pane label="预览" name="preview">
          <DocPreview :doc="doc" @fallback="tab = 'text'" />
        </el-tab-pane>
        <el-tab-pane label="解析文本" name="text">
          <pre class="plain">{{ doc.content || '内容为空' }}</pre>
        </el-tab-pane>
        <el-tab-pane :label="`知识片段 ${chunkTotal}`" name="chunks">
          <div v-if="!chunks.length" class="empty">暂无片段</div>
          <div v-for="chunk in chunks" :key="chunk.id" class="chunk">
            <div class="chunk-head">
              <span class="seq">#{{ chunk.seq }}</span>
              <span class="chunk-meta">{{ chunk.charCount }} 字</span>
              <span v-if="chunk.dim" class="chunk-meta">{{ chunk.dim }} 维</span>
              <span class="chunk-meta vector-id">{{ chunk.vectorId?.slice(0, 8) }}</span>
            </div>
            <div class="chunk-body">{{ chunk.content }}</div>
          </div>
          <div v-if="chunks.length < chunkTotal" class="load-more">
            <el-button size="small" :loading="loadingChunks" @click="loadChunks(chunkPage + 1)">加载更多</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
    <div v-else class="empty">加载中</div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { docApi } from '../api'
import DocPreview from './DocPreview.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  docId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const doc = ref(null)
const chunks = ref([])

const strategyLabel = computed(() =>
  ({ AUTO: '智能分块', HEADING: '标题结构', TABLE: '表格优先', FIXED: '固定长度' }[doc.value?.chunkStrategy] || '')
)
const chunkTotal = ref(0)
const chunkPage = ref(0)
const loadingChunks = ref(false)
const tab = ref('preview')
const busy = ref(false)

const statusText = (s) =>
  ({ PENDING: '排队中', PARSING: '解析中', EMBEDDING: '向量化', READY: '已就绪', FAILED: '失败' }[s] || s)
const statusType = (s) =>
  ({ READY: 'success', FAILED: 'danger', PENDING: 'info', PARSING: 'warning', EMBEDDING: 'warning' }[s] || 'info')

const loadDoc = async () => {
  if (!props.docId) return
  doc.value = await docApi.detail(props.docId, true)
  chunks.value = []
  chunkTotal.value = 0
  await loadChunks(1)
}

const loadChunks = async (page) => {
  if (!props.docId) return
  loadingChunks.value = true
  try {
    const res = await docApi.chunks(props.docId, { page, size: 10 })
    chunks.value = page === 1 ? res.list : [...chunks.value, ...res.list]
    chunkTotal.value = res.total
    chunkPage.value = page
  } finally {
    loadingChunks.value = false
  }
}

const reindex = async () => {
  busy.value = true
  try {
    await docApi.reindex(props.docId)
    emit('changed')
    await loadDoc()
  } finally {
    busy.value = false
  }
}

const download = () => {
  window.open(docApi.downloadUrl(props.docId), '_blank')
}

const reset = () => {
  doc.value = null
  chunks.value = []
  chunkTotal.value = 0
  chunkPage.value = 0
  tab.value = 'preview'
}

watch(
  () => [props.modelValue, props.docId],
  ([open]) => {
    if (open) loadDoc()
  }
)
</script>

<style scoped>
.drawer {
  padding: 0 2px;
}
.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 0.5px solid var(--border-subtle);
  margin-bottom: 4px;
}
.head-main {
  min-width: 0;
}
.title {
  font-size: var(--fs-body);
  font-weight: var(--fw-strong);
  display: flex;
  align-items: center;
  gap: 8px;
  word-break: break-all;
}
.ext-icon {
  font-size: 15px;
  color: var(--text-faint);
  flex-shrink: 0;
}
.sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.meta {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
}
.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.plain {
  max-height: calc(100vh - 232px);
  overflow: auto;
  padding: 14px 16px;
  border: 0.5px solid var(--border);
  border-radius: var(--r-control);
  background: var(--gray-1);
  font-size: var(--fs-body);
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-muted);
  margin: 0;
  font-family: var(--el-font-family);
}
.chunk {
  border: 0.5px solid var(--border);
  border-radius: var(--r-control);
  padding: 10px 12px;
  margin-bottom: 8px;
  background: var(--gray-1);
}
.chunk-head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 6px;
}
.seq {
  font-size: var(--fs-caption);
  font-weight: var(--fw-strong);
  color: var(--accent-text);
  background: var(--accent-bg);
  border-radius: var(--r-inner);
  padding: 0 5px;
  font-variant-numeric: tabular-nums;
}
.chunk-meta {
  font-size: var(--fs-caption);
  color: var(--text-faint);
}
.vector-id {
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  margin-left: auto;
}
.chunk-body {
  font-size: var(--fs-body);
  line-height: 1.7;
  color: var(--text-muted);
  white-space: pre-wrap;
  word-break: break-word;
}
.load-more {
  text-align: center;
  padding: 4px 0 12px;
}
</style>
