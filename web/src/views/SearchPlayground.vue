<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">检索问答</h1>
    </div>

    <div class="layout">
      <div class="card control">
        <el-radio-group v-model="mode" size="small" class="mode">
          <el-radio-button value="search">语义检索</el-radio-button>
          <el-radio-button value="ask">RAG 问答</el-radio-button>
        </el-radio-group>

        <el-input
          v-model="query"
          type="textarea"
          :rows="4"
          resize="none"
          :placeholder="mode === 'ask' ? '输入问题' : '输入检索内容或一段原文'"
          @keydown.ctrl.enter="run"
        />

        <div class="field">
          <label>知识库范围</label>
          <el-select
            v-model="selectedKbs"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="全部知识库"
            style="width: 100%"
          >
            <el-option v-for="kb in kbs" :key="kb.id" :value="kb.id" :label="kb.name">
              <div class="opt-row">
                <span>{{ kb.name }}</span>
                <span class="opt-dim">{{ kb.chunkCount || 0 }} 片段</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <div class="field">
          <div class="field-head">
            <label>返回片段数</label>
            <b>{{ topK }}</b>
          </div>
          <el-slider v-model="topK" :min="1" :max="20" :step="1" />
        </div>

        <div class="field">
          <div class="field-head">
            <label>相似度阈值</label>
            <b>{{ minScore.toFixed(2) }}</b>
          </div>
          <el-slider v-model="minScore" :min="0" :max="0.9" :step="0.05" />
        </div>

        <el-button class="run" type="primary" :loading="loading" @click="run">
          <el-icon><Search /></el-icon>{{ mode === 'ask' ? '提问' : '检索' }}
        </el-button>

        <div class="examples">
          <div class="examples-title">示例问题</div>
          <button v-for="ex in examples" :key="ex" type="button" class="example" @click="useExample(ex)">
            {{ ex }}
          </button>
        </div>
      </div>

      <div class="result">
        <div v-if="!result && !answer" class="card placeholder">
          <div class="ph-icon"><el-icon><Aim /></el-icon></div>
          <div class="ph-title">{{ mode === 'ask' ? '尚未提问' : '尚未执行检索' }}</div>
          <div class="ph-hint">Ctrl + Enter 执行</div>
        </div>

        <template v-if="mode === 'search' && result">
          <div class="result-head">
            <span>命中 <b>{{ result.total }}</b> 条</span>
            <span>{{ backendLabel(result.backend) }}</span>
            <span>{{ result.tookMs }} ms</span>
          </div>
          <div v-if="!result.hits.length" class="card empty">未召回到片段，可降低阈值或换个问法</div>
          <div v-for="(hit, i) in result.hits" :key="hit.vectorId" class="hit-card">
            <div class="hit-head">
              <span class="rank">{{ i + 1 }}</span>
              <span class="doc">{{ hit.docName }}</span>
              <el-tag size="small">{{ hit.kbName }}</el-tag>
              <el-tag size="small">#{{ hit.seq }}</el-tag>
              <div class="spacer"></div>
              <span class="score">{{ hit.score.toFixed(4) }}</span>
              <el-button size="small" text @click="openDoc(hit.docId)">查看文档</el-button>
            </div>
            <div class="score-bar">
              <div class="score-fill" :style="{ width: Math.max(2, hit.score * 100) + '%' }"></div>
            </div>
            <div class="hit-content">{{ hit.content }}</div>
          </div>
        </template>

        <template v-if="mode === 'ask' && answer">
          <div class="card answer-card">
            <div class="answer-head">
              <h2 class="section-title"><el-icon><ChatDotRound /></el-icon>回答</h2>
              <div class="answer-meta">
                <el-tag v-if="answer.llmUsed" size="small" type="success">{{ answer.model }}</el-tag>
                <el-tag v-else size="small" type="warning">未配置对话模型</el-tag>
                <span class="took">{{ answer.tookMs }} ms</span>
              </div>
            </div>
            <div class="markdown-body" v-html="render(answer.answer)"></div>
          </div>

          <div class="result-head citations-head">
            <span>引用 <b>{{ answer.citations?.length || 0 }}</b> 条</span>
            <span>{{ backendLabel(answer.backend) }}</span>
          </div>
          <div v-for="(hit, i) in answer.citations || []" :key="hit.vectorId" class="hit-card">
            <div class="hit-head">
              <span class="rank">{{ i + 1 }}</span>
              <span class="doc">{{ hit.docName }}</span>
              <el-tag size="small">{{ hit.kbName }}</el-tag>
              <el-tag size="small">#{{ hit.seq }}</el-tag>
              <div class="spacer"></div>
              <span class="score">{{ hit.score.toFixed(4) }}</span>
              <el-button size="small" text @click="openDoc(hit.docId)">查看文档</el-button>
            </div>
            <div class="hit-content">{{ hit.content }}</div>
          </div>
        </template>
      </div>
    </div>

    <DocDrawer v-model="docOpen" :doc-id="currentDocId" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import { ElMessage } from 'element-plus'
