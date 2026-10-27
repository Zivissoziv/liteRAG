import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 300000, withCredentials: true })

/** 未登录 / 登录过期时通知外层（App.vue 监听后跳转登录页） */
export const notifyAuthExpired = () => {
  window.dispatchEvent(new CustomEvent('auth:expired'))
}

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      // 登录等场景自行展示错误，避免与页面内联提示重复
      if (!res.config?.skipErrorToast) {
        ElMessage.error(body.message || '请求失败')
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (err) => {
    const status = err.response?.status
    if (status === 401) {
      // auth/me 是启动时的登录态探测，未登录属正常情况，不弹过期提示
      if (!err.config?.skipAuthExpired) {
        notifyAuthExpired()
      }
      return Promise.reject(err)
    }
    const msg = err.response?.data?.message || err.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export const authApi = {
  login: (data) => http.post('/auth/login', data, { skipErrorToast: true }),
  logout: () => http.post('/auth/logout'),
  me: () => http.get('/auth/me', { skipAuthExpired: true }),
  changePassword: (data) => http.put('/auth/password', data)
}

export const userApi = {
  list: () => http.get('/users'),
  roles: () => http.get('/users/roles'),
  create: (data) => http.post('/users', data),
  update: (id, data) => http.put(`/users/${id}`, data),
  resetPassword: (id, newPassword) => http.put(`/users/${id}/password`, { newPassword }),
  remove: (id) => http.delete(`/users/${id}`)
}

export const kbApi = {
  list: () => http.get('/kb'),
  create: (data) => http.post('/kb', data),
  detail: (id) => http.get(`/kb/${id}`),
  update: (id, data) => http.put(`/kb/${id}`, data),
  remove: (id) => http.delete(`/kb/${id}`),
  accessOptions: () => http.get('/kb/access-options'),
  documents: (id, params) => http.get(`/kb/${id}/documents`, { params }),
  reindex: (id) => http.post(`/kb/${id}/reindex`),
  upload: (id, form, onProgress) =>
    http.post(`/kb/${id}/documents/upload`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: onProgress
    }),
  createWiki: (id, data) => http.post(`/kb/${id}/wiki`, data)
}

export const folderApi = {
  tree: (kbId) => http.get(`/kb/${kbId}/folders`),
  create: (kbId, data) => http.post(`/kb/${kbId}/folders`, data),
  update: (id, data) => http.put(`/folders/${id}`, data),
  remove: (id) => http.delete(`/folders/${id}`)
}

export const docApi = {
  detail: (id, content = true) => http.get(`/documents/${id}`, { params: { content } }),
  remove: (id) => http.delete(`/documents/${id}`),
  reindex: (id) => http.post(`/documents/${id}/reindex`),
  move: (id, folderId) => http.post(`/documents/${id}/move`, { folderId }),
  chunks: (id, params) => http.get(`/documents/${id}/chunks`, { params }),
  updateWiki: (id, data) => http.put(`/documents/${id}/wiki`, data),
  downloadUrl: (id) => `/api/documents/${id}/download`
}

export const searchApi = {
  search: (data) => http.post('/search', data),
  ask: (data) => http.post('/chat/ask', data)
}

export const modelApi = {
  list: (type) => http.get('/models', { params: { type } }),
  create: (data) => http.post('/models', data),
  update: (id, data) => http.put(`/models/${id}`, data),
  remove: (id) => http.delete(`/models/${id}`),
  setDefault: (id) => http.post(`/models/${id}/default`),
  test: (data) => http.post('/models/test', data),
  testChat: (data) => http.post('/models/test-chat', data),
  presets: () => http.get('/models/presets')
}

export const systemApi = {
  settings: () => http.get('/settings'),
  saveSettings: (data) => http.put('/settings', data),
  status: () => http.get('/system/status'),
  overview: () => http.get('/stats/overview'),
  mcpSessions: () => http.get('/mcp/sessions')
}

export default http
