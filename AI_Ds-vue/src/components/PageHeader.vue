<template>
  <div class="g-page-header">
    <div class="g-header-inner">
      <div>
        <h1 class="g-title">{{ title }}</h1>
        <p v-if="description" class="g-desc">{{ description }}</p>
      </div>
      <div class="g-extra"><slot></slot></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.g-page-header {
  margin-bottom: 24px;
}
.g-header-inner {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.g-title {
  margin: 0;
  font-size: 28px;
  font-weight: 400;
  color: #202124;
  letter-spacing: -0.5px;
  line-height: 1.2;
}
.g-desc {
  margin: 6px 0 0;
  font-size: 14px;
  color: #5f6368;
  font-weight: 400;
}
</style>
