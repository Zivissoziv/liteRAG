<template>
  <el-dialog v-model="visible" title="上传文档" width="640px" align-center @closed="reset">
    <el-upload
      v-model:file-list="fileList"
      drag
      multiple
      :auto-upload="false"
      :on-change="onChange"
      :on-remove="onChange"
      accept=".docx,.pdf,.xlsx,.xls,.pptx,.md,.markdown,.txt,.csv,.html,.htm,.json"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖入文件，或<em>点击选择</em></div>
      <template #tip>
        <div class="upload-tip">docx / pdf / xlsx / xls / pptx / md / txt / csv / html，单文件 200MB，单次最多 20 个</div>
      </template>
    </el-upload>

    <el-form label-width="76px" label-position="left" class="meta-form">
      <el-form-item label="目录">
        <FolderSelect v-if="visible" v-model="folderId" :kb-id="kbId" />
      </el-form-item>
      <el-form-item label="分块规则">
        <el-select v-model="strategy" class="strategy">
          <el-option value="auto" label="智能推荐（按文件类型自动选择）" />
          <el-option value="heading" label="标题结构（md / pptx 按章节切，带出处路径）" />
          <el-option value="table" label="表格优先（xlsx 按表头+行组切）" />
          <el-option value="fixed" label="固定长度（段落 + 重叠滑窗）" />
        </el-select>
        <div class="strategy-hint">智能推荐：md / pptx 走标题结构，xlsx 走表格优先，其余固定长度</div>
      </el-form-item>
      <el-form-item label="标签">
        <el-input v-model="tags" placeholder="可选，逗号分隔" maxlength="120" />
      </el-form-item>
    </el-form>

    <div v-if="rejected.length" class="rejected">
      <div class="rejected-title">未上传</div>
      <div v-for="item in rejected" :key="item.name" class="rejected-item">
        <span class="name">{{ item.name }}</span>
        <span class="reason">{{ item.reason }}</span>
      </div>
    </div>

    <template #footer>
      <div class="footer">
        <span class="count"><template v-if="fileList.length">已选 {{ fileList.length }} 个文件</template></span>
        <div>
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" :disabled="!fileList.length" @click="submit">
            {{ uploading ? `上传中 ${percent}%` : '上传' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { kbApi } from '../api'
import FolderSelect from './FolderSelect.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  kbId: { type: [Number, String], required: true },
  /** 默认归属目录，0 或空表示根目录 */
  folderId: { type: [Number, String], default: 0 }
})
const emit = defineEmits(['update:modelValue', 'uploaded'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const fileList = ref([])
const folderId = ref(0)
const tags = ref('')
const strategy = ref('auto')
const uploading = ref(false)
const percent = ref(0)
const rejected = ref([])

watch(
  () => props.modelValue,
  (open) => {
    if (open) folderId.value = props.folderId || 0
  }
)

const onChange = (file, files) => {
  fileList.value = files
  rejected.value = []
}

const submit = async () => {
  if (!fileList.value.length) return
  const form = new FormData()
  fileList.value.forEach((f) => {
    if (f.raw) form.append('files', f.raw)
  })
  if (folderId.value) form.append('folderId', folderId.value)
  form.append('strategy', strategy.value)
  if (tags.value) form.append('tags', tags.value)

  uploading.value = true
  percent.value = 0
  try {
    const res = await kbApi.upload(props.kbId, form, (e) => {
      if (e.total) percent.value = Math.round((e.loaded / e.total) * 100)
    })
    const uploaded = res?.uploaded || []
    rejected.value = res?.rejected || []
    if (uploaded.length) {
      ElMessage.success(`已上传 ${uploaded.length} 个文件`)
      emit('uploaded')
    }
    if (!rejected.value.length) {
      visible.value = false
    } else {
      fileList.value = []
    }
  } finally {
    uploading.value = false
  }
}

const reset = () => {
  fileList.value = []
  folderId.value = 0
  tags.value = ''
  strategy.value = 'auto'
  percent.value = 0
  uploading.value = false
  rejected.value = []
}
</script>

<style scoped>
.upload-tip {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  margin-top: 8px;
  line-height: 1.6;
}
.meta-form {
  margin-top: 16px;
}
.strategy {
  width: 100%;
}
.strategy-hint {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  line-height: 1.5;
  margin-top: 2px;
}
.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.count {
  font-size: var(--fs-caption);
  color: var(--text-faint);
}
.rejected {
  margin-top: 12px;
  background: var(--warning-bg);
  border: 0.5px solid var(--warning-border);
  border-radius: var(--r-control);
  padding: 10px 12px;
}
.rejected-title {
  font-size: var(--fs-caption);
  font-weight: var(--fw-strong);
  color: var(--warning-text);
  margin-bottom: 4px;
}
.rejected-item {
  display: flex;
  gap: 10px;
  font-size: var(--fs-caption);
  padding: 2px 0;
}
.rejected-item .name {
  color: var(--text-muted);
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rejected-item .reason {
  color: var(--warning-text);
}
</style>
