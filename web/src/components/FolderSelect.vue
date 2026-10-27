<template>
  <el-tree-select
    v-model="value"
    :data="options"
    node-key="id"
    :props="{ label: 'name', children: 'children' }"
    check-strictly
    :render-after-expand="false"
    :expand-on-click-node="false"
    clearable
    :placeholder="placeholder"
    class="folder-select"
  >
    <template #default="{ data }">
      <span class="opt">
        <el-icon><Folder /></el-icon>
        <span class="opt-name">{{ data.name }}</span>
      </span>
    </template>
  </el-tree-select>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { folderApi } from '../api'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  kbId: { type: [Number, String], required: true },
  placeholder: { type: String, default: '根目录' }
})
const emit = defineEmits(['update:modelValue'])

const tree = ref([])
const options = computed(() => [{ id: 0, name: '根目录' }, ...tree.value])

const value = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const load = async () => {
  try {
    tree.value = await folderApi.tree(props.kbId)
  } catch (e) {
    tree.value = []
  }
}

onMounted(load)
watch(() => props.kbId, load)

defineExpose({ reload: load })
</script>

<style scoped>
.folder-select {
  width: 100%;
}
.opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-body);
}
.opt .el-icon {
  font-size: 13px;
  color: var(--icon-quiet);
}
.opt-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
