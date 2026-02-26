<template>
  <div class="g-page">
    <page-header title="实验列表" description="管理和查看您创建的所有实验">
      <button class="g-primary-btn" @click="createExperiment">创建实验</button>
    </page-header>

    <div class="g-card">
      <el-table :data="experiments" style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="实验名称" min-width="180" />
        <el-table-column prop="deadline" label="截止日期" width="120" />
        <el-table-column prop="submissionCount" label="提交数" width="90" />
        <el-table-column prop="averageScore" label="平均分" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <span class="g-chip" :class="'c-' + scope.row.status">{{ getStatusText(scope.row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <a class="g-link" @click="viewDetail(scope.row.id)">查看详情</a>
            <a class="g-link" style="margin-left:12px" @click="viewSubmissions(scope.row.id)">学生提交</a>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../../api'
import PageHeader from '../../components/PageHeader.vue'

const router = useRouter()
const experiments = ref([])

const loadExperiments = async () => {
  try {
    const response = await api.getTeacherExperimentList()
    if (response?.data && Array.isArray(response.data)) experiments.value = response.data
    else if (Array.isArray(response)) experiments.value = response
    else experiments.value = response?.data?.data || []
  } catch (e) { console.error('加载实验列表失败:', e); experiments.value = [] }
}

const getStatusText = s => ({ active: '进行中', draft: '草稿', expired: '已截止' }[s] || '未知')
const createExperiment = () => router.push('/teacher/experiment-create')
const viewDetail = id => router.push(`/teacher/experiment-detail/${id}`)
const viewSubmissions = id => router.push(`/teacher/submissions/${id}`)

onMounted(loadExperiments)
</script>

<style scoped>
.g-page { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
.g-card {
  background: #fff; border-radius: 16px; padding: 20px;
  border: 1px solid #dadce0;
}
.g-card :deep(.el-table) { --el-table-border-color: #f1f3f4; --el-table-header-bg-color: #f8f9fa; }
.g-card :deep(.el-table th) { font-weight: 500; color: #5f6368; font-size: 12px; }
.g-card :deep(.el-table td) { font-size: 13px; color: #202124; }

.g-chip {
  display: inline-block; font-size: 11px; padding: 2px 10px; border-radius: 100px; font-weight: 500;
}
.c-active { background: #e6f4ea; color: #1e8e3e; }
.c-draft { background: #f1f3f4; color: #5f6368; }
.c-expired { background: #fce8e6; color: #d93025; }

.g-link { font-size: 13px; color: #1a73e8; cursor: pointer; font-weight: 500; }
.g-link:hover { text-decoration: underline; }

.g-primary-btn {
  background: #1a73e8; color: #fff; border: none; border-radius: 100px;
  padding: 9px 24px; font-size: 14px; font-weight: 500; cursor: pointer;
  transition: background 0.2s;
}
.g-primary-btn:hover { background: #1765cc; }
</style>
