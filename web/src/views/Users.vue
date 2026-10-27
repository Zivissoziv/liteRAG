<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">用户管理</h1>
      <div class="header-actions">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新建用户
        </el-button>
      </div>
    </div>

    <div class="card user-list">
      <div v-for="user in list" :key="user.id" class="user-row">
        <div class="avatar" :class="user.role.toLowerCase()">{{ initial(user) }}</div>

        <div class="u-main">
          <div class="u-title">
            <span class="u-name">{{ user.displayName }}</span>
            <el-tag v-if="user.id === auth.user?.id" size="small">当前登录</el-tag>
          </div>
          <div class="u-sub">{{ user.username }}</div>
        </div>

        <div class="u-cell">
          <el-tag size="small" :type="roleType(user.role)">{{ user.roleLabel }}</el-tag>
        </div>

        <div class="u-cell state">
          <span class="dot" :class="user.enabled ? 'ok' : 'off'"></span>
          <span>{{ user.enabled ? '启用' : '停用' }}</span>
        </div>

        <div class="u-cell login">
          <span class="time">{{ user.lastLoginAt ? formatTime(user.lastLoginAt) : '从未登录' }}</span>
        </div>

        <el-dropdown trigger="click" @command="(cmd) => onCommand(cmd, user)">
          <button class="more" type="button">
            <el-icon><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="edit">编辑</el-dropdown-item>
              <el-dropdown-item command="password">重置密码</el-dropdown-item>
              <el-dropdown-item command="toggle">
                {{ user.enabled ? '停用账号' : '启用账号' }}
              </el-dropdown-item>
              <el-dropdown-item command="delete" divided>删除用户</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <button class="add-row" type="button" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>新建用户</span>
      </button>
    </div>

    <div class="card role-card">
      <div class="section-head">
        <h2 class="section-title">角色权限</h2>
      </div>
      <div class="role-grid">
        <div v-for="role in roles" :key="role.role" class="role-item">
          <div class="role-head">
            <el-tag size="small" :type="roleType(role.role)">{{ role.label }}</el-tag>
            <span class="role-code">{{ role.role }}</span>
          </div>
          <div class="role-perms">
            <span v-for="perm in role.permissions" :key="perm" class="perm">
              <el-icon class="perm-ok"><Check /></el-icon>{{ permLabel(perm) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="dialogOpen"
      :title="editing ? '编辑用户' : '新建用户'"
      width="420px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form label-width="76px" label-position="left" class="form" @submit.prevent>
        <el-form-item label="显示名">
          <el-input v-model="form.displayName" placeholder="例如 张三" maxlength="40" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.username" :disabled="!!editing" placeholder="字母数字，3 到 32 位" />
        </el-form-item>
        <el-form-item v-if="!editing" label="初始密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="role in roles" :key="role.role" :value="role.role" :label="role.label">
              <div class="opt">
                <span>{{ role.label }}</span>
                <span class="opt-code">{{ role.role }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="passwordOpen" title="重置密码" width="380px" :close-on-click-modal="false" @closed="resetPassword">
      <el-form label-width="76px" label-position="left" class="form" @submit.prevent>
        <el-form-item label="用户">
          <span class="target">{{ passwordTarget?.displayName }}（{{ passwordTarget?.username }}）</span>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
      </el-form>
      <p class="hint">重置后该用户的登录状态立即失效，下次登录需使用新密码。</p>
      <template #footer>
        <el-button @click="passwordOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { formatTime } from '../utils/format'

const auth = useAuthStore()

const list = ref([])
const roles = ref([])
const dialogOpen = ref(false)
const editing = ref(null)
const saving = ref(false)

const form = reactive({
  displayName: '',
  username: '',
  password: '',
  role: 'VIEWER',
  enabled: true
})

const passwordOpen = ref(false)
const passwordTarget = ref(null)
const newPassword = ref('')

const roleType = (role) =>
  role === 'ADMIN' ? 'primary' : role === 'EDITOR' ? 'success' : 'info'

const permLabel = (perm) =>
  ({
    'content:read': '浏览知识库与文档',
    'content:write': '上传与编辑内容',
    'config:manage': '配置模型与系统',
    'user:manage': '管理用户与角色'
  })[perm] || perm

const initial = (user) => (user.displayName || user.username || '?').slice(0, 1).toUpperCase()

const load = async () => {
  list.value = await userApi.list()
}

const loadRoles = async () => {
  roles.value = await userApi.roles()
}

const openCreate = () => {
  editing.value = null
  Object.assign(form, { displayName: '', username: '', password: '', role: 'VIEWER', enabled: true })
  dialogOpen.value = true
}

const resetForm = () => {
  editing.value = null
}

const save = async () => {
  if (saving.value) return
  if (!form.username.trim()) {
    ElMessage.warning('请输入账号')
    return
  }
  if (!editing.value && (!form.password || form.password.length < 6)) {
    ElMessage.warning('初始密码至少 6 位')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await userApi.update(editing.value.id, {
        displayName: form.displayName,
        role: form.role,
        enabled: form.enabled
      })
      ElMessage.success('已保存')
    } else {
      await userApi.create({ ...form, username: form.username.trim() })
      ElMessage.success('已创建用户')
    }
    dialogOpen.value = false
    await load()
  } catch (e) {
    // 请求层已提示
  } finally {
    saving.value = false
  }
}

const resetPassword = () => {
  newPassword.value = ''
  passwordTarget.value = null
}

const submitPassword = async () => {
  if (!newPassword.value || newPassword.value.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  saving.value = true
  try {
    await userApi.resetPassword(passwordTarget.value.id, newPassword.value)
    ElMessage.success('密码已重置')
    passwordOpen.value = false
  } catch (e) {
    // 请求层已提示
  } finally {
    saving.value = false
  }
}

const onCommand = async (cmd, user) => {
  if (cmd === 'edit') {
    editing.value = user
    Object.assign(form, {
      displayName: user.displayName,
      username: user.username,
      password: '',
      role: user.role,
      enabled: user.enabled
    })
    dialogOpen.value = true
    return
  }
  if (cmd === 'password') {
    passwordTarget.value = user
    passwordOpen.value = true
    return
  }
  if (cmd === 'toggle') {
    try {
      await userApi.update(user.id, { enabled: !user.enabled })
      ElMessage.success(user.enabled ? '已停用账号' : '已启用账号')
      await load()
    } catch (e) {
      // 请求层已提示
    }
    return
  }
  if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm(`删除后「${user.displayName}」将无法登录，是否继续？`, '删除用户', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      })
    } catch (e) {
      return
    }
    try {
      await userApi.remove(user.id)
      ElMessage.success('已删除')
      await load()
    } catch (e) {
      // 请求层已提示
    }
  }
}

