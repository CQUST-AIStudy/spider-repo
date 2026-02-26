<template>
  <div class="experiment-tab-content">
    <el-row :gutter="20">
      <el-col :span="24" v-if="experiments.length === 0">
        <el-empty description="暂无实验数据" />
      </el-col>
      
      <el-col :span="8" v-for="experiment in experiments" :key="experiment.id">
        <experiment-card :experiment="experiment" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import ExperimentCard from '../../../components/ExperimentCard.vue'

defineProps({
  experiments: {
    type: Array,
    required: true
  }
})
</script>

<style scoped>
.experiment-tab-content {
  min-height: 200px;
}
</style> 