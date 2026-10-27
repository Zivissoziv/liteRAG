<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑知识库' : '新建知识库'"
    width="560px"
    align-center
    @closed="onClosed"
  >
    <el-form :model="form" label-width="88px" label-position="left">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" maxlength="60" show-word-limit placeholder="知识库名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="300" placeholder="可选" />
      </el-form-item>
      <el-form-item label="向量模型">
        <el-select v-model="form.embeddingModelId" placeholder="选择 Embedding 模型" style="width: 100%">
          <el-option v-for="m in models" :key="m.id" :value="m.id" :label="m.name">
            <div class="opt-row">
              <span>{{ m.name }}</span>
              <span class="opt-dim">{{ m.dimension ? m.dimension + ' 维' : '自动' }}</span>
            </div>
          </el-option>
        </el-select>
        <p class="hint">更换模型后需重新索引已有文档</p>
      </el-form-item>
      <el-form-item label="分块长度">
        <el-slider v-model="form.chunkSize" :min="200" :max="2000" :step="50" show-input />
      </el-form-item>
      <el-form-item label="重叠长度">
        <el-slider v-model="form.chunkOverlap" :min="0" :max="400" :step="10" show-input />
      </el-form-item>
      <el-form-item label="MCP 开放">
        <el-switch v-model="form.mcpExposed" />
      </el-form-item>

      <el-form-item label="访问范围">
        <el-radio-group v-model="form.visibility">
          <el-radio-button value="PUBLIC">全员可见</el-radio-button>
          <el-radio-button value="RESTRICTED">指定范围</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <template v-if="form.visibility === 'RESTRICTED'">
        <el-form-item label="可见角色">
          <el-select
            v-model="form.accessRoles"
            multiple
            placeholder="选择角色"
            style="width: 100%"
            :disabled="!options.users.length && !options.roles.length"
          >
            <el-option v-for="r in options.roles" :key="r.role" :value="r.role" :label="r.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="指定用户">
          <el-select
            v-model="form.accessUserIds"
            multiple
            filterable
            placeholder="可选，按账号搜索"
            style="width: 100%"
          >
            <el-option
              v-for="u in options.users"
              :key="u.id"
              :value="u.id"
              :label="`${u.displayName}（${u.username}）`"
            >
              <div class="opt-row">
                <span>{{ u.displayName }}<span class="opt-sub">{{ u.username }}</span></span>
                <span class="opt-dim">{{ u.roleLabel }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <p class="hint access-hint">管理员始终可见；文档、目录与检索结果都会继承该知识库的可见范围。</p>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">{{ isEdit ? '保存' : '创建' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { kbApi, modelApi } from '../api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  kb: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const isEdit = computed(() => !!props.kb?.id)
const saving = ref(false)
const models = ref([])
const options = ref({ roles: [], users: [] })

const form = reactive({
  name: '',
  description: '',
  embeddingModelId: null,
  chunkSize: 800,
  chunkOverlap: 120,
  mcpExposed: true,
  visibility: 'PUBLIC',
  accessRoles: [],
  accessUserIds: []
})

const loadModels = async () => {
  try {
    models.value = await modelApi.list('EMBEDDING')
    if (!form.embeddingModelId && models.value.length) {
      form.embeddingModelId = (models.value.find((m) => m.isDefault) || models.value[0]).id
    }
  } catch (e) {
    models.value = []
  }
}

const loadOptions = async () => {
  try {
    options.value = await kbApi.accessOptions()
  } catch (e) {
    options.value = { roles: [], users: [] }
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    loadModels()
    loadOptions()
    if (props.kb) {
      Object.assign(form, {
        name: props.kb.name,
        description: props.kb.description || '',
        embeddingModelId: props.kb.embeddingModelId,
        chunkSize: props.kb.chunkSize || 800,
        chunkOverlap: props.kb.chunkOverlap || 120,
        mcpExposed: props.kb.mcpExposed !== false,
        visibility: props.kb.visibility || 'PUBLIC',
        accessRoles: [...(props.kb.accessRoles || [])],
        accessUserIds: [...(props.kb.accessUserIds || [])]
      })
    } else {
      Object.assign(form, {
        name: '',
        description: '',
        embeddingModelId: null,
        chunkSize: 800,
        chunkOverlap: 120,
        mcpExposed: true,
        visibility: 'PUBLIC',
        accessRoles: [],
        accessUserIds: []
      })
    }
  }
)

const submit = async () => {
  if (!form.name.trim()) {
    ElMessage.warning('请填写知识库名称')
    return
  }
  if (form.visibility === 'RESTRICTED' && !form.accessRoles.length && !form.accessUserIds.length) {
    ElMessage.warning('指定范围内至少选择一个角色或用户')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      accessRoles: form.visibility === 'RESTRICTED' ? form.accessRoles : [],
      accessUserIds: form.visibility === 'RESTRICTED' ? form.accessUserIds : []
    }
    if (isEdit.value) {
      await kbApi.update(props.kb.id, payload)
      ElMessage.success('已保存')
    } else {
      await kbApi.create(payload)
      ElMessage.success('知识库已创建')
    }
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

const onClosed = () => {
  saving.value = false
}
</script>

<style scoped>
.opt-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.opt-dim {
  color: var(--text-faint);
  font-size: var(--fs-caption);
}
.opt-sub {
  margin-left: 6px;
  color: var(--text-faint);
  font-size: var(--fs-caption);
}
.access-hint {
  margin-left: 88px;
}
</style>
