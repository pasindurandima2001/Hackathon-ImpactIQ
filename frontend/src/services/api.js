import axios from 'axios'

export const UNAUTHORIZED_EVENT = 'impactiq:unauthorized'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
  timeout: 20000,
  // Spring Security double-submit CSRF: the XSRF-TOKEN cookie is echoed in this header on state-changing requests.
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withCredentials: true,
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const url = error?.config?.url || ''
    if (error?.response?.status === 401 && !url.startsWith('/auth/')) {
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT))
    }
    return Promise.reject(error)
  },
)

const data = ({ data: body }) => body

// Content-Type must be multipart so axios does not serialise FormData as JSON; the browser adds the boundary.
const multipart = { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 }

function formData(fields) {
  const form = new FormData()
  Object.entries(fields).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') form.append(key, value)
  })
  return form
}

export const authApi = {
  csrf: () => api.get('/auth/csrf').then(data),
  me: () => api.get('/auth/me').then(data),
  login: async (payload) => {
    const user = await api.post('/auth/login', payload).then(data)
    await api.get('/auth/csrf') // the session and CSRF token are rotated on login
    return user
  },
  register: (payload) => api.post('/auth/register', payload).then(data),
  logout: () => api.post('/auth/logout'),
}

export const defectApi = {
  getDefects: () => api.get('/defects').then(data),
  getDefect: (id) => api.get(`/defects/${id}`).then(data),
  createDefect: (payload) => api.post('/defects', payload).then(data),
  updateDefect: (id, payload) => api.put(`/defects/${id}`, payload).then(data),
  deleteDefect: (id) => api.delete(`/defects/${id}`),
  analyzeDefect: (id) => api.post(`/defects/${id}/analyze`, null, { timeout: 60000 }).then(data),
  reanalyzeDefect: (id) => api.post(`/defects/${id}/reanalyze`, null, { timeout: 60000 }).then(data),
  getLatestAnalysis: (id) => api.get(`/defects/${id}/analysis`).then(data),
  getAnalysisHistory: (id) => api.get(`/defects/${id}/analysis/history`).then(data),
}

export const draftApi = {
  draft: (payload) => api.post('/defects/draft', payload, { timeout: 90000 }).then(data),
  draftFromAzureDevOps: (payload) => api.post('/defects/draft/azure-devops', payload, { timeout: 120000 }).then(data),
  draftFromFile: ({ file, note, projectId, componentId }) =>
    api.post('/defects/draft/upload', formData({ file, note, projectId, componentId }), multipart).then(data),
}

export const evidenceApi = {
  list: (defectId) => api.get(`/defects/${defectId}/evidence`).then(data),
  add: (defectId, payload) => api.post(`/defects/${defectId}/evidence`, payload).then(data),
  upload: (defectId, { file, type, label }) =>
    api.post(`/defects/${defectId}/evidence/upload`, formData({ file, type, label }), multipart).then(data),
  remove: (defectId, evidenceId) => api.delete(`/defects/${defectId}/evidence/${evidenceId}`),
}

export const investigationApi = {
  list: (defectId) => api.get(`/defects/${defectId}/investigations`).then(data),
  get: (defectId, investigationId) => api.get(`/defects/${defectId}/investigations/${investigationId}`).then(data),
  start: (defectId) => api.post(`/defects/${defectId}/investigations`).then(data),
  retry: (defectId, investigationId) => api.post(`/defects/${defectId}/investigations/${investigationId}/retry`).then(data),
  feedback: (defectId, investigationId, payload) =>
    api.post(`/defects/${defectId}/investigations/${investigationId}/feedback`, payload).then(data),
}

export const projectApi = {
  list: () => api.get('/projects').then(data),
  get: (projectId) => api.get(`/projects/${projectId}`).then(data),
  create: (payload) => api.post('/projects', payload).then(data),
  update: (projectId, payload) => api.put(`/projects/${projectId}`, payload).then(data),
  remove: (projectId) => api.delete(`/projects/${projectId}`),
  addComponent: (projectId, payload) => api.post(`/projects/${projectId}/components`, payload).then(data),
  discoverComponents: (projectId) => api.post(`/projects/${projectId}/components/discover`, null, { timeout: 90000 }).then(data),
  deleteComponent: (projectId, componentId) => api.delete(`/projects/${projectId}/components/${componentId}`),
  members: (projectId) => api.get(`/projects/${projectId}/members`).then(data),
  addMember: (projectId, payload) => api.post(`/projects/${projectId}/members`, payload).then(data),
  updateMember: (projectId, userId, role) => api.put(`/projects/${projectId}/members/${userId}`, { role }).then(data),
  removeMember: (projectId, userId) => api.delete(`/projects/${projectId}/members/${userId}`),
  audit: (projectId) => api.get(`/projects/${projectId}/audit`).then(data),
}

