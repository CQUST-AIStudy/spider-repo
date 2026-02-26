<template>
  <div class="app-container">
    <router-view />
  </div>
</template>

<script setup>
// App级别的逻辑可以在这里添加
</script>

<style>
@import './assets/styles/fonts.css';

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: "Microsoft YaHei", Arial, sans-serif;
}

.app-container {
  height: 100vh;
  width: 100%;
  background-color: #f5f7fa;
}

.my-page-header{
  padding: 20px;
}
</style>
