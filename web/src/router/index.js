import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/', redirect: '/dashboard' },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', public: true, bare: true }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { title: '概览', icon: 'DataLine' }
  },
  {
    path: '/kb',
    name: 'kb',
    component: () => import('../views/KnowledgeBases.vue'),
    meta: { title: '知识库', icon: 'Collection' }
  },
  {
    path: '/kb/:id',
    name: 'kb-detail',
    component: () => import('../views/KnowledgeBaseDetail.vue'),
    meta: { title: '知识库详情', hidden: true }
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('../views/SearchPlayground.vue'),
    meta: { title: '检索问答', icon: 'Search' }
  },
  {
    path: '/models',
    name: 'models',
    component: () => import('../views/Models.vue'),
    meta: { title: '模型配置', icon: 'Cpu', perm: 'config:manage' }
  },
  {
    path: '/mcp',
    name: 'mcp',
    component: () => import('../views/McpGuide.vue'),
    meta: { title: 'MCP 接入', icon: 'Connection' }
  },
  {
    path: '/users',
    name: 'users',
    component: () => import('../views/Users.vue'),
    meta: { title: '用户管理', icon: 'User', perm: 'user:manage' }
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('../views/Settings.vue'),
    meta: { title: '系统设置', icon: 'Setting', perm: 'config:manage' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    return auth.loggedIn ? '/dashboard' : true
  }

  if (!auth.ready) {
    await auth.fetchMe()
  }
  if (!auth.loggedIn) {
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (to.meta.perm && !auth.permissions.includes(to.meta.perm)) {
    ElMessage.warning('当前账号没有访问该页面的权限')
    return '/dashboard'
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} - LiteRAG` : 'LiteRAG'
})

export default router
