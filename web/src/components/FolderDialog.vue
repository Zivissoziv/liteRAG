<template>
  <el-dialog
    v-model="visible"
    :title="folder ? '重命名目录' : '新建目录'"
    width="420px"
    align-center
    @closed="reset"
  >
    <p v-if="parentPath" class="parent-hint">上级目录：{{ parentPath }}</p>
    <el-input
      v-model="name"
      placeholder="目录名称"
      maxlength="60"
      show-word-limit
      @keyup.enter="submit"
    />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!name.trim()" @click="submit">
        {{ folder ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { folderApi } from '../api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  kbId: { type: [Number, String], required: true },
  /** 新建时的上级目录，null 表示一级目录 */
  parent: { type: Object, default: null },
  /** 重命名时的目标目录 */
  folder: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const name = ref('')
const saving = ref(false)
const parentPath = computed(() => props.folder?.path || props.parent?.path || '')

const reset = () => {
  name.value = ''
  saving.value = false
}

const submit = async () => {
  const value = name.value.trim()
  if (!value) return
  saving.value = true
  try {
    if (props.folder) {
      await folderApi.update(props.folder.id, { name: value })
      ElMessage.success('目录已重命名')
    } else {
      await folderApi.create(props.kbId, { name: value, parentId: props.parent?.id ?? null })
      ElMessage.success('目录已创建')
    }
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

// 打开时带入原名
watch(
  () => props.modelValue,
  (open) => {
    if (open) name.value = props.folder?.name || ''
  }
)
</script>

<style scoped>
.parent-hint {
  margin: 0 0 10px;
  font-size: var(--fs-caption);
  color: var(--text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
