<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">模型配置</h1>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增模型
        </el-button>
      </div>
    </div>

    <el-tabs v-model="type" @tab-change="load">
      <el-tab-pane label="Embedding 模型" name="EMBEDDING" />
      <el-tab-pane label="对话模型" name="CHAT" />
    </el-tabs>

    <div v-if="!loading && !list.length" class="card">
      <div class="empty">
        <div class="empty-icon"><el-icon><Cpu /></el-icon></div>
        <div class="empty-title">还没有{{ type === 'CHAT' ? '对话' : ' Embedding ' }}模型</div>
        <div>{{ type === 'CHAT' ? '配置后可启用 RAG 问答' : '知识库需要绑定一个向量化模型' }}</div>
        <el-button class="empty-action" type="primary" @click="openCreate">新增模型</el-button>
      </div>
    </div>

    <div v-else class="card model-list">
      <div v-for="m in list" :key="m.id" class="model-row" :class="{ disabled: m.enabled === false }">
        <div class="m-tile">
          <el-icon><Cpu /></el-icon>
        </div>

        <div class="m-main">
          <div class="m-title">
            <span class="m-name">{{ m.name }}</span>
            <el-tag v-if="m.isDefault" size="small" class="tag-accent">默认</el-tag>
            <el-tag v-if="m.enabled === false" size="small">已停用</el-tag>
          </div>

          <div class="m-meta">
            <span class="pair"><em>协议</em>{{ providerLabel(m.provider) }}</span>
            <span class="pair"><em>模型</em><b class="mono">{{ m.modelName || '-' }}</b></span>
            <span class="pair">
              <em>{{ type === 'CHAT' ? '超时' : '维度' }}</em>
              <b>{{ type === 'CHAT' ? (m.timeout || 60) + ' s' : m.dimension ? m.dimension : '未探测' }}</b>
            </span>
            <span v-if="type === 'EMBEDDING'" class="pair"><em>引用</em><b>{{ m.usedByKbCount || 0 }} 个知识库</b></span>
            <span v-if="m.baseUrl" class="pair url"><em>地址</em><b class="mono">{{ m.baseUrl }}</b></span>
          </div>

          <div v-if="m.remark" class="m-remark">{{ m.remark }}</div>

          <el-alert
            v-if="probe[m.id]"
            :type="probe[m.id].ok ? 'success' : 'error'"
            :closable="false"
            class="m-probe"
            :title="probe[m.id].text"
          />
        </div>

        <div class="m-actions">
          <el-button size="small" @click="test(m)" :loading="testingId === m.id">测试连接</el-button>
          <el-button size="small" text @click="openEdit(m)">编辑</el-button>
          <el-dropdown trigger="click" @command="(cmd) => onCommand(cmd, m)">
            <button class="more" type="button">
              <el-icon><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="!m.isDefault" command="default">设为默认</el-dropdown-item>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <button class="add-row" type="button" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>新增{{ type === 'CHAT' ? '对话' : ' Embedding ' }}模型</span>
      </button>
    </div>

    <ModelDialog v-model="dialogOpen" :model="editing" :default-type="type" @saved="load" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelApi } from '../api'
import ModelDialog from '../components/ModelDialog.vue'

const type = ref('EMBEDDING')
const list = ref([])
const loading = ref(false)
const dialogOpen = ref(false)
const editing = ref(null)
const testingId = ref(null)
const probe = reactive({})

const providerLabel = (p) => ({ openai: 'OpenAI 兼容', ollama: 'Ollama', local: '本地哈希' }[p] || p)

const load = async () => {
  loading.value = true
  try {
    list.value = await modelApi.list(type.value)
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editing.value = null
  dialogOpen.value = true
}

const openEdit = (m) => {
  editing.value = m
  dialogOpen.value = true
}

const test = async (m) => {
  testingId.value = m.id
  delete probe[m.id]
  try {
    const payload = {
      id: m.id,
      provider: m.provider,
      baseUrl: m.baseUrl,
      modelName: m.modelName,
      batchSize: m.batchSize,
      timeout: m.timeout,
      extraParams: m.extraParams
    }
    const res = m.type === 'CHAT' ? await modelApi.testChat(payload) : await modelApi.test(payload)
    probe[m.id] = {
      ok: true,
      text:
        m.type === 'CHAT'
          ? `连接正常，${res.costMs} ms，回复：${res.answer}`
          : `连接正常，维度 ${res.dimension}，${res.costMs} ms`
    }
    if (m.type === 'EMBEDDING' && m.dimension !== res.dimension) {
      load()
    }
  } catch (e) {
    probe[m.id] = { ok: false, text: e.message || '连接失败' }
  } finally {
    testingId.value = null
  }
}

const onCommand = async (cmd, m) => {
  if (cmd === 'edit') return openEdit(m)
  if (cmd === 'default') {
    await modelApi.setDefault(m.id)
    ElMessage.success('已设为默认模型')
    return load()
  }
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`确认删除模型配置「${m.name}」？`, '删除模型', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await modelApi.remove(m.id)
    ElMessage.success('已删除')
    load()
  }
}

onMounted(load)
</script>

<style scoped>
.model-list {
  padding: 0;
  overflow: hidden;
}

.model-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 15px 16px;
  transition: background-color var(--dur) var(--ease);
}

.model-row + .model-row {
  border-top: 0.5px solid var(--border-subtle);
}

.model-row:hover {
  background: var(--gray-2);
}

.model-row.disabled .m-tile {
  background: var(--gray-3);
  border-color: var(--border);
  color: var(--gray-9);
}

.model-row.disabled .m-name {
  color: var(--text-muted);
}

.m-tile {
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

.m-main {
  min-width: 0;
  flex: 1;
  padding-top: 1px;
}

.m-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.m-name {
  font-size: var(--fs-body);
  font-weight: var(--fw-strong);
  letter-spacing: -0.005em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 参数按「标签 值」横向排布，用发丝线分隔，避免堆成多行 */
.m-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0 14px;
  margin-top: 5px;
  font-size: var(--fs-caption);
  color: var(--text-muted);
  line-height: 1.7;
}

.m-meta .pair {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.m-meta .pair + .pair {
  padding-left: 14px;
  border-left: 0.5px solid var(--border-subtle);
}

.m-meta em {
  font-style: normal;
  color: var(--text-faint);
  flex-shrink: 0;
}

.m-meta b {
  font-weight: normal;
  color: var(--text);
  font-variant-numeric: tabular-nums;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-meta .url b {
  max-width: 260px;
}

.m-remark {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  line-height: 1.6;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-probe {
  margin-top: 8px;
}

.m-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  padding-top: 1px;
}

.more {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 0;
  background: none;
  border-radius: var(--r-inner);
  color: var(--text-faint);
  cursor: pointer;
  font-size: 15px;
  transition: background-color var(--dur) var(--ease), color var(--dur) var(--ease);
}

.more:hover {
  background: var(--gray-4);
  color: var(--text);
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
  .m-meta .url {
    display: none;
  }
}

@media (max-width: 860px) {
  .m-actions :deep(.el-button) {
    display: none;
  }
}
</style>
