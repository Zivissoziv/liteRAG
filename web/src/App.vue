<template>
  <router-view v-if="isBare" />

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="panel">
        <div class="brand">
          <div class="brand-mark">LR</div>
          <div class="brand-title">LiteRAG</div>
        </div>

        <div class="nav">
          <template v-for="group in visibleGroups" :key="group.title">
            <div class="nav-group-title">{{ group.title }}</div>
            <div
              v-for="item in group.items"
              :key="item.path"
              class="nav-item"
              :class="{ active: isActive(item.path) }"
              tabindex="0"
              @click="router.push(item.path)"
              @keydown.enter="router.push(item.path)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </div>
          </template>
        </div>

        <el-dropdown trigger="click" placement="top-start" @command="onUserCommand">
          <div class="user-card">
            <div class="avatar">{{ initial }}</div>
            <div class="u-main">
              <div class="u-name">{{ auth.displayName }}</div>
              <div class="u-role">{{ auth.roleLabel }}</div>
            </div>
            <el-icon class="u-more"><MoreFilled /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">
                <el-icon><Lock /></el-icon>修改密码
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <main class="main">
      <router-view v-slot="{ Component }">
        <transition name="view" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <ChangePasswordDialog v-model="passwordOpen" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from './stores/auth'
import ChangePasswordDialog from './components/ChangePasswordDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const passwordOpen = ref(false)

const isBare = computed(() => !!route.meta.bare)
const initial = computed(() => (auth.displayName || '?').slice(0, 1).toUpperCase())

const groups = computed(() => [
  {
    title: '工作台',
    items: [
      { path: '/dashboard', title: '概览', icon: 'DataLine' },
      { path: '/kb', title: '知识库', icon: 'Collection' },
      { path: '/search', title: '检索问答', icon: 'Search' }
    ]
  },
  {
    title: '系统',
    items: [
      { path: '/models', title: '模型配置', icon: 'Cpu', perm: 'config:manage' },
      { path: '/mcp', title: 'MCP 接入', icon: 'Connection' },
      { path: '/users', title: '用户管理', icon: 'User', perm: 'user:manage' },
      { path: '/settings', title: '系统设置', icon: 'Setting', perm: 'config:manage' }
    ]
  }
])

const visibleGroups = computed(() =>
  groups.value
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.perm || auth.permissions.includes(item.perm))
    }))
    .filter((group) => group.items.length > 0)
)

const isActive = (path) => {
  if (path === '/kb') return route.path === '/kb' || route.path.startsWith('/kb/')
  return route.path === path
}

const onUserCommand = async (command) => {
  if (command === 'password') {
    passwordOpen.value = true
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('退出后需要重新登录。', '退出登录', {
        confirmButtonText: '退出',
        cancelButtonText: '取消'
      })
    } catch (e) {
      return
    }
    await auth.logout()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}

const onExpired = () => {
  if (route.path === '/login') return
  auth.clear()
  ElMessage.warning('登录状态已失效，请重新登录')
  router.replace({ path: '/login', query: { redirect: route.fullPath } })
}

onMounted(() => {
  window.addEventListener('auth:expired', onExpired)
})

onUnmounted(() => {
  window.removeEventListener('auth:expired', onExpired)
})
</script>

<style scoped>
.brand-title {
  letter-spacing: -0.01em;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 6px 6px;
  margin-left: -6px;
  margin-right: -6px;
  border-radius: var(--r-control);
  cursor: pointer;
  transition: background-color var(--dur) var(--ease);
  outline: none;
}

.user-card:hover {
  background: var(--bg-hover);
}

.avatar {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--gray-4);
  color: var(--text-muted);
  font-size: var(--fs-caption);
  font-weight: var(--fw-strong);
}

.u-main {
  min-width: 0;
  flex: 1;
}

.u-name {
  font-size: var(--fs-caption);
  color: var(--text);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.u-role {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  line-height: 1.3;
}

.u-more {
  font-size: 13px;
  color: var(--icon-quiet);
  flex-shrink: 0;
}
</style>
