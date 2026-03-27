<template>
  <div class="app-shell">
    <div class="app-orb orb-left"></div>
    <div class="app-orb orb-right"></div>
    <router-view />
  </div>
</template>

<script setup>
</script>

<style>
html,
body {
  margin: 0;
}

body {
  min-height: 100vh;
}

.app-shell {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
}

.app-orb {
  position: fixed;
  z-index: 0;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  filter: blur(60px);
  pointer-events: none;
  opacity: 0.45;
}

.orb-left {
  top: -140px;
  left: -120px;
  background: radial-gradient(circle, rgba(58, 153, 255, 0.34) 0%, rgba(58, 153, 255, 0) 72%);
}

.orb-right {
  top: 18%;
  right: -180px;
  background: radial-gradient(circle, rgba(44, 181, 160, 0.26) 0%, rgba(44, 181, 160, 0) 72%);
}
</style>
