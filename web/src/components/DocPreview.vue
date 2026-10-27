<template>
  <div class="preview">
    <div v-if="error" class="state">
      <span class="state-text">{{ error }}</span>
      <el-button size="small" @click="$emit('fallback')">查看解析文本</el-button>
    </div>

    <iframe v-else-if="mode === 'pdf'" class="frame" :src="pdfSrc" :title="doc.name" />

    <iframe v-else-if="mode === 'slides'" class="frame" :src="slidesSrc" :title="doc.name" />

    <div v-else-if="mode === 'image'" class="image-wrap">
      <img class="image" :src="rawSrc" :alt="doc.name" />
    </div>

    <div v-else-if="mode === 'sheet'" class="sheet">
      <div v-if="sheets.length > 1" class="sheet-tabs">
        <button
          v-for="name in sheets"
          :key="name"
          type="button"
          class="sheet-tab"
          :class="{ on: name === activeSheet }"
          @click="selectSheet(name)"
        >
          {{ name }}
        </button>
      </div>
      <div v-loading="loading" class="sheet-body" v-html="sheetHtml" />
    </div>

    <div v-else-if="mode === 'markdown'" class="markdown-body" v-html="markdownHtml" />

    <pre v-else-if="mode === 'text'" class="text">{{ doc.content || '内容为空' }}</pre>

    <div v-show="mode === 'docx'" ref="docxEl" v-loading="loading" class="docx-host" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'

const props = defineProps({
  doc: { type: Object, required: true }
})
defineEmits(['fallback'])

const SLIDES = ['pptx', 'pptm', 'ppsx']
const WORDS = ['docx', 'docm', 'dotx']
const SHEETS = ['xlsx', 'xls', 'csv']
const IMAGES = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg']
const PLAIN = ['txt', 'log', 'json', 'xml', 'html', 'yaml', 'yml']

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const loading = ref(false)
const error = ref('')
const sheetHtml = ref('')
const sheetTabs = ref([])
const activeSheet = ref('')
const sheets = computed({
  get: () => sheetTabs.value,
  set: (v) => (sheetTabs.value = v)
})
const docxEl = ref(null)
let workbook = null

const ext = computed(() => (props.doc?.fileExt || '').toLowerCase())
const rawSrc = computed(() => `/api/documents/${props.doc.id}/raw`)
const pdfSrc = computed(() => `${rawSrc.value}#navpanes=0&view=FitH`)
const slidesSrc = computed(() => `/api/documents/${props.doc.id}/slides`)

const mode = computed(() => {
  if (props.doc?.sourceType === 'WIKI') return 'markdown'
  if (!ext.value) return 'text'
  if (ext.value === 'pdf') return 'pdf'
  if (SLIDES.includes(ext.value)) return 'slides'
  if (WORDS.includes(ext.value)) return 'docx'
  if (SHEETS.includes(ext.value)) return 'sheet'
  if (IMAGES.includes(ext.value)) return 'image'
  if (ext.value === 'md' || ext.value === 'markdown') return 'markdown'
  if (PLAIN.includes(ext.value)) return 'text'
  return 'text'
})

const markdownHtml = computed(() => md.render(props.doc?.content || ''))

const renderDocx = async () => {
  const res = await fetch(rawSrc.value)
  if (!res.ok) throw new Error('原始文件不可用')
  const buffer = await res.arrayBuffer()
  const { renderAsync } = await import('docx-preview')
  await nextTick()
  if (!docxEl.value) throw new Error('渲染容器未就绪')
  docxEl.value.innerHTML = ''
  await renderAsync(buffer, docxEl.value, null, {
    className: 'docx',
    inWrapper: true,
    breakPages: true,
    ignoreWidth: false,
    ignoreHeight: false,
    ignoreFonts: false,
    useBase64URL: true,
    renderHeaders: true,
    renderFooters: true,
    renderFootnotes: true,
    experimental: true
  })
}

const renderSheet = async () => {
  const res = await fetch(rawSrc.value)
  if (!res.ok) throw new Error('原始文件不可用')
  const buffer = await res.arrayBuffer()
  const XLSX = await import('xlsx')
  workbook = XLSX.read(new Uint8Array(buffer), { type: 'array', cellDates: true })
  const names = workbook.SheetNames || []
  if (!names.length) throw new Error('表格没有工作表')
  sheetTabs.value = names
  activeSheet.value = names[0]
  paintSheet()
}

const escapeHtml = (text) =>
  String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')

