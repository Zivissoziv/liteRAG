<template>
  <div class="tree">
    <div v-for="node in nodes" :key="node.id" class="node">
      <div
        class="row"
        :class="{ active: current === node.id }"
        :style="{ paddingLeft: (depth - 1) * 14 + 6 + 'px' }"
        @click="$emit('select', node)"
      >
        <button
          v-if="node.children && node.children.length"
          type="button"
          class="twist"
          :class="{ open: isOpen(node.id) }"
          @click.stop="toggle(node.id)"
        >
          <el-icon><CaretRight /></el-icon>
        </button>
        <span v-else class="twist-space"></span>

        <el-icon class="folder-icon"><Folder /></el-icon>
        <span class="name">{{ node.name }}</span>
        <span class="count">{{ node.totalCount }}</span>

        <el-dropdown
          v-if="!readonly"
          trigger="click"
          placement="bottom-end"
          @command="(cmd) => $emit('menu', cmd, node)"
          @click.stop
        >
          <button type="button" class="more" @click.stop>
            <el-icon><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="create">新建子目录</el-dropdown-item>
              <el-dropdown-item command="rename">重命名</el-dropdown-item>
              <el-dropdown-item command="delete" divided>删除目录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <FolderTree
        v-if="node.children && node.children.length && isOpen(node.id)"
        :nodes="node.children"
        :current="current"
        :depth="depth + 1"
        :readonly="readonly"
        @select="$emit('select', $event)"
        @menu="(cmd, target) => $emit('menu', cmd, target)"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineOptions({ name: 'FolderTree' })

defineProps({
  nodes: { type: Array, default: () => [] },
  current: { type: [Number, String], default: null },
  depth: { type: Number, default: 1 },
  /** 只读用户不显示目录操作菜单 */
  readonly: { type: Boolean, default: false }
})
defineEmits(['select', 'menu'])

/** 默认展开，仅记录被折叠的节点 */
const collapsed = ref(new Set())
const isOpen = (id) => !collapsed.value.has(id)
const toggle = (id) => {
  const next = new Set(collapsed.value)
  next.has(id) ? next.delete(id) : next.add(id)
  collapsed.value = next
}
</script>

<style scoped>
.tree {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding-right: 6px;
  border-radius: var(--r-control);
  color: var(--text-muted);
  cursor: pointer;
  transition: background-color var(--dur) var(--ease), color var(--dur) var(--ease);
}
.row:hover {
  background: var(--bg-hover);
  color: var(--text);
}
.row.active {
  background: var(--accent-bg);
  color: var(--accent-text);
}
.twist {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--icon-quiet);
  cursor: pointer;
}
.twist .el-icon {
  font-size: 11px;
  transition: transform var(--dur) var(--ease);
}
.twist.open .el-icon {
  transform: rotate(90deg);
}
.twist-space {
  width: 14px;
  flex: 0 0 14px;
}
.folder-icon {
  font-size: 14px;
  flex: 0 0 auto;
  color: var(--icon-quiet);
}
.row.active .folder-icon {
  color: var(--accent-text);
}
.name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.count {
  font-size: var(--fs-caption);
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
  flex: 0 0 auto;
  transition: opacity var(--dur) var(--ease);
}
.row.active .count {
  color: var(--accent-text);
}
.more {
  position: absolute;
  right: 3px;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: var(--r-inner);
  background: none;
  color: var(--icon-quiet);
  opacity: 0;
  cursor: pointer;
  transition: opacity var(--dur) var(--ease), background-color var(--dur) var(--ease);
}
.more .el-icon {
  font-size: 13px;
}
.row:hover .more {
  opacity: 1;
}
.row:hover .count {
  opacity: 0;
}
.more:hover {
  background: var(--gray-4);
  color: var(--text);
}
</style>
