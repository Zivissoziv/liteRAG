<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑模型配置' : '新增模型配置'"
    width="600px"
    align-center
    @closed="reset"
  >
    <el-form :model="form" label-width="96px" label-position="left">
      <el-form-item label="预设">
        <el-select v-model="presetName" placeholder="选择常用服务商" clearable style="width: 100%" @change="applyPreset">
          <el-option v-for="p in presets" :key="p.name" :value="p.name" :label="p.name" />
        </el-select>
      </el-form-item>

      <el-form-item label="模型类型">
        <el-radio-group v-model="form.type">
          <el-radio-button value="EMBEDDING">Embedding</el-radio-button>
          <el-radio-button value="CHAT">对话模型</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="显示名称" required>
        <el-input v-model="form.name" placeholder="例如：硅基流动 bge-m3" maxlength="60" />
      </el-form-item>

      <el-form-item label="接入协议">
        <el-select v-model="form.provider" style="width: 100%">
          <el-option value="openai" label="OpenAI 兼容（硅基流动 / 通义 / 智谱 / vLLM）" />
          <el-option value="ollama" label="Ollama" />
          <el-option v-if="form.type === 'EMBEDDING'" value="local" label="本地哈希（离线）" />
        </el-select>
      </el-form-item>

      <template v-if="form.provider !== 'local'">
        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com/v1" />
          <p v-if="form.provider === 'openai'" class="hint">填到 /v1 层级，系统自动拼接资源路径</p>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-…（Ollama 可留空）" />
        </el-form-item>
      </template>

      <el-form-item label="模型名称" required>
        <el-input
          v-model="form.modelName"
          :placeholder="form.type === 'EMBEDDING' ? 'text-embedding-3-small' : 'deepseek-chat'"
        />
      </el-form-item>

      <el-form-item v-if="form.type === 'EMBEDDING'" label="向量维度">
        <el-input-number v-model="form.dimension" :min="0" :max="8192" :step="128" controls-position="right" />
        <span class="hint inline">0 表示自动探测</span>
      </el-form-item>

      <el-form-item v-if="form.type === 'EMBEDDING'" label="批大小">
        <el-input-number v-model="form.batchSize" :min="1" :max="128" controls-position="right" />
        <span class="hint inline">每次请求的文本条数</span>
      </el-form-item>

      <el-form-item label="超时（秒）">
        <el-input-number v-model="form.timeout" :min="5" :max="600" :step="10" controls-position="right" />
      </el-form-item>

      <el-form-item label="额外参数">
        <el-input v-model="form.extraParams" type="textarea" :rows="2" placeholder='可选 JSON，例如 {"dimensions":1024}' />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="可选" maxlength="200" />
      </el-form-item>

      <el-form-item label="设为默认">
        <el-switch v-model="form.isDefault" />
      </el-form-item>
    </el-form>

    <el-alert v-if="testResult" :type="testOk ? 'success' : 'error'" :closable="false">
      <template #title>
        <span v-if="testOk">
          连接正常{{ testResult.dimension ? `，维度 ${testResult.dimension}` : '' }}，{{ testResult.costMs }} ms
          <template v-if="testResult.answer">，回复：{{ testResult.answer }}</template>
        </span>
        <span v-else>{{ testResult.message }}</span>
      </template>
    </el-alert>

    <template #footer>
      <div class="footer">
        <el-button :loading="testing" @click="test">测试连接</el-button>
        <div>
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { modelApi } from '../api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  model: { type: Object, default: null },
  defaultType: { type: String, default: 'EMBEDDING' }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const isEdit = computed(() => !!props.model?.id)
const saving = ref(false)
const testing = ref(false)
const presets = ref([])
const presetName = ref('')
const testResult = ref(null)
const testOk = ref(false)

const form = reactive({
  name: '',
  type: 'EMBEDDING',
  provider: 'openai',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  dimension: 0,
  batchSize: 16,
  timeout: 60,
  extraParams: '',
  enabled: true,
  isDefault: false,
  remark: ''
})

const loadPresets = async () => {
  try {
    const all = await modelApi.presets()
    presets.value = all.filter((p) => p.type === form.type)
  } catch (e) {
    presets.value = []
  }
}

watch(
  () => form.type,
  () => {
    loadPresets()
    presetName.value = ''
  }
)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    testResult.value = null
    if (props.model) {
      Object.assign(form, {
        ...props.model,
        apiKey: props.model.apiKey === '******' ? '' : props.model.apiKey || '',
        extraParams: props.model.extraParams || '',
        dimension: props.model.dimension ?? 0,
        batchSize: props.model.batchSize ?? 16,
        timeout: props.model.timeout ?? 60
      })
    } else {
      Object.assign(form, {
        name: '',
        type: props.defaultType,
        provider: 'openai',
        baseUrl: '',
        apiKey: '',
        modelName: '',
        dimension: 0,
        batchSize: 16,
        timeout: 60,
        extraParams: '',
        enabled: true,
        isDefault: false,
        remark: ''
      })
    }
    loadPresets()
  }
)

const applyPreset = (name) => {
  const p = presets.value.find((x) => x.name === name)
  if (!p) return
  form.name = p.name
  form.provider = p.provider
  form.baseUrl = p.baseUrl
  form.modelName = p.modelName
  form.dimension = p.dimension || 0
}

const payload = () => ({
  ...form,
  apiKey: form.apiKey === '******' ? undefined : form.apiKey,
  id: props.model?.id
})

const test = async () => {
  if (!form.modelName && form.provider !== 'local') {
    ElMessage.warning('请先填写模型名称')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const data = payload()
    const res =
      form.type === 'EMBEDDING'
        ? await modelApi.test(data)
        : await modelApi.testChat({ ...data, modelName: form.modelName })
    testResult.value = res
    testOk.value = true
    if (form.type === 'EMBEDDING' && res.dimension) {
      form.dimension = res.dimension
    }
  } catch (e) {
    testOk.value = false
    testResult.value = { message: e.message || '连接失败' }
  } finally {
    testing.value = false
  }
}

const submit = async () => {
  if (!form.name.trim()) {
    ElMessage.warning('请填写显示名称')
    return
  }
  if (form.provider !== 'local' && !form.modelName.trim()) {
    ElMessage.warning('请填写模型名称')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await modelApi.update(props.model.id, { ...form })
    } else {
      await modelApi.create({ ...form })
    }
    ElMessage.success('已保存')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

const reset = () => {
  testResult.value = null
}
</script>

<style scoped>
.footer {
  display: flex;
  justify-content: space-between;
}
</style>