export const userApi = {
  list: () => api.get('/users').then(data),
}

export const knowledgeApi = {
  list: (projectId, componentId) =>
    api.get(`/projects/${projectId}/documents`, { params: componentId ? { componentId } : {} }).then(data),
  get: (projectId, documentId) => api.get(`/projects/${projectId}/documents/${documentId}`).then(data),
  upload: (projectId, { file, componentId, title }) =>
    api.post(`/projects/${projectId}/documents`, formData({ file, componentId, title }), multipart).then(data),
  uploadVersion: (projectId, documentId, file) =>
    api.post(`/projects/${projectId}/documents/${documentId}/versions`, formData({ file }), multipart).then(data),
  reprocess: (projectId, documentId) => api.post(`/projects/${projectId}/documents/${documentId}/reprocess`).then(data),
  remove: (projectId, documentId) => api.delete(`/projects/${projectId}/documents/${documentId}`),
  search: (projectId, payload) => api.post(`/projects/${projectId}/search`, payload, { timeout: 30000 }).then(data),
}

// Replies are synchronous; the server bounds a reply at impactiq.chat.timeout-ms (90 s by default).
const CHAT_REPLY_TIMEOUT_MS = 100000

export const chatApi = {
  list: (projectId) => api.get('/chat/conversations', { params: projectId ? { projectId } : {} }).then(data),
  get: (conversationId) => api.get(`/chat/conversations/${conversationId}`).then(data),
  create: (payload) => api.post('/chat/conversations', payload).then(data),
  update: (conversationId, payload) => api.patch(`/chat/conversations/${conversationId}`, payload).then(data),
  remove: (conversationId) => api.delete(`/chat/conversations/${conversationId}`),
  send: (conversationId, content) =>
    api.post(`/chat/conversations/${conversationId}/messages`, { content }, { timeout: CHAT_REPLY_TIMEOUT_MS }).then(data),
  retry: (conversationId, messageId) =>
    api.post(`/chat/conversations/${conversationId}/messages/${messageId}/retry`, null, { timeout: CHAT_REPLY_TIMEOUT_MS }).then(data),
  suggestions: ({ projectId, componentId, defectId }) =>
    api.get('/chat/suggestions', { params: { projectId, componentId: componentId || undefined, defectId: defectId || undefined } }).then(data),
}

/** Seconds from a Retry-After header, when the server sent one. */
export function getRetryAfterSeconds(error) {
  const value = Number(error?.response?.headers?.['retry-after'])
  return Number.isFinite(value) && value > 0 ? value : null
}

export const dashboardApi = {
  getMetrics: async () => {
    for (let attempt = 0; ; attempt += 1) {
      try {
        const { data: body } = await api.get('/dashboard/metrics')
        return body
      } catch (error) {
        if (error.response || attempt === 3) throw error
        await new Promise((resolve) => setTimeout(resolve, 500 * (2 ** attempt)))
      }
    }
  },
}

export function getApiErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  const body = error?.response?.data
  const fieldMessages = body?.validationErrors ? Object.values(body.validationErrors) : []
  if (fieldMessages.length) return fieldMessages.join(' ')
  return body?.message || error?.message || fallback
}

export const ALLOWED_UPLOAD_TYPES = '.txt,.log,.md,.json,.pdf,.java,.kt,.js,.jsx,.ts,.tsx,.py,.go,.cs,.sql,.xml,.yaml,.yml,.properties,.html,.css,.sh,.csv'
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

export function formatDate(value, withTime = true) {
  if (!value) return '—'
  const options = withTime ? { dateStyle: 'medium', timeStyle: 'short' } : { dateStyle: 'medium' }
  return new Intl.DateTimeFormat('en', options).format(new Date(value))
}

export default api
