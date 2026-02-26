<template>
  <router-link :to="`/student/experiment-detail/${experiment.id}`" class="g-exp-link">
    <div class="g-exp-card">
      <div class="g-exp-head">
        <h3 class="g-exp-name">{{ experiment.name }}</h3>
        <span class="g-chip" :class="'c-' + experiment.status">{{ statusText }}</span>
      </div>
      <div class="g-exp-deadline">
        <el-icon><Clock /></el-icon>
        <span>截止: {{ experiment.deadline }}</span>
      </div>
      <div v-if="experiment.status === 'completed'" class="g-exp-extra">
        <span v-if="experiment.score">得分: <b>{{ experiment.score }}</b></span>
        <span v-if="experiment.plagiarismRate != null">查重率: {{ experiment.plagiarismRate }}%</span>
        <span v-if="experiment.submitTime">{{ experiment.submitTime }}</span>
      </div>
      <div class="g-exp-action">
        <span class="g-action-link">{{ actionText }} →</span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
import { computed } from 'vue'
import { Clock } from '@element-plus/icons-vue'

const props = defineProps({ experiment: { type: Object, required: true } })

const statusText = computed(() => ({ completed: '已完成', in_progress: '进行中', not_started: '未开始' }[props.experiment.status] || '未知'))
const actionText = computed(() => ({ completed: '查看结果', in_progress: '继续实验', not_started: '开始实验' }[props.experiment.status] || '查看详情'))
</script>

<style scoped>
.g-exp-link { text-decoration: none; color: inherit; display: block; margin-bottom: 16px; }
.g-exp-card {
  background: #fff; border-radius: 16px; padding: 20px;
  border: 1px solid #dadce0; transition: box-shadow 0.2s, transform 0.2s;
}
.g-exp-card:hover { box-shadow: 0 1px 3px rgba(60,64,67,0.15), 0 4px 8px rgba(60,64,67,0.08); transform: translateY(-2px); }
.g-exp-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; gap: 8px; }
.g-exp-name { font-size: 15px; font-weight: 500; color: #202124; margin: 0; line-height: 1.4; }
.g-chip { display: inline-block; font-size: 11px; padding: 2px 10px; border-radius: 100px; font-weight: 500; white-space: nowrap; }
.c-completed { background: #e6f4ea; color: #1e8e3e; }
.c-in_progress { background: #fef7e0; color: #e37400; }
.c-not_started { background: #f1f3f4; color: #5f6368; }
.g-exp-deadline { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #5f6368; margin-bottom: 8px; }
.g-exp-extra { display: flex; flex-wrap: wrap; gap: 12px; font-size: 12px; color: #5f6368; margin-bottom: 8px; }
.g-exp-extra b { color: #1a73e8; }
.g-exp-action { text-align: right; margin-top: 8px; }
.g-action-link { font-size: 13px; color: #1a73e8; font-weight: 500; }
</style>
