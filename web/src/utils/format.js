export const statusText = (s) =>
  ({
    PENDING: '排队中',
    PARSING: '解析中',
    EMBEDDING: '向量化',
    READY: '已就绪',
    FAILED: '失败'
  }[s] || s || '-')

export const statusType = (s) =>
  ({
    READY: 'success',
    FAILED: 'danger',
    PENDING: 'info',
    PARSING: 'warning',
    EMBEDDING: 'warning'
  }[s] || 'info')

export const formatTime = (value) => {
  if (!value) return '-'
  const s = String(value).replace('T', ' ')
  return s.length > 16 ? s.slice(0, 16) : s
}

export const formatSize = (bytes) => {
  const n = Number(bytes || 0)
  if (n <= 0) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(2)} MB`
}

export const isBusy = (status) => ['PENDING', 'PARSING', 'EMBEDDING'].includes(status)

export const fileIcon = (doc) => {
  if (doc?.sourceType === 'WIKI') return 'Notebook'
  const ext = (doc?.fileExt || '').toLowerCase()
  if (ext === 'pdf') return 'Document'
  if (ext === 'xlsx' || ext === 'xls' || ext === 'csv') return 'Grid'
  if (ext === 'pptx') return 'Picture'
  if (ext === 'md' || ext === 'markdown') return 'Notebook'
  return 'Document'
}
