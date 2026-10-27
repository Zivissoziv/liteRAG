<template>
  <span class="file-badge" :class="tone" aria-hidden="true">{{ label }}</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  doc: { type: Object, default: () => ({}) },
  ext: { type: String, default: '' }
})

const raw = computed(() => {
  if (props.doc?.sourceType === 'WIKI') return 'wiki'
  return (props.ext || props.doc?.fileExt || props.doc?.name || '').toLowerCase().replace(/^\./, '')
})

const MAP = {
  pdf: ['PDF', 'pdf'],
  doc: ['DOC', 'doc'],
  docx: ['DOC', 'doc'],
  xls: ['XLS', 'xls'],
  xlsx: ['XLS', 'xls'],
  csv: ['CSV', 'xls'],
  ppt: ['PPT', 'ppt'],
  pptx: ['PPT', 'ppt'],
  md: ['MD', 'text'],
  markdown: ['MD', 'text'],
  txt: ['TXT', 'text'],
  wiki: ['WIK', 'wiki']
}

const entry = computed(() => {
  const key = raw.value.split('.').pop()
  if (MAP[key]) return MAP[key]
  return [key ? key.slice(0, 3).toUpperCase() : 'DOC', 'text']
})

const label = computed(() => entry.value[0])
const tone = computed(() => entry.value[1])
</script>

<style scoped>
.file-badge {
  width: 28px;
  height: 20px;
  flex: 0 0 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-inner);
  border: 0.5px solid transparent;
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  line-height: 1;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
  user-select: none;
}

.pdf {
  background: var(--red-3);
  border-color: var(--red-6);
  color: var(--red-11);
}

.doc {
  background: var(--blue-3);
  border-color: var(--blue-6);
  color: var(--blue-11);
}

.xls {
  background: var(--green-3);
  border-color: var(--green-6);
  color: var(--green-11);
}

.ppt {
  background: var(--amber-3);
  border-color: var(--amber-6);
  color: var(--amber-11);
}

.text {
  background: var(--gray-3);
  border-color: var(--border);
  color: var(--text-muted);
}

.wiki {
  background: var(--accent-bg);
  border-color: var(--accent-border);
  color: var(--accent-text);
}
</style>
