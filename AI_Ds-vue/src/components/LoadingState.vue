<template>
  <div class="g-loading-state">
    <div v-if="loading" class="g-skeleton-wrap">
      <div class="g-skeleton-row" v-for="i in 4" :key="i">
        <div class="g-skeleton-block" :style="{ width: (90 - i * 12) + '%' }"></div>
      </div>
    </div>
    <slot v-else></slot>
  </div>
</template>

<script setup>
defineProps({ loading: { type: Boolean, default: false } })
</script>

<style scoped>
.g-loading-state { min-height: 200px; }
.g-skeleton-wrap { padding: 32px; }
.g-skeleton-row { margin-bottom: 20px; }
.g-skeleton-block {
  height: 16px;
  border-radius: 8px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: g-shimmer 1.5s ease-in-out infinite;
}
@keyframes g-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
