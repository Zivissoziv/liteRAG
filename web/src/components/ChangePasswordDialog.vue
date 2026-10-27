<template>
  <el-dialog v-model="visible" title="修改密码" width="400px" :close-on-click-modal="false" @closed="reset">
    <el-form label-width="76px" label-position="left" class="form" @submit.prevent>
      <el-form-item label="当前密码">
        <el-input v-model="form.currentPassword" type="password" show-password placeholder="请输入当前密码" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 位" />
      </el-form-item>
      <el-form-item label="确认密码">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          placeholder="再次输入新密码"
          @keyup.enter="submit"
        />
      </el-form-item>
    </el-form>
    <p class="hint">修改后所有登录状态都会失效，需要重新登录。</p>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { useAuthStore } from '../stores/auth'

const props = defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const auth = useAuthStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const saving = ref(false)

const reset = () => {
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
}

const submit = async () => {
  if (saving.value) return
  if (!form.currentPassword) {
    ElMessage.warning('请输入当前密码')
    return
  }
  if (!form.newPassword || form.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await authApi.changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })
    visible.value = false
    ElMessage.success('密码已更新，请重新登录')
    auth.clear()
    router.replace('/login')
  } catch (e) {
    // 错误提示由请求层统一处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.hint {
  margin: 0;
  font-size: var(--fs-caption);
  color: var(--text-faint);
  line-height: 1.6;
}
</style>
