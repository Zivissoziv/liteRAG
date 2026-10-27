<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑 Wiki 页面' : '新建 Wiki 页面'"
    width="720px"
    align-center
    top="6vh"
    @closed="reset"
  >
    <el-form :model="form" label-width="76px" label-position="left">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="页面标题" maxlength="120" />
      </el-form-item>
      <div class="row">
        <el-form-item label="目录">
          <FolderSelect v-if="visible" v-model="form.folderId" :kb-id="kbId" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="可选，逗号分隔" maxlength="120" />
        </el-form-item>
      </div>
      <el-form-item v-if="!props.doc" label="分块规则">
        <el-select v-model="form.strategy" class="strategy">
          <el-option value="auto" label="标题结构（推荐，Wiki 天然按标题切）" />
          <el-option value="fixed" label="固定长度（段落 + 重叠滑窗）" />
        </el-select>
      </el-form-item>
      <el-form-item label="内容" required>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="16"
          resize="vertical"
          placeholder="支持 Markdown"
          class="md-input"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="footer">
        <span class="count">{{ form.content.length }} 字符</span>
        <div>
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存并索引</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { docApi, kbApi } from '../api'
import FolderSelect from './FolderSelect.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  kbId: { type: [Number, String], default: null },
  doc: { type: Object, default: null },
  /** 新建时默认归属目录，0 或空表示根目录 */
  folderId: { type: [Number, String], default: 0 }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const isEdit = computed(() => !!props.doc?.id)
const saving = ref(false)

const form = reactive({ title: '', content: '', tags: '', folderId: 0, strategy: 'auto' })

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    Object.assign(form, {
      title: props.doc?.title || '',
      content: props.doc?.content || '',
      tags: props.doc?.tags || '',
      strategy: props.doc?.chunkStrategy?.toLowerCase() || 'auto',
      folderId: props.doc?.folderId ?? props.folderId ?? 0
    })
  }
)

const submit = async () => {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题和内容不能为空')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await docApi.updateWiki(props.doc.id, {
        title: form.title, content: form.content, tags: form.tags, folderId: form.folderId
      })
      ElMessage.success('已保存')
    } else {
      await kbApi.createWiki(props.kbId, {
        title: form.title, content: form.content, tags: form.tags, folderId: form.folderId,
        chunkStrategy: form.strategy
      })
      ElMessage.success('已创建')
    }
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

const reset = () => {
  saving.value = false
}
</script>

<style scoped>
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.count {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
}
.md-input :deep(textarea) {
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: var(--fs-caption);
  line-height: 1.7;
}
</style>
