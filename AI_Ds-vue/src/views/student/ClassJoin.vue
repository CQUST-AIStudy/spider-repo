<template>
  <div class="class-join-page">
    <page-header
      title="教学班"
      description="加入教学班后，AI 学习助手会自动解锁该班级可访问的课程知识库和 RAG 问答空间。"
    />

    <div class="class-join-grid">
      <el-card class="join-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>加入新班级</span>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="班级号">
            <el-input v-model="joinForm.classCode" placeholder="例如：CS2025-01" />
          </el-form-item>
          <el-form-item label="加入密码">
            <el-input
              v-model="joinForm.password"
              type="password"
              show-password
              placeholder="输入教师提供的加入密码"
            />
          </el-form-item>
        </el-form>

        <div class="join-actions">
          <el-button type="primary" :loading="joining" :disabled="!canSubmit" @click="submitJoin">
            加入班级
          </el-button>
          <el-button @click="goAssistant">前往 AI 助手</el-button>
        </div>

        <div class="join-tip">
          加入成功后，AI 助手会自动展示你当前教学班有权限访问的课程空间。
        </div>
      </el-card>

      <el-card class="joined-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>已加入班级</span>
            <el-button link :loading="loading" @click="loadJoinedClasses">刷新</el-button>
          </div>
        </template>

        <el-empty v-if="!loading && joinedClasses.length === 0" description="你还没有加入任何教学班" />

        <div v-else v-loading="loading" class="joined-list">
          <div v-for="item in joinedClasses" :key="item.id" class="joined-item">
            <div class="joined-main">
              <div class="joined-title">{{ item.name }}</div>
              <div class="joined-meta">
                <span v-if="item.courseName">{{ item.courseName }}</span>
                <span v-if="item.grade">{{ item.grade }}级</span>
                <span>{{ item.classCode }}</span>
              </div>
            </div>
            <div class="joined-side">
              <el-button size="small" @click="goAssistant">去问 AI</el-button>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import { buildApiUrl } from '../../config/runtime'

const router = useRouter()
const loading = ref(false)
const joining = ref(false)
const joinedClasses = ref([])
const joinForm = reactive({
  classCode: '',
  password: ''
})

const canSubmit = computed(() => joinForm.classCode.trim() && joinForm.password.trim())

async function loadJoinedClasses() {
  loading.value = true
  try {
    const response = await fetch(buildApiUrl('/api/student-classes'), {
      credentials: 'include'
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const data = await response.json()
    joinedClasses.value = Array.isArray(data) ? data : data?.data || []
  } catch (error) {
    ElMessage.error('加载已加入班级失败')
  } finally {
    loading.value = false
  }
}

async function submitJoin() {
  if (!canSubmit.value || joining.value) return
  joining.value = true
  try {
    const response = await fetch(buildApiUrl('/api/student-classes/join'), {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        classCode: joinForm.classCode.trim(),
        password: joinForm.password.trim()
      })
    })
    const payload = await response.json().catch(() => null)
    if (!response.ok) {
      throw new Error(payload?.message || `HTTP ${response.status}`)
    }
    ElMessage.success('加入班级成功')
    joinForm.classCode = ''
    joinForm.password = ''
    await loadJoinedClasses()
  } catch (error) {
    ElMessage.error(error.message || '加入班级失败')
  } finally {
    joining.value = false
  }
}

function goAssistant() {
  router.push('/student/ai-assistant')
}

onMounted(() => {
  loadJoinedClasses()
})
</script>

<style scoped>
.class-join-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.class-join-grid {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 20px;
}

.join-card,
.joined-card {
  border-radius: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.join-actions {
  display: flex;
  gap: 12px;
}

.join-tip {
  margin-top: 12px;
  color: #909399;
  font-size: 13px;
  line-height: 1.6;
}

.joined-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.joined-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: #fcfcfd;
}

.joined-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.joined-title {
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.joined-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #909399;
  font-size: 13px;
}

@media (max-width: 960px) {
  .class-join-grid {
    grid-template-columns: 1fr;
  }

  .join-actions {
    flex-wrap: wrap;
  }

  .joined-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