onMounted(async () => {
  await Promise.all([load(), loadRoles()])
})
</script>

<style scoped>
.user-list {
  padding: 0;
  overflow: hidden;
}

.user-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  transition: background-color var(--dur) var(--ease);
}

.user-row + .user-row {
  border-top: 0.5px solid var(--border-subtle);
}

.user-row:hover {
  background: var(--gray-2);
}

.avatar {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: var(--fs-caption);
  font-weight: var(--fw-strong);
}

.avatar.admin {
  background: var(--accent-bg);
  color: var(--accent-text);
}

.avatar.editor {
  background: var(--success-bg);
  color: var(--success-text);
}

.avatar.viewer {
  background: var(--gray-3);
  color: var(--text-muted);
}

.u-main {
  min-width: 0;
  flex: 1;
}

.u-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.u-name {
  font-size: var(--fs-body);
  font-weight: var(--fw-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.u-sub {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  line-height: 1.5;
}

.u-cell {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--fs-caption);
  color: var(--text-muted);
}

.u-cell.state {
  width: 64px;
}

.u-cell.login {
  width: 108px;
  justify-content: flex-end;
}

.time {
  font-variant-numeric: tabular-nums;
  color: var(--text-faint);
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
  transition: opacity var(--dur) var(--ease), background-color var(--dur) var(--ease);
}

.user-row:hover .more,
.more:focus-visible {
  opacity: 1;
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

.role-card {
  margin-top: 12px;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.role-item {
  padding: 12px 14px;
  border: 0.5px solid var(--border-subtle);
  border-radius: var(--r-control);
  background: var(--bg-subtle);
}

.role-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-code {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
}

.role-perms {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 10px;
}

.perm {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--fs-caption);
  color: var(--text-muted);
  line-height: 1.5;
}

.perm-ok {
  font-size: 11px;
  color: var(--success-text);
  flex-shrink: 0;
}

.opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.opt-code {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
}

.target {
  font-size: var(--fs-body);
  color: var(--text-muted);
}

.hint {
  margin: 0;
  font-size: var(--fs-caption);
  color: var(--text-faint);
  line-height: 1.6;
}

@media (max-width: 1000px) {
  .u-cell.login {
    display: none;
  }
  .role-grid {
    grid-template-columns: 1fr;
  }
}
</style>
