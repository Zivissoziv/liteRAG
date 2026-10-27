<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">知识库</h1>
      <div class="header-actions">
        <el-button v-if="canWrite" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新建知识库
        </el-button>
      </div>
    </div>

    <div v-if="!loading && !list.length" class="card">
      <div class="empty">
        <div class="empty-icon"><el-icon><Collection /></el-icon></div>
        <div class="empty-title">还没有知识库</div>
        <div>创建后即可上传文档并建立向量索引</div>
        <el-button v-if="canWrite" class="empty-action" type="primary" @click="openCreate">新建知识库</el-button>
      </div>
    </div>

    <div v-else class="card kb-list">
      <div v-for="kb in list" :key="kb.id" class="kb-row" @click="enter(kb)">
        <div class="kb-tile">
          <el-icon><Collection /></el-icon>
        </div>

        <div class="kb-main">
          <div class="kb-title">
            <span class="kb-name">{{ kb.name }}</span>
            <el-tag v-if="kb.visibility === 'RESTRICTED'" size="small" type="warning">受限可见</el-tag>
            <el-tag v-if="kb.mcpExposed" size="small" type="success">MCP 已开放</el-tag>
          </div>
          <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
        </div>

        <div class="metrics">
          <div class="metric">
            <b>{{ kb.docCount || 0 }}</b>
            <span>文档</span>
          </div>
          <div class="metric">
            <b>{{ kb.chunkCount || 0 }}</b>
            <span>片段</span>
          </div>
          <div class="metric wide">
            <b>{{ kb.chunkSize }} / {{ kb.chunkOverlap }}</b>
            <span>分块</span>
          </div>
          <div class="metric model">
            <b>{{ kb.embeddingModelName || '未绑定' }}</b>
            <span>模型</span>
          </div>
        </div>

        <el-dropdown v-if="canWrite" trigger="click" @command="(cmd) => onCommand(cmd, kb)" @click.stop>
          <button class="more" type="button" @click.stop>
            <el-icon><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="edit">编辑设置</el-dropdown-item>
              <el-dropdown-item command="reindex">重新索引</el-dropdown-item>
              <el-dropdown-item command="delete" divided>删除知识库</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-icon class="chev"><ArrowRight /></el-icon>
      </div>

      <button v-if="canWrite" class="add-row" type="button" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>新建知识库</span>
      </button>
    </div>

    <KbDialog v-model="dialogOpen" :kb="editing" @saved="load" />
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { kbApi } from '../api'
import KbDialog from '../components/KbDialog.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const canWrite = computed(() => auth.permissions.includes('content:write'))
const list = ref([])
const loading = ref(false)
const dialogOpen = ref(false)
const editing = ref(null)

const load = async () => {
  loading.value = true
  try {
    list.value = await kbApi.list()
  } finally {
    loading.value = false
  }
}

const enter = (kb) => router.push(`/kb/${kb.id}`)

const openCreate = () => {
  editing.value = null
  dialogOpen.value = true
}

const onCommand = async (cmd, kb) => {
  if (cmd === 'edit') {
    editing.value = kb
    dialogOpen.value = true
  } else if (cmd === 'reindex') {
    await ElMessageBox.confirm(`将重新解析并向量化「${kb.name}」下的全部文档，是否继续？`, '重新索引', {
      type: 'warning',
      confirmButtonText: '开始重建',
      cancelButtonText: '取消'
    })
    const res = await kbApi.reindex(kb.id)
    ElMessage.success(`已提交 ${res.count} 篇文档`)
    load()
  } else if (cmd === 'delete') {
    await ElMessageBox.confirm(`「${kb.name}」下的文档、向量与原文件都会被删除，且不可恢复。`, '删除知识库', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await kbApi.remove(kb.id)
    ElMessage.success('已删除')
    load()
  }
}

onMounted(load)
</script>

<style scoped>
.kb-list {
  padding: 0;
  overflow: hidden;
}

.kb-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  cursor: pointer;
  transition: background-color var(--dur) var(--ease);
}

.kb-row + .kb-row {
  border-top: 0.5px solid var(--border-subtle);
}

.kb-row:hover {
  background: var(--gray-2);
}

.kb-tile {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-control);
  background: var(--accent-bg);
  border: 0.5px solid var(--accent-border);
  color: var(--accent-text);
  font-size: 16px;
}

.kb-main {
  min-width: 0;
  flex: 1;
}

.kb-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.kb-name {
  font-size: var(--fs-body);
  font-weight: var(--fw-strong);
  letter-spacing: -0.005em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-desc {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  line-height: 1.6;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metrics {
  display: flex;
  align-items: center;
  gap: 26px;
  flex-shrink: 0;
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;
  min-width: 36px;
}

.metric.wide {
  min-width: 74px;
}

.metric.model {
  min-width: 0;
  max-width: 168px;
}

.metric b {
  font-size: var(--fs-body);
  font-weight: var(--fw-strong);
  color: var(--text);
  font-variant-numeric: tabular-nums;
  line-height: 1.35;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric span {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  line-height: 1.3;
}

.more {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  padding: 0;
  border: 0;
  background: none;
  border-radius: var(--r-inner);
  color: var(--text-faint);
  cursor: pointer;
  font-size: 15px;
  opacity: 0;
  transition: opacity var(--dur) var(--ease), background-color var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.kb-row:hover .more,
.more:focus-visible {
  opacity: 1;
}

.more:hover {
  background: var(--gray-4);
  color: var(--text);
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

.add-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 46px;
  padding: 0;
  border: 0;
  border-top: 0.5px dashed var(--border);
  background: none;
  color: var(--text-faint);
  font-family: inherit;
  font-size: var(--fs-caption);
  cursor: pointer;
  transition: background-color var(--dur) var(--ease), color var(--dur) var(--ease);
}

.add-row:hover {
  background: var(--gray-2);
  color: var(--accent-text);
}

.add-row .el-icon {
  font-size: 13px;
}

@media (max-width: 1000px) {
  .metric.model {
    display: none;
  }
}
@media (max-width: 820px) {
  .metrics {
    display: none;
  }
}
</style>