import { kbApi, searchApi } from '../api'
import DocDrawer from '../components/DocDrawer.vue'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const render = (text) => md.render(text || '')

const kbs = ref([])
const mode = ref('search')
const query = ref('')
const selectedKbs = ref([])
const topK = ref(6)
const minScore = ref(0)
const loading = ref(false)
const result = ref(null)
const answer = ref(null)
const docOpen = ref(false)
const currentDocId = ref(null)

const examples = computed(() => {
  if (!kbs.value.length) return ['系统支持哪些文件格式？']
  return ['支持哪些文件格式？', 'MCP 服务如何接入？', '分块长度怎么设置？']
})

const backendLabel = (b) => ({ qdrant: 'Qdrant', local: '本地向量库' }[b] || b || '-')

const useExample = (text) => {
  query.value = text
  mode.value = 'ask'
}

const run = async () => {
  if (!query.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'search') {
      answer.value = null
      result.value = await searchApi.search({
        query: query.value.trim(),
        kbIds: selectedKbs.value.length ? selectedKbs.value : null,
        topK: topK.value,
        minScore: minScore.value
      })
    } else {
      result.value = null
      answer.value = await searchApi.ask({
        question: query.value.trim(),
        kbIds: selectedKbs.value.length ? selectedKbs.value : null,
        topK: topK.value
      })
    }
  } finally {
    loading.value = false
  }
}

const openDoc = (docId) => {
  currentDocId.value = docId
  docOpen.value = true
}

watch(mode, () => {
  result.value = null
  answer.value = null
})

onMounted(async () => {
  kbs.value = await kbApi.list()
})
</script>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
  min-height: calc(100vh - 150px);
}
.control {
  position: sticky;
  top: 76px;
  align-self: start;
}
.result {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}
.mode {
  margin-bottom: 12px;
  width: 100%;
}
.mode :deep(.el-radio-button) {
  flex: 1;
}
.mode :deep(.el-radio-button__inner) {
  width: 100%;
}
.field {
  margin-top: 14px;
}
.field label {
  font-size: var(--fs-caption);
  color: var(--text-muted);
}
.field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 2px;
}
.field-head b {
  font-size: var(--fs-caption);
  font-weight: var(--fw-strong);
  color: var(--accent-text);
  font-variant-numeric: tabular-nums;
}
.opt-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.opt-dim {
  font-size: var(--fs-caption);
  color: var(--text-faint);
}
.run {
  width: 100%;
  margin-top: 16px;
}
.examples {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 0.5px solid var(--border-subtle);
}
.examples-title {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  margin-bottom: 4px;
}
.example {
  display: block;
  width: 100%;
  text-align: left;
  padding: 6px 8px;
  margin: 0 -8px;
  border: 0;
  background: none;
  border-radius: var(--r-control);
  font-size: var(--fs-caption);
  color: var(--text-muted);
  cursor: pointer;
  transition: background-color var(--dur) var(--ease), color var(--dur) var(--ease);
}
.example:hover {
  background: var(--bg-subtle);
  color: var(--text);
}
.placeholder {
  flex: 1;
  text-align: center;
  padding: 0 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360px;
}
.ph-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--gray-3);
  color: var(--gray-9);
  font-size: 20px;
}
.ph-title {
  font-size: var(--fs-body);
  font-weight: var(--fw-strong);
  margin-top: 12px;
}
.ph-hint {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  margin-top: 2px;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: var(--fs-caption);
  color: var(--text-faint);
  margin-bottom: 10px;
  font-variant-numeric: tabular-nums;
}
.result-head b {
  color: var(--text);
  font-weight: var(--fw-strong);
}
.citations-head {
  margin-top: 16px;
}
.rank {
  width: 18px;
  height: 18px;
  border-radius: var(--r-inner);
  background: var(--accent-bg);
  color: var(--accent-text);
  font-size: var(--fs-caption);
  font-weight: var(--fw-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.doc {
  font-size: var(--fs-body);
  font-weight: var(--fw-strong);
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.score {
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: var(--fs-caption);
  color: var(--accent-text);
  font-variant-numeric: tabular-nums;
}
.answer-card {
  padding: 16px;
}
.answer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.answer-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.took {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
}
@media (max-width: 1080px) {
  .layout {
    grid-template-columns: 1fr;
  }
  .control {
    position: static;
  }
}
</style>