const paintSheet = async () => {
  const XLSX = await import('xlsx')
  const ws = workbook?.Sheets?.[activeSheet.value]
  if (!ws || !ws['!ref']) {
    sheetHtml.value = '<p class="sheet-empty">空工作表</p>'
    return
  }

  const range = XLSX.utils.decode_range(ws['!ref'])
  const lastRow = Math.min(range.e.r, range.s.r + 2000)
  const lastCol = Math.min(range.e.c, range.s.c + 80)

  const merges = new Map()
  const covered = new Set()
  for (const m of ws['!merges'] || []) {
    if (m.s.r < range.s.r || m.s.c < range.s.c || m.s.r > lastRow || m.s.c > lastCol) continue
    const endRow = Math.min(m.e.r, lastRow)
    const endCol = Math.min(m.e.c, lastCol)
    merges.set(`${m.s.r}:${m.s.c}`, { rowspan: endRow - m.s.r + 1, colspan: endCol - m.s.c + 1 })
    for (let r = m.s.r; r <= endRow; r++) {
      for (let c = m.s.c; c <= endCol; c++) {
        if (r !== m.s.r || c !== m.s.c) covered.add(`${r}:${c}`)
      }
    }
  }

  let html = '<table class="grid"><thead><tr><th class="corner"></th>'
  for (let c = range.s.c; c <= lastCol; c++) {
    html += `<th class="col">${XLSX.utils.encode_col(c)}</th>`
  }
  html += '</tr></thead><tbody>'

  for (let r = range.s.r; r <= lastRow; r++) {
    html += `<tr><th class="rowno">${r + 1}</th>`
    for (let c = range.s.c; c <= lastCol; c++) {
      const key = `${r}:${c}`
      if (covered.has(key)) continue
      const cell = ws[XLSX.utils.encode_cell({ r, c })]
      const text = cell ? (cell.w ?? (cell.v == null ? '' : String(cell.v))) : ''
      const span = merges.get(key)
      const attrs = span ? ` rowspan="${span.rowspan}" colspan="${span.colspan}"` : ''
      const cls = cell && typeof cell.v === 'number' ? ' class="num"' : ''
      html += `<td${attrs}${cls}>${escapeHtml(text)}</td>`
    }
    html += '</tr>'
  }

  sheetHtml.value = `${html}</tbody></table>`
}

const selectSheet = (name) => {
  activeSheet.value = name
  paintSheet()
}

const load = async () => {
  error.value = ''
  sheetHtml.value = ''
  sheetTabs.value = []
  activeSheet.value = ''
  workbook = null
  if (mode.value !== 'docx' && mode.value !== 'sheet') return

  loading.value = true
  try {
    if (mode.value === 'docx') await renderDocx()
    else await renderSheet()
  } catch (e) {
    error.value = e?.message || '无法渲染原始文件'
  } finally {
    loading.value = false
  }
}

watch(() => props.doc?.id, load, { immediate: true })

onBeforeUnmount(() => {
  workbook = null
})
</script>

<style scoped>
.preview {
  height: calc(100vh - 232px);
  min-height: 380px;
  display: flex;
  flex-direction: column;
}

.frame {
  flex: 1;
  width: 100%;
  border: 0.5px solid var(--border);
  border-radius: var(--r-control);
  background: var(--gray-2);
}

.image-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
  border: 0.5px solid var(--border);
  border-radius: var(--r-control);
  background: var(--gray-2);
  padding: 12px;
}

.image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.docx-host {
  flex: 1;
  overflow: auto;
  background: var(--gray-2);
  border: 0.5px solid var(--border);
  border-radius: var(--r-control);
  padding: 14px 10px;
}

.docx-host :deep(.docx-wrapper) {
  background: transparent;
  padding: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.docx-host :deep(.docx-wrapper > section.docx) {
  box-shadow: none;
  border: 0.5px solid var(--border);
  margin: 0;
}

.sheet {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 0.5px solid var(--border);
  border-radius: var(--r-control);
  overflow: hidden;
}

.sheet-tabs {
  display: flex;
  gap: 2px;
  padding: 6px 8px;
  border-bottom: 0.5px solid var(--border-subtle);
  background: var(--gray-1);
  overflow-x: auto;
  flex: none;
}

.sheet-tab {
  border: 0;
  background: transparent;
  padding: 2px 8px;
  height: 22px;
  border-radius: var(--r-inner);
  font-size: var(--fs-caption);
  color: var(--text-muted);
  cursor: pointer;
  white-space: nowrap;
  transition: background-color var(--dur) var(--ease), color var(--dur) var(--ease);
}

.sheet-tab:hover {
  background: var(--gray-3);
}

.sheet-tab.on {
  background: var(--accent-bg);
  color: var(--accent-text);
}

.sheet-body {
  flex: 1;
  overflow: auto;
  background: #fff;
}

.sheet-body :deep(.grid) {
  border-collapse: collapse;
  font-size: var(--fs-caption);
  font-variant-numeric: tabular-nums;
  color: var(--text);
}

.sheet-body :deep(.grid th) {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 22px;
  padding: 0 6px;
  background: var(--gray-2);
  border: 0.5px solid var(--gray-5);
  color: var(--text-faint);
  font-weight: 400;
  text-align: center;
}

.sheet-body :deep(.grid th.col) {
  min-width: 64px;
}

.sheet-body :deep(.grid th.corner) {
  left: 0;
  z-index: 3;
  width: 40px;
}

.sheet-body :deep(.grid th.rowno) {
  top: auto;
  left: 0;
  z-index: 1;
  width: 40px;
}

.sheet-body :deep(.grid td) {
  border: 0.5px solid var(--gray-4);
  padding: 4px 8px;
  min-width: 64px;
  max-width: 320px;
  vertical-align: top;
  white-space: pre-wrap;
  word-break: break-word;
}

.sheet-body :deep(.grid td.num) {
  text-align: right;
}

.sheet-empty {
  margin: 0;
  padding: 16px;
  font-size: var(--fs-body);
  color: var(--text-muted);
}

.text {
  flex: 1;
  overflow: auto;
  margin: 0;
  padding: 14px 16px;
  border: 0.5px solid var(--border);
  border-radius: var(--r-control);
  background: var(--gray-1);
  font-family: var(--el-font-family);
  font-size: var(--fs-body);
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-muted);
}

.markdown-body {
  height: calc(100vh - 232px);
  overflow: auto;
  padding-right: 4px;
}

.state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border: 0.5px solid var(--border);
  border-radius: var(--r-control);
  background: var(--gray-1);
}

.state-text {
  font-size: var(--fs-body);
  color: var(--text-muted);
}
</style>
