<template>
  <div class="login">
    <div class="panel">
      <div class="logo">
        <div class="logo-mark">LR</div>
        <div class="logo-name">LiteRAG</div>
      </div>

      <h1 class="title">登录</h1>

      <form class="form" @submit.prevent="submit">
        <label class="field">
          <span class="label">账号</span>
          <el-input
            v-model="form.username"
            size="large"
            placeholder="请输入账号"
            autocomplete="username"
            :disabled="loading"
          />
        </label>

        <label class="field">
          <span class="label">密码</span>
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
            :disabled="loading"
          />
        </label>

        <div v-if="error" class="error">{{ error }}</div>

        <el-button type="primary" size="large" class="submit" :loading="loading" native-type="submit">
          登录
        </el-button>
      </form>

      <div class="demo">
        <span class="demo-label">演示账号</span>
        <div class="demo-list">
          <button
            v-for="account in accounts"
            :key="account.username"
            type="button"
            class="demo-item"
            :class="{ on: form.username === account.username }"
            @click="fill(account)"
          >
            <span class="demo-role">{{ account.role }}</span>
            <span class="demo-user">{{ account.username }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

const accounts = [
  { username: 'admin', password: 'admin123', role: '管理员' },
  { username: 'editor', password: 'editor123', role: '编辑者' },
  { username: 'viewer', password: 'viewer123', role: '只读' }
]

const fill = (account) => {
  form.username = account.username
  form.password = account.password
  error.value = ''
}

const submit = async () => {
  if (loading.value) return
  error.value = ''
  if (!form.username.trim() || !form.password) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background: var(--bg-app);
}

.panel {
  width: 100%;
  max-width: 360px;
  padding: 32px 28px 24px;
  background: var(--bg-panel);
  border: 0.5px solid var(--border-subtle);
  border-radius: var(--r-surface);
}

.logo {
  display: flex;
  align-items: center;
  gap: 9px;
}

.logo-mark {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-control);
  background: var(--accent);
  color: #fff;
  font-size: 12px;
  font-weight: var(--fw-strong);
  letter-spacing: 0.02em;
}

.logo-name {
  font-size: var(--fs-body);
  font-weight: var(--fw-strong);
  letter-spacing: -0.01em;
}

.title {
  margin: 20px 0 18px;
  font-size: var(--fs-title);
  font-weight: var(--fw-strong);
  letter-spacing: -0.01em;
  line-height: 1.2;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.label {
  font-size: var(--fs-caption);
  color: var(--text-muted);
}

.error {
  font-size: var(--fs-caption);
  color: var(--red-11);
  background: var(--red-3);
  border-radius: var(--r-inner);
  padding: 7px 10px;
  line-height: 1.5;
}

.submit {
  width: 100%;
  margin-top: 2px;
}

.demo {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 0.5px solid var(--border-subtle);
}

.demo-label {
  font-size: var(--fs-caption);
  color: var(--text-faint);
}

.demo-list {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.demo-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 7px 8px;
  background: none;
  border: 0.5px solid var(--border-subtle);
  border-radius: var(--r-control);
  cursor: pointer;
  text-align: left;
  transition: background-color var(--dur) var(--ease), border-color var(--dur) var(--ease);
}

.demo-item:hover {
  background: var(--bg-subtle);
}

.demo-item.on {
  border-color: var(--accent-border);
  background: var(--accent-bg);
}

.demo-role {
  font-size: var(--fs-caption);
  color: var(--text-muted);
}

.demo-item.on .demo-role {
  color: var(--accent-text);
}

.demo-user {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
}
</style>
