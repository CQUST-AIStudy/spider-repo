<template>
  <div class="page-header">
    <div class="page-header__glow"></div>
    <div class="page-header__body">
      <div class="page-header__text">
        <div class="page-header__eyebrow">教学智能平台</div>
        <h1 class="page-header__title">{{ title }}</h1>
        <p v-if="description" class="page-header__desc">{{ description }}</p>
      </div>
      <div v-if="$slots.default" class="page-header__extra">
        <slot></slot>
      </div>
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
.page-header {
  position: relative;
  overflow: hidden;
  margin-bottom: 24px;
  padding: 26px 30px;
  border-radius: 24px;
  border: 1px solid rgba(126, 157, 183, 0.18);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(246, 250, 255, 0.92)),
    radial-gradient(circle at top right, rgba(18, 112, 216, 0.08), transparent 35%);
  box-shadow: 0 16px 38px rgba(24, 48, 76, 0.08);
}

.page-header__glow {
  position: absolute;
  right: -42px;
  top: -56px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(18, 112, 216, 0.14) 0%, rgba(18, 112, 216, 0) 72%);
}

.page-header__body {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.page-header__text {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.page-header__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #1270d8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  line-height: 1.4;
  word-break: break-word;
}

.page-header__title {
  margin: 0;
  color: #15314a;
  font-size: clamp(28px, 3.2vw, 34px);
  line-height: 1.15;
  letter-spacing: -0.03em;
  word-break: break-word;
}

.page-header__desc {
  max-width: 760px;
  margin: 0;
  color: #5e7288;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.page-header__extra {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .page-header {
    padding: 20px;
    border-radius: 20px;
  }

  .page-header__body {
    flex-direction: column;
    align-items: stretch;
  }

  .page-header__extra {
    justify-content: flex-start;
  }
}
</style>
