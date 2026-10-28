<template>
  <div class="hit-card">
    <div class="hit-head">
      <span class="rank">{{ index + 1 }}</span>
      <span class="doc" :title="hit.docName">{{ hit.docName }}</span>
      <el-tag size="small">{{ hit.kbName }}</el-tag>
      <el-tag size="small">#{{ hit.seq }}</el-tag>
      <div class="spacer"></div>
      <span class="score">{{ hit.score.toFixed(4) }}</span>
      <el-button size="small" text @click="$emit('open', hit.docId)">查看文档</el-button>
    </div>

    <div v-if="showBar" class="score-bar">
      <div class="score-fill" :style="{ width: Math.max(2, hit.score * 100) + '%' }"></div>
    </div>

    <div class="hit-content" :class="{ clamped: collapsible && !open }">{{ hit.content }}</div>

    <button v-if="collapsible" class="more" type="button" @click="open = !open">
      <span>{{ open ? '收起' : '展开全文' }}</span>
      <el-icon><ArrowUp v-if="open" /><ArrowDown v-else /></el-icon>
    </button>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  hit: { type: Object, required: true },
  index: { type: Number, default: 0 },
  showBar: { type: Boolean, default: false }
})

defineEmits(['open'])

const open = ref(false)

// 只在正文确实超出 3 行时才给「展开」，避免短片段出现无意义按钮
const collapsible = computed(() => {
  const text = props.hit.content || ''
  const lines = text.split('\n').length
  return text.length > 120 || lines > 3
})
</script>

<style scoped>
/* 卡片密度按「一屏看更多」调过：上下内边距收紧，行高略降 */
.hit-card {
  padding: 10px 13px;
  margin-bottom: 8px;
}
.hit-card:last-child {
  margin-bottom: 0;
}
.hit-head {
  margin-bottom: 5px;
}
.score-bar {
  margin-top: 0;
  margin-bottom: 7px;
  height: 3px;
}
.hit-content {
  line-height: 1.55;
  font-size: var(--fs-caption);
}
/* 默认折叠为 3 行，长片段不再把整页顶出屏幕 */
.hit-content.clamped {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 3px;
  padding: 0;
  border: 0;
  background: none;
  font-size: var(--fs-caption);
  color: var(--accent-text);
  cursor: pointer;
  border-radius: var(--r-inner);
}
.more:hover {
  text-decoration: underline;
  text-underline-offset: 2px;
}
.more .el-icon {
  font-size: 11px;
}
</style>
