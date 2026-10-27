<template>
  <div class="page">
    <div class="crumbs">
      <button class="crumb-back" type="button" @click="router.push('/kb')">
        <el-icon><ArrowLeft /></el-icon>
        <span>知识库</span>
      </button>
      <span class="crumb-sep">/</span>
      <span class="crumb-current">{{ kb?.name || '加载中' }}</span>
    </div>

    <div class="page-header">
      <div class="title-block">
        <h1 class="page-title">{{ kb?.name }}</h1>
      </div>
      <div class="header-actions">
        <template v-if="canWrite">
          <el-button @click="wikiOpen = true">
            <el-icon><EditPen /></el-icon>新建 Wiki
          </el-button>
          <el-button @click="editOpen = true">设置</el-button>
          <el-button type="primary" @click="uploadOpen = true">
            <el-icon><Upload /></el-icon>上传文档
          </el-button>
        </template>
      </div>
    </div>

    <div class="kb-brief">
      <p class="kb-desc">{{ kb?.description || '暂无描述' }}</p>
      <div class="chips">
        <el-tag size="small">{{ kb?.embeddingModelName || '未绑定模型' }}</el-tag>
        <el-tag size="small">分块 {{ kb?.chunkSize }} / {{ kb?.chunkOverlap }}</el-tag>
        <el-tag size="small">{{ kb?.docCount || 0 }} 文档</el-tag>
        <el-tag size="small">{{ kb?.chunkCount || 0 }} 片段</el-tag>
        <el-tag v-if="kb?.mcpExposed" size="small" type="success">MCP 已开放</el-tag>
        <el-tag v-if="kb?.visibility === 'RESTRICTED'" size="small" type="warning">仅授权可见</el-tag>
      </div>
    </div>

    <div class="kb-body">
      <aside class="folder-panel card">
        <div class="panel-head">
          <span class="panel-title">目录</span>
          <button v-if="canWrite" class="icon-btn" type="button" title="新建目录" @click="openCreate(null)">
            <el-icon><Plus /></el-icon>
          </button>
        </div>

        <div class="panel-body">
          <div
            class="quick-row"
            :class="{ active: filters.folderId === null }"
            @click="selectFolder(null)"
          >
            <el-icon class="quick-icon"><Files /></el-icon>
            <span class="quick-name">全部文档</span>
            <span class="quick-count">{{ kb?.docCount || 0 }}</span>
          </div>
          <div
            class="quick-row"
            :class="{ active: filters.folderId === 0 }"
            @click="selectFolder(0)"
          >
            <el-icon class="quick-icon"><FolderOpened /></el-icon>
            <span class="quick-name">未分类</span>
            <span class="quick-count">{{ rootCount }}</span>
          </div>

          <div class="panel-sep"></div>

          <FolderTree
            v-if="folders.length"
            :nodes="folders"
            :current="filters.folderId"
            :readonly="!canWrite"
            @select="(node) => selectFolder(node.id)"
            @menu="onFolderMenu"
          />
          <p v-else class="panel-tip">还没有目录</p>
        </div>
      </aside>

      <div class="card doc-panel">
        <div class="toolbar">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索名称、标题或标签"
            clearable
            style="width: 240px"
            @keyup.enter="reload"
            @clear="reload"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 120px" @change="reload">
            <el-option value="READY" label="已就绪" />
            <el-option value="PENDING" label="排队中" />
            <el-option value="PARSING" label="解析中" />
            <el-option value="EMBEDDING" label="向量化" />
            <el-option value="FAILED" label="失败" />
          </el-select>
          <el-button :loading="loading" @click="reload">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
          <div class="spacer"></div>
          <span class="scope">{{ scopeLabel }}</span>
          <el-dropdown v-if="canWrite" trigger="click" @command="onBulkCommand">
            <el-button text>更多<el-icon class="caret"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="reindex">重新索引全部</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <el-table
          v-loading="loading"
          :data="docs"
          size="small"
          :empty-text="emptyText"
          @row-click="openDoc"
        >
          <el-table-column label="文档" min-width="280">
            <template #default="{ row }">
              <div class="doc-cell">
                <FileBadge :doc="row" />
                <div class="doc-main">
                  <div class="doc-name">{{ row.name }}</div>
                  <div class="doc-sub">
                    <span v-if="row.folderPath" class="cat">
                      <el-icon class="path-icon"><Folder /></el-icon>{{ row.folderPath }}
                    </span>
                    <span v-if="row.tags" class="tags">{{ row.tags }}</span>
                    <span class="size">{{ formatSize(row.fileSize) }}</span>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tooltip :disabled="row.status !== 'FAILED'" :content="row.errorMsg || ''" placement="top">
                <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="片段" width="60" align="right">
            <template #default="{ row }">{{ row.chunkCount || 0 }}</template>
          </el-table-column>
          <el-table-column v-if="wide" label="字数" width="68" align="right">
            <template #default="{ row }">{{ row.charCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="136">
            <template #default="{ row }">
              <span class="time">{{ formatTime(row.updatedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="56" align="right">
            <template #default="{ row }">
              <el-dropdown trigger="click" @command="(cmd) => onDocCommand(cmd, row)" @click.stop>
                <el-button size="small" text class="row-more" @click.stop>
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="preview">预览</el-dropdown-item>
                    <template v-if="canWrite">
                      <el-dropdown-item command="move">移动到目录</el-dropdown-item>
                      <el-dropdown-item command="reindex">重新索引</el-dropdown-item>
                      <el-dropdown-item v-if="row.sourceType === 'WIKI'" command="edit">编辑内容</el-dropdown-item>
                    </template>
                    <el-dropdown-item v-if="row.sourceType !== 'WIKI'" command="download">下载原文件</el-dropdown-item>
                    <el-dropdown-item v-if="canWrite" command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            layout="total, sizes, prev, pager, next"
            :total="total"
            :page-size="filters.size"
            :current-page="filters.page"
            :page-sizes="[10, 20, 50]"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </div>
    </div>

    <UploadDialog
      v-model="uploadOpen"
      :kb-id="kbId"
      :folder-id="currentFolder"
      @uploaded="refreshAll"
    />
    <WikiDialog v-model="wikiOpen" :kb-id="kbId" :folder-id="currentFolder" @saved="refreshAll" />
    <WikiDialog v-model="wikiEditOpen" :kb-id="kbId" :doc="editingWiki" @saved="refreshAll" />
    <KbDialog v-model="editOpen" :kb="kb" @saved="loadKb" />
    <DocDrawer v-model="docOpen" :doc-id="currentDocId" @changed="refreshAll" />

    <FolderDialog
      v-model="folderDialogOpen"
      :kb-id="kbId"
      :parent="folderParent"
      :folder="folderEditing"
      @saved="refreshAll"
    />

    <el-dialog v-model="moveOpen" title="移动到目录" width="420px" align-center>
      <p class="move-name">{{ movingDoc?.name }}</p>
      <FolderSelect v-if="moveOpen" v-model="moveTarget" :kb-id="kbId" />
      <template #footer>
        <el-button @click="moveOpen = false">取消</el-button>
        <el-button type="primary" :loading="moving" @click="confirmMove">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { docApi, folderApi, kbApi } from '../api'
import { formatSize, formatTime, isBusy, statusText, statusType } from '../utils/format'
import FileBadge from '../components/FileBadge.vue'
import UploadDialog from '../components/UploadDialog.vue'
import WikiDialog from '../components/WikiDialog.vue'
import KbDialog from '../components/KbDialog.vue'
import DocDrawer from '../components/DocDrawer.vue'
import FolderTree from '../components/FolderTree.vue'
import FolderDialog from '../components/FolderDialog.vue'
import FolderSelect from '../components/FolderSelect.vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const canWrite = computed(() => auth.permissions.includes('content:write'))
const kbId = route.params.id

const kb = ref(null)
const docs = ref([])
const folders = ref([])
const total = ref(0)
const loading = ref(false)
const uploadOpen = ref(false)
const wikiOpen = ref(false)
const wikiEditOpen = ref(false)
const editOpen = ref(false)
const editingWiki = ref(null)
const docOpen = ref(false)
const currentDocId = ref(null)
const wide = ref(window.innerWidth >= 1200)
const onResize = () => (wide.value = window.innerWidth >= 1200)
let timer = null

const folderDialogOpen = ref(false)
const folderParent = ref(null)
const folderEditing = ref(null)
const moveOpen = ref(false)
const moving = ref(false)
const movingDoc = ref(null)
const moveTarget = ref(0)

const filters = reactive({ keyword: '', status: '', folderId: null, page: 1, size: 20 })

const currentFolder = computed(() => filters.folderId ?? 0)
const rootCount = computed(() => {
  const inFolders = folders.value.reduce((sum, f) => sum + (f.totalCount || 0), 0)
  return Math.max(0, (kb.value?.docCount || 0) - inFolders)
})
const scopeLabel = computed(() => {
  if (filters.folderId === null) return ''
  if (filters.folderId === 0) return '未分类'
  return findPath(folders.value, filters.folderId) || ''
})
const emptyText = computed(() =>
  filters.folderId === null ? '暂无文档' : '该目录下暂无文档'
)

const findPath = (nodes, id, parent = '') => {
  for (const node of nodes) {
    const path = parent ? `${parent} / ${node.name}` : node.name
    if (node.id === id) return path
    const hit = node.children?.length ? findPath(node.children, id, path) : null
    if (hit) return hit
  }
  return null
}

const loadKb = async () => {
  try {
    kb.value = await kbApi.detail(kbId)
  } catch (e) {
    // 权限被收回或知识库已删除时退回列表，避免停在空壳详情页
    kb.value = null
    router.replace('/kb')
  }
}

const loadFolders = async () => {
  try {
    folders.value = await folderApi.tree(kbId)
  } catch (e) {
    folders.value = []
  }
}

const reload = async () => {
  loading.value = true
  try {
    const res = await kbApi.documents(kbId, {
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      folderId: filters.folderId === null ? undefined : filters.folderId,
      page: filters.page,
      size: filters.size
    })
    docs.value = res.list
    total.value = res.total
    if (docs.value.some((d) => isBusy(d.status))) {
      schedulePoll()
    }
  } finally {
    loading.value = false
  }
}

const refreshAll = async () => {
  await Promise.all([reload(), loadFolders()])
  await loadKb()
}

const schedulePoll = () => {
  if (timer) return
  timer = setTimeout(async () => {
    timer = null
    await reload()
    await loadKb()
  }, 2000)
}

const selectFolder = (id) => {
  if (filters.folderId === id) return
  router.replace({ query: { ...route.query, folder: id === null ? undefined : String(id) } })
}

const applyQueryFolder = () => {
  const raw = route.query.folder
  filters.folderId = raw === undefined || raw === null || raw === '' ? null : raw === '0' ? 0 : Number(raw)
}

const onPageChange = (page) => {
  filters.page = page
  reload()
}

const onSizeChange = (size) => {
  filters.size = size
  filters.page = 1
  reload()
}

const openDoc = (row) => {
  currentDocId.value = row.id
  docOpen.value = true
}

const editWiki = async (row) => {
  const detail = await docApi.detail(row.id, true)
  editingWiki.value = detail
  wikiEditOpen.value = true
}

const reindex = async (row) => {
  await docApi.reindex(row.id)
  ElMessage.success('已提交重新索引')
  reload()
}

// ------------------------------------------------------------------ 目录操作

const openCreate = (parent) => {
  folderEditing.value = null
  folderParent.value = parent
  folderDialogOpen.value = true
}

const onFolderMenu = async (cmd, node) => {
  if (cmd === 'create') {
    openCreate(node)
    return
  }
  if (cmd === 'rename') {
    folderParent.value = null
    folderEditing.value = node
    folderDialogOpen.value = true
    return
  }
  if (cmd === 'delete') {
    await ElMessageBox.confirm(
      `删除目录「${node.name}」及其子目录？其中的文档会移到上一级目录，不会被删除。`,
      '删除目录',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    const res = await folderApi.remove(node.id)
    ElMessage.success(res?.movedDocs ? `已删除目录，${res.movedDocs} 篇文档已上移` : '已删除目录')
    await loadFolders()
    await loadKb()
    const selected = filters.folderId
    if (selected === null || selected === 0 || findPath(folders.value, selected)) {
      reload()
    } else {
      selectFolder(null)
    }
  }
}

const onDocCommand = async (cmd, row) => {
  if (cmd === 'preview') {
    openDoc(row)
  } else if (cmd === 'move') {
    movingDoc.value = row
    moveTarget.value = row.folderId ?? 0
    moveOpen.value = true
  } else if (cmd === 'reindex') {
    await reindex(row)
  } else if (cmd === 'edit') {
    editWiki(row)
  } else if (cmd === 'download') {
    window.open(docApi.downloadUrl(row.id), '_blank')
  } else if (cmd === 'delete') {
    await ElMessageBox.confirm(`确认删除「${row.name}」及其全部向量片段？`, '删除文档', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await docApi.remove(row.id)
    ElMessage.success('已删除')
    await refreshAll()
  }
}

const confirmMove = async () => {
  if (!movingDoc.value) return
  moving.value = true
  try {
    await docApi.move(movingDoc.value.id, moveTarget.value || 0)
    ElMessage.success('已移动')
    moveOpen.value = false
    await refreshAll()
  } finally {
    moving.value = false
  }
}

const onBulkCommand = async (cmd) => {
  if (cmd !== 'reindex') return
  await ElMessageBox.confirm('将重新解析并向量化该知识库下全部文档，是否继续？', '重新索引', {
    type: 'warning',
    confirmButtonText: '开始',
    cancelButtonText: '取消'
  })
  const res = await kbApi.reindex(kbId)
  ElMessage.success(`已提交 ${res.count} 篇文档`)
  reload()
}

watch(
  () => route.query.folder,
  () => {
    applyQueryFolder()
    filters.page = 1
    reload()
  }
)

watch(
  () => route.params.id,
  () => {
    if (route.name === 'kb-detail') {
      applyQueryFolder()
      loadKb()
      loadFolders()
      reload()
    }
  }
)

onMounted(async () => {
  window.addEventListener('resize', onResize)
  applyQueryFolder()
  await loadKb()
  await loadFolders()
  await reload()
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  timer && clearTimeout(timer)
})
</script>

<style scoped>
.crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-caption);
  color: var(--text-faint);
  margin-bottom: 10px;
}
.crumb-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text-muted);
  font: inherit;
  cursor: pointer;
  transition: color var(--dur) var(--ease);
}
.crumb-back:hover {
  color: var(--text);
}
.crumb-back .el-icon {
  font-size: 13px;
}
.crumb-sep {
  color: var(--gray-8);
}
.crumb-current {
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.title-block {
  min-width: 0;
}
.kb-brief {
  margin: -6px 0 16px;
}
.kb-desc {
  font-size: var(--fs-caption);
  color: var(--text-muted);
  margin: 0;
  line-height: 1.65;
}
.chips {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
}

/* 目录侧栏 + 文档列表 */
.kb-body {
  display: grid;
  grid-template-columns: 212px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}
.folder-panel {
  position: sticky;
  top: 72px;
  padding: 10px 8px 12px;
  max-height: calc(100vh - 96px);
  display: flex;
  flex-direction: column;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 6px;
}
.panel-title {
  font-size: var(--fs-caption);
  color: var(--text-faint);
}
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: var(--r-inner);
  background: none;
  color: var(--icon-quiet);
  cursor: pointer;
  transition: background-color var(--dur) var(--ease), color var(--dur) var(--ease);
}
.icon-btn .el-icon {
  font-size: 14px;
}
.icon-btn:hover {
  background: var(--bg-hover);
  color: var(--text);
}
.panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.quick-row {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 6px;
  border-radius: var(--r-control);
  color: var(--text-muted);
  cursor: pointer;
  transition: background-color var(--dur) var(--ease), color var(--dur) var(--ease);
}
.quick-row:hover {
  background: var(--bg-hover);
  color: var(--text);
}
.quick-row.active {
  background: var(--accent-bg);
  color: var(--accent-text);
}
.quick-icon {
  font-size: 14px;
  color: var(--icon-quiet);
}
.quick-row.active .quick-icon {
  color: var(--accent-text);
}
.quick-name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.quick-count {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
}
.quick-row.active .quick-count {
  color: var(--accent-text);
}
.panel-sep {
  height: 0.5px;
  background: var(--border-subtle);
  margin: 8px 2px;
}
.panel-tip {
  margin: 6px;
  font-size: var(--fs-caption);
  color: var(--text-faint);
}

.doc-panel {
  min-width: 0;
}
.scope {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-cell {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}
:deep(.el-table__row) {
  cursor: pointer;
}
.doc-main {
  min-width: 0;
}
.doc-name {
  font-size: var(--fs-body);
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-sub {
  display: flex;
  gap: 8px;
  margin-top: 2px;
  font-size: var(--fs-caption);
  color: var(--text-faint);
  min-width: 0;
}
.doc-sub .tags {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-sub .size {
  flex: 0 0 auto;
  white-space: nowrap;
}
.cat {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--text-muted);
  flex-shrink: 0;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.path-icon {
  font-size: 11px;
  opacity: 0.75;
}
.time {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.caret {
  font-size: 12px;
  margin-left: 2px;
}
.row-more {
  padding: 0 6px;
  height: 24px;
}
.row-more :deep(.el-icon) {
  font-size: 14px;
  color: var(--icon-quiet);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.move-name {
  margin: 0 0 10px;
  font-size: var(--fs-body);
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1080px) {
  .kb-body {
    grid-template-columns: 1fr;
  }
  .folder-panel {
    position: static;
    max-height: 220px;
  }
}
</style>
